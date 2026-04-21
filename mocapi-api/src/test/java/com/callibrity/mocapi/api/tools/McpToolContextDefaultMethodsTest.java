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
package com.callibrity.mocapi.api.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.api.sampling.CreateMessageRequestConfig;
import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolContextDefaultMethodsTest {

  /** Base test double: no-op everywhere except what the individual test needs. */
  abstract static class StubContext implements McpToolContext {
    @Override
    public String handlerName() {
      return "mcp";
    }

    @Override
    public McpLogger logger(String name) {
      throw new UnsupportedOperationException("logger not exercised");
    }

    @Override
    public void sendProgress(long progress, long total) {
      // not under test
    }
  }

  @Nested
  class Sample_shortcuts {

    static class FluentSampleContext extends StubContext {
      final CreateMessageResult nextResult;
      Consumer<CreateMessageRequestConfig> lastCustomizer;

      FluentSampleContext(CreateMessageResult nextResult) {
        this.nextResult = nextResult;
      }

      @Override
      public ElicitResult elicit(ElicitRequestFormParams params) {
        throw new UnsupportedOperationException();
      }

      @Override
      public CreateMessageResult sample(CreateMessageRequestParams params) {
        throw new UnsupportedOperationException(
            "sample(Consumer) is the entry point the default methods delegate to.");
      }

      @Override
      public CreateMessageResult sample(Consumer<CreateMessageRequestConfig> customizer) {
        this.lastCustomizer = customizer;
        CreateMessageRequestConfig config = mock(CreateMessageRequestConfig.class);
        when(config.userMessage(ArgumentMatchers.anyString())).thenReturn(config);
        customizer.accept(config);
        return nextResult;
      }
    }

    private static CreateMessageResult assistantText(String text) {
      return new CreateMessageResult(Role.ASSISTANT, new TextContent(text, null), null, null);
    }

    @Test
    void sample_string_delegates_to_sample_consumer_with_user_message() {
      CreateMessageResult result = assistantText("hello");
      var ctx = new FluentSampleContext(result);

      CreateMessageResult returned = ctx.sample("hello there");

      assertThat(returned).isSameAs(result);
      assertThat(ctx.lastCustomizer).isNotNull();
      CreateMessageRequestConfig spy = mock(CreateMessageRequestConfig.class);
      when(spy.userMessage(ArgumentMatchers.anyString())).thenReturn(spy);
      ctx.lastCustomizer.accept(spy);
      verify(spy).userMessage("hello there");
    }

    @Test
    void sample_text_string_returns_assistant_text() {
      var ctx = new FluentSampleContext(assistantText("bonjour"));

      assertThat(ctx.sampleText("salut")).isEqualTo("bonjour");
    }

    @Test
    void sample_text_consumer_runs_customizer_and_returns_text() {
      var ctx = new FluentSampleContext(assistantText("ciao"));

      assertThat(ctx.sampleText(b -> b.userMessage("hola"))).isEqualTo("ciao");
    }
  }

  @Nested
  class Fluent_elicit {

    static class CapturingElicitContext extends StubContext {
      ElicitRequestFormParams lastElicitParams;

      @Override
      public ElicitResult elicit(ElicitRequestFormParams params) {
        lastElicitParams = params;
        return new ElicitResult(com.callibrity.mocapi.model.ElicitAction.ACCEPT, null);
      }

      @Override
      public CreateMessageResult sample(CreateMessageRequestParams params) {
        throw new UnsupportedOperationException();
      }

      @Override
      public CreateMessageResult sample(Consumer<CreateMessageRequestConfig> customizer) {
        throw new UnsupportedOperationException();
      }
    }

    @Test
    void fluent_elicit_builds_schema_and_delegates_to_elicit() {
      var ctx = new CapturingElicitContext();

      ctx.elicit(
          "Enter your info",
          schema -> schema.string("name", "Your name").string("email", "Email", s -> s.email()));

      assertThat(ctx.lastElicitParams).isNotNull();
      assertThat(ctx.lastElicitParams.mode()).isEqualTo("form");
      assertThat(ctx.lastElicitParams.message()).isEqualTo("Enter your info");
      assertThat(ctx.lastElicitParams.requestedSchema()).isNotNull();
      assertThat(ctx.lastElicitParams.requestedSchema().properties()).containsKeys("name", "email");
      assertThat(ctx.lastElicitParams.requestedSchema().required()).contains("name", "email");
    }

    @Test
    void fluent_elicit_with_optional_field_excludes_from_required() {
      var ctx = new CapturingElicitContext();

      ctx.elicit(
          "Optional test",
          schema ->
              schema
                  .string("required", "Required")
                  .string("optional", "Optional", s -> s.optional()));

      assertThat(ctx.lastElicitParams.requestedSchema().required()).containsExactly("required");
    }
  }

  @Nested
  class Logger_shortcut {
    @Test
    void no_arg_logger_uses_handler_name() {
      var captured = new java.util.concurrent.atomic.AtomicReference<String>();
      McpToolContext ctx =
          new StubContext() {
            @Override
            public String handlerName() {
              return "greet-tool";
            }

            @Override
            public McpLogger logger(String name) {
              captured.set(name);
              return mock(McpLogger.class);
            }

            @Override
            public ElicitResult elicit(ElicitRequestFormParams params) {
              throw new UnsupportedOperationException();
            }

            @Override
            public CreateMessageResult sample(CreateMessageRequestParams params) {
              throw new UnsupportedOperationException();
            }

            @Override
            public CreateMessageResult sample(Consumer<CreateMessageRequestConfig> customizer) {
              throw new UnsupportedOperationException();
            }
          };

      ctx.logger();

      assertThat(captured.get()).isEqualTo("greet-tool");
    }
  }
}
