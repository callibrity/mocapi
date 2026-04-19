/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MockMcpToolContextTest {

  @Nested
  class Progress_capture {
    @Test
    void captures_each_send_progress_call_in_order() {
      var ctx = new MockMcpToolContext();

      ctx.sendProgress(1, 10);
      ctx.sendProgress(5, 10);
      ctx.sendProgress(10, 10);

      assertThat(ctx.progressEvents())
          .containsExactly(
              new MockMcpToolContext.ProgressEvent(1, 10),
              new MockMcpToolContext.ProgressEvent(5, 10),
              new MockMcpToolContext.ProgressEvent(10, 10));
    }

    @Test
    void accessor_returns_independent_copy() {
      var ctx = new MockMcpToolContext();
      ctx.sendProgress(1, 2);

      var first = ctx.progressEvents();
      ctx.sendProgress(2, 2);

      assertThat(first).hasSize(1);
      assertThat(ctx.progressEvents()).hasSize(2);
    }
  }

  @Nested
  class Log_capture {
    @Test
    void captures_direct_log_call() {
      var ctx = new MockMcpToolContext();

      ctx.log(LoggingLevel.INFO, "logger", "hello");

      assertThat(ctx.logEntries())
          .containsExactly(new MockMcpToolContext.LogEntry(LoggingLevel.INFO, "logger", "hello"));
    }

    @Test
    void default_convenience_methods_all_route_through_log() {
      var ctx = new MockMcpToolContext();

      ctx.debug("l", "d");
      ctx.info("l", "i");
      ctx.notice("l", "n");
      ctx.warning("l", "w");
      ctx.error("l", "e");
      ctx.critical("l", "c");
      ctx.alert("l", "a");
      ctx.emergency("l", "em");

      assertThat(ctx.logEntries())
          .extracting(MockMcpToolContext.LogEntry::level)
          .containsExactly(
              LoggingLevel.DEBUG,
              LoggingLevel.INFO,
              LoggingLevel.NOTICE,
              LoggingLevel.WARNING,
              LoggingLevel.ERROR,
              LoggingLevel.CRITICAL,
              LoggingLevel.ALERT,
              LoggingLevel.EMERGENCY);
    }
  }

  @Nested
  class Elicit_behavior {
    @Test
    void captures_elicit_and_returns_default_accept_with_null_content() {
      var ctx = new MockMcpToolContext();
      var params = new ElicitRequestFormParams("form", "msg", null, null, null);

      var result = ctx.elicit(params);

      assertThat(ctx.elicitCalls()).containsExactly(new MockMcpToolContext.ElicitCall(params));
      assertThat(result.action()).isEqualTo(ElicitAction.ACCEPT);
      assertThat(result.content()).isNull();
    }

    @Test
    void fixed_scripted_response_is_returned_for_every_call() {
      var ctx = new MockMcpToolContext();
      var scripted = new ElicitResult(ElicitAction.DECLINE, null);
      ctx.elicitResponse(scripted);

      assertThat(ctx.elicit(new ElicitRequestFormParams("form", "one", null, null, null)))
          .isSameAs(scripted);
      assertThat(ctx.elicit(new ElicitRequestFormParams("form", "two", null, null, null)))
          .isSameAs(scripted);
    }

    @Test
    void function_scripted_response_receives_params() {
      var ctx = new MockMcpToolContext();
      ctx.elicitResponse(
          params ->
              new ElicitResult(
                  "accept-me".equals(params.message()) ? ElicitAction.ACCEPT : ElicitAction.CANCEL,
                  null));

      assertThat(
              ctx.elicit(new ElicitRequestFormParams("form", "accept-me", null, null, null))
                  .action())
          .isEqualTo(ElicitAction.ACCEPT);
      assertThat(ctx.elicit(new ElicitRequestFormParams("form", "nope", null, null, null)).action())
          .isEqualTo(ElicitAction.CANCEL);
    }

    @Test
    void fluent_elicit_default_method_routes_through_capture() {
      var ctx = new MockMcpToolContext();

      ctx.elicit("Enter name", schema -> schema.string("name", "Your name"));

      assertThat(ctx.elicitCalls())
          .singleElement()
          .satisfies(call -> assertThat(call.params().message()).isEqualTo("Enter name"));
    }
  }

  @Nested
  class Sample_behavior {
    @Test
    void captures_sample_and_returns_default_assistant_text() {
      var ctx = new MockMcpToolContext();

      var result = ctx.sample("hi");

      assertThat(ctx.sampleCalls()).hasSize(1);
      assertThat(result.role()).isEqualTo(Role.ASSISTANT);
      assertThat(result.text()).isEqualTo("mock-response");
    }

    @Test
    void fixed_scripted_sample_response() {
      var ctx = new MockMcpToolContext();
      var scripted =
          new CreateMessageResult(Role.ASSISTANT, new TextContent("fixed", null), "m", "stop");
      ctx.sampleResponse(scripted);

      assertThat(ctx.sample("x")).isSameAs(scripted);
      assertThat(ctx.sample("y")).isSameAs(scripted);
    }

    @Test
    void function_scripted_sample_response_receives_params() {
      var ctx = new MockMcpToolContext();
      ctx.sampleResponse(
          params ->
              new CreateMessageResult(
                  Role.ASSISTANT,
                  new TextContent("echo:" + params.messages().size(), null),
                  null,
                  null));

      var result = ctx.sample(b -> b.userMessage("a").userMessage("b"));

      assertThat(result.text()).isEqualTo("echo:2");
    }

    @Test
    void fluent_sample_builds_params_and_captures() {
      var ctx = new MockMcpToolContext();

      ctx.sample(b -> b.userMessage("hello").maxTokens(42).preferModel("gpt"));

      assertThat(ctx.sampleCalls()).hasSize(1);
      CreateMessageRequestParams params = ctx.sampleCalls().getFirst().params();
      assertThat(params.messages()).hasSize(1);
      assertThat(params.maxTokens()).isEqualTo(42);
      assertThat(params.modelPreferences()).isNotNull();
    }

    @Test
    void staging_a_tool_directly_works() {
      var ctx = new MockMcpToolContext();
      var tool = new Tool("weather", null, null, null, null);

      ctx.sample(b -> b.userMessage("x").tool(tool));

      assertThat(ctx.sampleCalls().getFirst().params().tools()).containsExactly(tool);
    }

    @Test
    void tool_string_lookup_is_unsupported_in_mock() {
      var ctx = new MockMcpToolContext();

      assertThatThrownBy(() -> ctx.sample(b -> b.userMessage("x").tool("named")))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("MockMcpToolContext");
    }

    @Test
    void tools_varargs_lookup_is_unsupported_in_mock() {
      var ctx = new MockMcpToolContext();

      assertThatThrownBy(() -> ctx.sample(b -> b.userMessage("x").tools("a", "b")))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("MockMcpToolContext");
    }

    @Test
    void all_server_tools_is_unsupported_in_mock() {
      var ctx = new MockMcpToolContext();

      assertThatThrownBy(() -> ctx.sample(b -> b.userMessage("x").allServerTools()))
          .isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("MockMcpToolContext");
    }
  }

  @Nested
  class Reset {
    @Test
    void reset_clears_all_captures_and_responders() {
      var ctx = new MockMcpToolContext();
      ctx.sendProgress(1, 1);
      ctx.info("l", "m");
      ctx.elicit(new ElicitRequestFormParams("form", "q", null, null, null));
      ctx.sample("s");
      ctx.elicitResponse(new ElicitResult(ElicitAction.DECLINE, null));
      ctx.sampleResponse(
          new CreateMessageResult(Role.ASSISTANT, new TextContent("custom", null), null, null));

      ctx.reset();

      assertThat(ctx.progressEvents()).isEmpty();
      assertThat(ctx.logEntries()).isEmpty();
      assertThat(ctx.elicitCalls()).isEmpty();
      assertThat(ctx.sampleCalls()).isEmpty();

      var elicit = ctx.elicit(new ElicitRequestFormParams("form", "after", null, null, null));
      assertThat(elicit.action()).isEqualTo(ElicitAction.ACCEPT);
      assertThat(ctx.sample("after").text()).isEqualTo("mock-response");
    }
  }
}
