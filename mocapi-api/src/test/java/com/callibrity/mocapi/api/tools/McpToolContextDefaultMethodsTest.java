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
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolContextDefaultMethodsTest {

  record LogEntry(LoggingLevel level, String logger, String message) {}

  static class CapturingContext implements McpToolContext {
    final List<LogEntry> entries = new ArrayList<>();
    ElicitRequestFormParams lastElicitParams;
    CreateMessageRequestParams lastSampleParams;

    @Override
    public void sendProgress(long progress, long total) {
      // Not under test — only default methods are exercised here
    }

    @Override
    public void log(LoggingLevel level, String logger, String message) {
      entries.add(new LogEntry(level, logger, message));
    }

    @Override
    public ElicitResult elicit(ElicitRequestFormParams params) {
      lastElicitParams = params;
      return new ElicitResult(com.callibrity.mocapi.model.ElicitAction.ACCEPT, null);
    }

    @Override
    public CreateMessageResult sample(CreateMessageRequestParams params) {
      lastSampleParams = params;
      return new CreateMessageResult(
          com.callibrity.mocapi.model.Role.ASSISTANT,
          new com.callibrity.mocapi.model.TextContent("ok", null),
          null,
          null);
    }

    @Override
    public CreateMessageResult sample(
        java.util.function.Consumer<com.callibrity.mocapi.api.sampling.CreateMessageRequestConfig>
            customizer) {
      throw new UnsupportedOperationException(
          "Fluent sample(Consumer) is exercised in the server module where the concrete "
              + "CreateMessageRequestConfig implementation lives.");
    }
  }

  @Nested
  class Logger_factory {
    @Test
    void logger_routes_parameterized_message_to_log() {
      var ctx = new CapturingContext();

      ctx.logger("catalog").info("processed {} in {}ms", 42, 17);

      assertThat(ctx.entries).hasSize(1);
      assertThat(ctx.entries.getFirst().level()).isEqualTo(LoggingLevel.INFO);
      assertThat(ctx.entries.getFirst().logger()).isEqualTo("catalog");
      assertThat(ctx.entries.getFirst().message()).isEqualTo("processed 42 in 17ms");
    }

    @Test
    void default_handler_name_is_mcp() {
      var ctx = new CapturingContext();

      ctx.logger().warn("careful");

      assertThat(ctx.entries).hasSize(1);
      assertThat(ctx.entries.getFirst().logger()).isEqualTo("mcp");
    }
  }

  @Nested
  class Sample_shortcuts {

    static class FluentSampleContext implements McpToolContext {
      final CreateMessageResult nextResult;
      Consumer<CreateMessageRequestConfig> lastCustomizer;

      FluentSampleContext(CreateMessageResult nextResult) {
        this.nextResult = nextResult;
      }

      @Override
      public void sendProgress(long progress, long total) {
        // Not under test
      }

      @Override
      public void log(LoggingLevel level, String logger, String message) {
        // Not under test
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
      // Replay the customizer on a fresh mock to verify the user message was set.
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
    @Test
    void fluent_elicit_builds_schema_and_delegates_to_elicit() {
      var ctx = new CapturingContext();

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
      var ctx = new CapturingContext();

      ctx.elicit(
          "Optional test",
          schema ->
              schema
                  .string("required", "Required")
                  .string("optional", "Optional", s -> s.optional()));

      assertThat(ctx.lastElicitParams.requestedSchema().required()).containsExactly("required");
    }
  }
}
