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

import com.callibrity.mocapi.model.CreateMessageRequestParams;
import com.callibrity.mocapi.model.CreateMessageResult;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.LoggingLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class McpToolContextDefaultMethodsTest {

  record LogEntry(LoggingLevel level, String logger, String message) {}

  static class CapturingContext implements McpToolContext {
    final List<LogEntry> entries = new ArrayList<>();
    ElicitRequestFormParams lastElicitParams;

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
      throw new UnsupportedOperationException("Not under test");
    }
  }

  @ParameterizedTest
  @MethodSource("loggingMethods")
  void convenienceMethodDelegatesToLogWithCorrectLevel(
      LoggingLevel expectedLevel, LogInvoker invoker) {
    var ctx = new CapturingContext();

    invoker.invoke(ctx, "test-logger", "test message");

    assertThat(ctx.entries).hasSize(1);
    assertThat(ctx.entries.getFirst().level()).isEqualTo(expectedLevel);
    assertThat(ctx.entries.getFirst().logger()).isEqualTo("test-logger");
    assertThat(ctx.entries.getFirst().message()).isEqualTo("test message");
  }

  static Stream<Arguments> loggingMethods() {
    return Stream.of(
        Arguments.of(LoggingLevel.DEBUG, (LogInvoker) McpToolContext::debug),
        Arguments.of(LoggingLevel.INFO, (LogInvoker) McpToolContext::info),
        Arguments.of(LoggingLevel.NOTICE, (LogInvoker) McpToolContext::notice),
        Arguments.of(LoggingLevel.WARNING, (LogInvoker) McpToolContext::warning),
        Arguments.of(LoggingLevel.ERROR, (LogInvoker) McpToolContext::error),
        Arguments.of(LoggingLevel.CRITICAL, (LogInvoker) McpToolContext::critical),
        Arguments.of(LoggingLevel.ALERT, (LogInvoker) McpToolContext::alert),
        Arguments.of(LoggingLevel.EMERGENCY, (LogInvoker) McpToolContext::emergency));
  }

  @Test
  void fluentElicitBuildsSchemaAndDelegatesToElicit() {
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
  void fluentElicitWithOptionalFieldExcludesFromRequired() {
    var ctx = new CapturingContext();

    ctx.elicit(
        "Optional test",
        schema ->
            schema
                .string("required", "Required")
                .string("optional", "Optional", s -> s.optional()));

    assertThat(ctx.lastElicitParams.requestedSchema().required()).containsExactly("required");
  }

  @FunctionalInterface
  interface LogInvoker {
    void invoke(McpToolContext ctx, String logger, String message);
  }
}
