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
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProtocolTypesSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Nested
  class Messages_and_results {

    @Test
    void prompt_message_round_trip() throws Exception {
      var msg = new PromptMessage(Role.USER, new TextContent("What is the weather?", null));
      String json = mapper.writeValueAsString(msg);
      assertThat(json).contains("\"role\":\"user\"").contains("\"content\":{\"type\":\"text\"");

      var deserialized = mapper.readValue(json, PromptMessage.class);
      assertThat(deserialized.role()).isEqualTo(Role.USER);
      assertThat(deserialized.content()).isInstanceOf(TextContent.class);
    }

    @Test
    void get_prompt_result_round_trip() throws Exception {
      var msg = new PromptMessage(Role.ASSISTANT, new TextContent("Hello!", null));
      var result = new GetPromptResult("A greeting prompt", List.of(msg), ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(result);
      assertThat(json).contains("\"resultType\":\"complete\"");

      var deserialized = mapper.readValue(json, GetPromptResult.class);
      assertThat(deserialized.description()).isEqualTo("A greeting prompt");
      assertThat(deserialized.messages()).hasSize(1);
      assertThat(deserialized.resultType()).isEqualTo(ResultTypes.COMPLETE);
    }

    @Test
    void call_tool_result_round_trip() throws Exception {
      var result =
          new CallToolResult(
              List.of(new TextContent("result", null)), false, null, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(result);
      assertThat(json).doesNotContain("structuredContent").contains("\"resultType\":\"complete\"");

      var deserialized = mapper.readValue(json, CallToolResult.class);
      assertThat(deserialized.content()).hasSize(1);
      assertThat(deserialized.isError()).isFalse();
    }

    @Test
    void call_tool_result_structured_content_accepts_any_json_value() throws Exception {
      var result = new CallToolResult(null, null, mapper.readTree("[1,2,3]"), ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(result);
      assertThat(json).contains("\"structuredContent\":[1,2,3]");

      var deserialized = mapper.readValue(json, CallToolResult.class);
      assertThat(deserialized.structuredContent().isArray()).isTrue();
    }

    @Test
    void completion_round_trip() throws Exception {
      var completion = new Completion(List.of("val1", "val2"), 10, true);
      var result = new CompleteResult(completion, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(result);
      assertThat(json).contains("\"resultType\":\"complete\"");

      var deserialized = mapper.readValue(json, CompleteResult.class);
      assertThat(deserialized.completion().values()).containsExactly("val1", "val2");
      assertThat(deserialized.completion().total()).isEqualTo(10);
      assertThat(deserialized.completion().hasMore()).isTrue();
    }

    @Test
    void logging_level_values() {
      assertThat(LoggingLevel.values())
          .extracting(Enum::name)
          .containsExactly(
              "DEBUG", "INFO", "NOTICE", "WARNING", "ERROR", "CRITICAL", "ALERT", "EMERGENCY");
    }

    @Test
    void json_rpc_error_round_trip() throws Exception {
      var error = new JsonRpcError(-32600, "Invalid Request", null);
      String json = mapper.writeValueAsString(error);
      assertThat(json).doesNotContain("\"data\"");

      var deserialized = mapper.readValue(json, JsonRpcError.class);
      assertThat(deserialized.code()).isEqualTo(-32600);
      assertThat(deserialized.message()).isEqualTo("Invalid Request");
    }
  }

  @Nested
  class Capabilities {

    @Test
    void completions_capability_round_trip() throws Exception {
      var original = new CompletionsCapability();
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{}");

      var deserialized = mapper.readValue(json, CompletionsCapability.class);
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void elicitation_capability_round_trip() throws Exception {
      var original = new ElicitationCapability(null, null);
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{}");

      var deserialized = mapper.readValue(json, ElicitationCapability.class);
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void elicitation_capability_with_form_sub_object_round_trip() throws Exception {
      var original = new ElicitationCapability(mapper.createObjectNode(), null);
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{\"form\":{}}");

      var deserialized = mapper.readValue(json, ElicitationCapability.class);
      assertThat(deserialized.form()).isNotNull();
      assertThat(deserialized.url()).isNull();
    }

    @Test
    // SEP-2577 spec contract: the sampling capability is deprecated but remains in the
    // specification for the deprecation window; this round-trip exercises the retained 1:1 model.
    @SuppressWarnings("deprecation")
    void sampling_capability_round_trip() throws Exception {
      var original = new SamplingCapability();
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{}");

      var deserialized = mapper.readValue(json, SamplingCapability.class);
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void prompts_capability_round_trip() throws Exception {
      var original = new PromptsCapability(true);
      String json = mapper.writeValueAsString(original);
      assertThat(json).contains("\"listChanged\":true");

      var deserialized = mapper.readValue(json, PromptsCapability.class);
      assertThat(deserialized.listChanged()).isTrue();
    }

    @Test
    void prompts_capability_null_field_omitted() throws Exception {
      var original = new PromptsCapability(null);
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{}");
    }

    @Test
    void resources_capability_round_trip() throws Exception {
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
    void resources_capability_null_fields_omitted() throws Exception {
      var original = new ResourcesCapability(null, null);
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{}");
    }

    @Test
    // SEP-2577 spec contract: the roots capability is deprecated but remains in the specification
    // for the deprecation window; this round-trip exercises the retained 1:1 model.
    @SuppressWarnings("deprecation")
    void roots_capability_is_an_empty_marker_object() throws Exception {
      var original = new RootsCapability();
      String json = mapper.writeValueAsString(original);
      assertThat(json).isEqualTo("{}");

      var deserialized = mapper.readValue(json, RootsCapability.class);
      assertThat(deserialized).isEqualTo(original);
    }

    @Test
    void client_capabilities_extensions_round_trip() throws Exception {
      var ext = mapper.createObjectNode();
      ext.put("enabled", true);
      var original =
          new ClientCapabilities(
              null,
              null,
              null,
              new ElicitationCapability(null, null),
              Map.of("com.example/x", ext));
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"elicitation\":{}")
          .contains("\"extensions\":{\"com.example/x\":{\"enabled\":true}}")
          .doesNotContain("roots")
          .doesNotContain("sampling");

      var deserialized = mapper.readValue(json, ClientCapabilities.class);
      assertThat(deserialized.extensions()).containsKey("com.example/x");
      assertThat(deserialized.elicitation()).isNotNull();
    }

    @Test
    void server_capabilities_extensions_round_trip() throws Exception {
      var original =
          new ServerCapabilities(
              null,
              new ToolsCapability(false),
              null,
              new CompletionsCapability(),
              new ResourcesCapability(false, false),
              new PromptsCapability(false),
              Map.of());
      String json = mapper.writeValueAsString(original);
      assertThat(json).contains("\"extensions\":{}").doesNotContain("logging");

      var deserialized = mapper.readValue(json, ServerCapabilities.class);
      assertThat(deserialized.tools().listChanged()).isFalse();
      assertThat(deserialized.extensions()).isEmpty();
    }
  }

  @Nested
  class Descriptors {

    @Test
    void icon_fully_populated_round_trip() throws Exception {
      var original =
          new Icon("https://example.com/icon.png", "image/png", List.of("32x32"), "light");
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
    void icon_null_fields_omitted() throws Exception {
      var original = new Icon("https://example.com/icon.png", null, null, null);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"src\":")
          .doesNotContain("mimeType")
          .doesNotContain("sizes")
          .doesNotContain("theme");
    }

    @Test
    void prompt_argument_fully_populated_round_trip() throws Exception {
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
    void prompt_argument_minimal_round_trip() throws Exception {
      var original = new PromptArgument("name", null, null);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"name\":\"name\"")
          .doesNotContain("description")
          .doesNotContain("required");
    }
  }

  @Nested
  class List_results {

    @Test
    void list_prompts_result_round_trip() throws Exception {
      var prompt = new Prompt("greeting", "Greeting", "A greeting prompt", null, null);
      var original =
          new ListPromptsResult(
              List.of(prompt), "cursor123", 5000L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"prompts\":[")
          .contains("\"nextCursor\":\"cursor123\"")
          .contains("\"ttlMs\":5000")
          .contains("\"cacheScope\":\"private\"")
          .contains("\"resultType\":\"complete\"");

      var deserialized = mapper.readValue(json, ListPromptsResult.class);
      assertThat(deserialized)
          .satisfies(
              r -> {
                assertThat(r.prompts()).hasSize(1);
                assertThat(r.nextCursor()).isEqualTo("cursor123");
                assertThat(r.ttlMs()).isEqualTo(5000L);
                assertThat(r.cacheScope()).isEqualTo(CacheScope.PRIVATE);
              });
    }

    @Test
    void list_prompts_result_null_cursor_omitted() throws Exception {
      var original =
          new ListPromptsResult(List.of(), null, 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json).doesNotContain("nextCursor");
    }

    @Test
    void list_resources_result_round_trip() throws Exception {
      var resource = new Resource("file:///test.txt", "test", "A test resource", "text/plain");
      var original =
          new ListResourcesResult(
              List.of(resource), "cursor456", 0L, CacheScope.PUBLIC, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"resources\":[")
          .contains("\"nextCursor\":\"cursor456\"")
          .contains("\"cacheScope\":\"public\"");

      var deserialized = mapper.readValue(json, ListResourcesResult.class);
      assertThat(deserialized)
          .satisfies(
              r -> {
                assertThat(r.resources()).hasSize(1);
                assertThat(r.nextCursor()).isEqualTo("cursor456");
                assertThat(r.cacheScope()).isEqualTo(CacheScope.PUBLIC);
              });
    }

    @Test
    void list_resources_result_null_cursor_omitted() throws Exception {
      var original =
          new ListResourcesResult(List.of(), null, 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json).doesNotContain("nextCursor");
    }

    @Test
    void list_resource_templates_result_round_trip() throws Exception {
      var template = new ResourceTemplate("file:///{path}", "files", "File access", "text/plain");
      var original =
          new ListResourceTemplatesResult(
              List.of(template), "cursorABC", 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
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
    void list_resource_templates_result_null_cursor_omitted() throws Exception {
      var original =
          new ListResourceTemplatesResult(
              List.of(), null, 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json).doesNotContain("nextCursor");
    }

    @Test
    void list_tools_result_round_trip() throws Exception {
      ObjectNode schema = mapper.createObjectNode();
      schema.put("type", "object");
      var tool = new Tool("echo", "Echo", "Echoes input", schema, null);
      var original =
          new ListToolsResult(
              List.of(tool), "cursorXYZ", 60000L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json)
          .contains("\"tools\":[")
          .contains("\"nextCursor\":\"cursorXYZ\"")
          .contains("\"ttlMs\":60000");

      var deserialized = mapper.readValue(json, ListToolsResult.class);
      assertThat(deserialized)
          .satisfies(
              r -> {
                assertThat(r.tools()).hasSize(1);
                assertThat(r.nextCursor()).isEqualTo("cursorXYZ");
                assertThat(r.ttlMs()).isEqualTo(60000L);
              });
    }

    @Test
    void list_tools_result_null_cursor_omitted() throws Exception {
      var original =
          new ListToolsResult(List.of(), null, 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(original);
      assertThat(json).doesNotContain("nextCursor");
    }
  }
}
