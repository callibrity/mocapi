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
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class ProtocolTypesSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void promptMessageRoundTrip() throws Exception {
    var msg = new PromptMessage(Role.USER, new TextContent("What is the weather?", null));
    String json = mapper.writeValueAsString(msg);
    assertThat(json).contains("\"role\":\"user\"").contains("\"content\":{\"type\":\"text\"");

    var deserialized = mapper.readValue(json, PromptMessage.class);
    assertThat(deserialized.role()).isEqualTo(Role.USER);
    assertThat(deserialized.content()).isInstanceOf(TextContent.class);
  }

  @Test
  void getPromptResultRoundTrip() throws Exception {
    var msg = new PromptMessage(Role.ASSISTANT, new TextContent("Hello!", null));
    var result = new GetPromptResult("A greeting prompt", List.of(msg));
    String json = mapper.writeValueAsString(result);

    var deserialized = mapper.readValue(json, GetPromptResult.class);
    assertThat(deserialized.description()).isEqualTo("A greeting prompt");
    assertThat(deserialized.messages()).hasSize(1);
  }

  @Test
  void callToolResultRoundTrip() throws Exception {
    var result = new CallToolResult(List.of(new TextContent("result", null)), false, null);
    String json = mapper.writeValueAsString(result);
    assertThat(json).doesNotContain("structuredContent");

    var deserialized = mapper.readValue(json, CallToolResult.class);
    assertThat(deserialized.content()).hasSize(1);
    assertThat(deserialized.isError()).isFalse();
  }

  @Test
  void initializeResultRoundTrip() throws Exception {
    var capabilities =
        new ServerCapabilities(
            new ToolsCapability(true), new LoggingCapability(), null, null, null);
    var serverInfo = new Implementation("test-server", "Test Server", "1.0.0");
    var result = new InitializeResult("2025-11-25", capabilities, serverInfo, "Welcome");
    String json = mapper.writeValueAsString(result);

    var deserialized = mapper.readValue(json, InitializeResult.class);
    assertThat(deserialized.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(deserialized.capabilities().tools().listChanged()).isTrue();
    assertThat(deserialized.serverInfo().name()).isEqualTo("test-server");
  }

  @Test
  void completionRoundTrip() throws Exception {
    var completion = new Completion(List.of("val1", "val2"), 10, true);
    var result = new CompleteResult(completion);
    String json = mapper.writeValueAsString(result);

    var deserialized = mapper.readValue(json, CompleteResult.class);
    assertThat(deserialized.completion().values()).containsExactly("val1", "val2");
    assertThat(deserialized.completion().total()).isEqualTo(10);
    assertThat(deserialized.completion().hasMore()).isTrue();
  }

  @Test
  void nullFieldsOmitted() throws Exception {
    var result = new InitializeResult("2025-11-25", null, null, null);
    String json = mapper.writeValueAsString(result);
    assertThat(json)
        .doesNotContain("capabilities")
        .doesNotContain("serverInfo")
        .doesNotContain("instructions");
  }

  @Test
  void loggingLevelValues() {
    assertThat(LoggingLevel.values())
        .extracting(Enum::name)
        .containsExactly(
            "DEBUG", "INFO", "NOTICE", "WARNING", "ERROR", "CRITICAL", "ALERT", "EMERGENCY");
  }

  @Test
  void jsonRpcErrorRoundTrip() throws Exception {
    var error = new JsonRpcError(-32600, "Invalid Request", null);
    String json = mapper.writeValueAsString(error);
    assertThat(json).doesNotContain("\"data\"");

    var deserialized = mapper.readValue(json, JsonRpcError.class);
    assertThat(deserialized.code()).isEqualTo(-32600);
    assertThat(deserialized.message()).isEqualTo("Invalid Request");
  }
}
