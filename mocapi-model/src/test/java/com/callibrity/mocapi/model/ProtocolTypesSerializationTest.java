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
import tools.jackson.databind.node.ObjectNode;

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

  @Test
  void completionsCapabilityRoundTrip() throws Exception {
    var original = new CompletionsCapability();
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{}");

    var deserialized = mapper.readValue(json, CompletionsCapability.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void elicitationCapabilityRoundTrip() throws Exception {
    var original = new ElicitationCapability();
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{}");

    var deserialized = mapper.readValue(json, ElicitationCapability.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void samplingCapabilityRoundTrip() throws Exception {
    var original = new SamplingCapability();
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{}");

    var deserialized = mapper.readValue(json, SamplingCapability.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void promptsCapabilityRoundTrip() throws Exception {
    var original = new PromptsCapability(true);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"listChanged\":true");

    var deserialized = mapper.readValue(json, PromptsCapability.class);
    assertThat(deserialized.listChanged()).isTrue();
  }

  @Test
  void promptsCapabilityNullFieldOmitted() throws Exception {
    var original = new PromptsCapability(null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void resourcesCapabilityRoundTrip() throws Exception {
    var original = new ResourcesCapability(true, true);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"subscribe\":true").contains("\"listChanged\":true");

    var deserialized = mapper.readValue(json, ResourcesCapability.class);
    assertThat(deserialized)
        .satisfies(
            r -> {
              assertThat(r.subscribe()).isTrue();
              assertThat(r.listChanged()).isTrue();
            });
  }

  @Test
  void resourcesCapabilityNullFieldsOmitted() throws Exception {
    var original = new ResourcesCapability(null, null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void rootsCapabilityRoundTrip() throws Exception {
    var original = new RootsCapability(true);
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"listChanged\":true");

    var deserialized = mapper.readValue(json, RootsCapability.class);
    assertThat(deserialized.listChanged()).isTrue();
  }

  @Test
  void rootsCapabilityNullFieldOmitted() throws Exception {
    var original = new RootsCapability(null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void iconFullyPopulatedRoundTrip() throws Exception {
    var original = new Icon("https://example.com/icon.png", "image/png", List.of("32x32"), "light");
    String json = mapper.writeValueAsString(original);
    assertThat(json)
        .contains("\"src\":\"https://example.com/icon.png\"")
        .contains("\"mimeType\":\"image/png\"")
        .contains("\"sizes\":[\"32x32\"]")
        .contains("\"theme\":\"light\"");

    var deserialized = mapper.readValue(json, Icon.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void iconNullFieldsOmitted() throws Exception {
    var original = new Icon("https://example.com/icon.png", null, null, null);
    String json = mapper.writeValueAsString(original);
    assertThat(json)
        .contains("\"src\":")
        .doesNotContain("mimeType")
        .doesNotContain("sizes")
        .doesNotContain("theme");
  }

  @Test
  void promptArgumentFullyPopulatedRoundTrip() throws Exception {
    var original = new PromptArgument("name", "The user's name", true);
    String json = mapper.writeValueAsString(original);
    assertThat(json)
        .contains("\"name\":\"name\"")
        .contains("\"description\":\"The user's name\"")
        .contains("\"required\":true");

    var deserialized = mapper.readValue(json, PromptArgument.class);
    assertThat(deserialized).isEqualTo(original);
  }

  @Test
  void promptArgumentMinimalRoundTrip() throws Exception {
    var original = new PromptArgument("name", null, null);
    String json = mapper.writeValueAsString(original);
    assertThat(json)
        .contains("\"name\":\"name\"")
        .doesNotContain("description")
        .doesNotContain("required");
  }

  @Test
  void listPromptsResultRoundTrip() throws Exception {
    var prompt = new Prompt("greeting", "Greeting", "A greeting prompt", null, null);
    var original = new ListPromptsResult(List.of(prompt), "cursor123");
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"prompts\":[").contains("\"nextCursor\":\"cursor123\"");

    var deserialized = mapper.readValue(json, ListPromptsResult.class);
    assertThat(deserialized)
        .satisfies(
            r -> {
              assertThat(r.prompts()).hasSize(1);
              assertThat(r.nextCursor()).isEqualTo("cursor123");
            });
  }

  @Test
  void listPromptsResultNullCursorOmitted() throws Exception {
    var original = new ListPromptsResult(List.of(), null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).doesNotContain("nextCursor");
  }

  @Test
  void listResourcesResultRoundTrip() throws Exception {
    var resource = new Resource("file:///test.txt", "test", "A test resource", "text/plain");
    var original = new ListResourcesResult(List.of(resource), "cursor456");
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"resources\":[").contains("\"nextCursor\":\"cursor456\"");

    var deserialized = mapper.readValue(json, ListResourcesResult.class);
    assertThat(deserialized)
        .satisfies(
            r -> {
              assertThat(r.resources()).hasSize(1);
              assertThat(r.nextCursor()).isEqualTo("cursor456");
            });
  }

  @Test
  void listResourcesResultNullCursorOmitted() throws Exception {
    var original = new ListResourcesResult(List.of(), null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).doesNotContain("nextCursor");
  }

  @Test
  void listResourceTemplatesResultRoundTrip() throws Exception {
    var template = new ResourceTemplate("file:///{path}", "files", "File access", "text/plain");
    var original = new ListResourceTemplatesResult(List.of(template), "cursorABC");
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"resourceTemplates\":[").contains("\"nextCursor\":\"cursorABC\"");

    var deserialized = mapper.readValue(json, ListResourceTemplatesResult.class);
    assertThat(deserialized)
        .satisfies(
            r -> {
              assertThat(r.resourceTemplates()).hasSize(1);
              assertThat(r.nextCursor()).isEqualTo("cursorABC");
            });
  }

  @Test
  void listResourceTemplatesResultNullCursorOmitted() throws Exception {
    var original = new ListResourceTemplatesResult(List.of(), null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).doesNotContain("nextCursor");
  }

  @Test
  void listToolsResultRoundTrip() throws Exception {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    var tool = new Tool("echo", "Echo", "Echoes input", schema, null);
    var original = new ListToolsResult(List.of(tool), "cursorXYZ");
    String json = mapper.writeValueAsString(original);
    assertThat(json).contains("\"tools\":[").contains("\"nextCursor\":\"cursorXYZ\"");

    var deserialized = mapper.readValue(json, ListToolsResult.class);
    assertThat(deserialized)
        .satisfies(
            r -> {
              assertThat(r.tools()).hasSize(1);
              assertThat(r.nextCursor()).isEqualTo("cursorXYZ");
            });
  }

  @Test
  void listToolsResultNullCursorOmitted() throws Exception {
    var original = new ListToolsResult(List.of(), null);
    String json = mapper.writeValueAsString(original);
    assertThat(json).doesNotContain("nextCursor");
  }
}
