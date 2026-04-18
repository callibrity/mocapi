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
import tools.jackson.databind.node.StringNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RequestAndNotificationTypesSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Nested
  class Request_params {

    @Test
    void round_trip() throws Exception {
      var params = new RequestParams(new RequestMeta(StringNode.valueOf("tok")));
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"_meta\"").contains("\"progressToken\":\"tok\"");

      var deserialized = mapper.readValue(json, RequestParams.class);
      assertThat(deserialized.meta().progressToken().asString()).isEqualTo("tok");
    }

    @Test
    void omits_null_meta() throws Exception {
      var params = new RequestParams(null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{}");
    }
  }

  @Nested
  class Paginated_request_params {

    @Test
    void round_trip() throws Exception {
      var params =
          new PaginatedRequestParams("cursor-123", new RequestMeta(StringNode.valueOf("tok")));
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"cursor\":\"cursor-123\"").contains("\"_meta\"");

      var deserialized = mapper.readValue(json, PaginatedRequestParams.class);
      assertThat(deserialized.cursor()).isEqualTo("cursor-123");
      assertThat(deserialized.meta().progressToken().asString()).isEqualTo("tok");
    }

    @Test
    void omits_null_fields() throws Exception {
      var params = new PaginatedRequestParams(null, null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{}");
    }
  }

  @Nested
  class Misc_params {

    @Test
    void notification_params_round_trip() throws Exception {
      var params = new NotificationParams(null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{}");
    }

    @Test
    void task_metadata_round_trip() throws Exception {
      var task = new TaskMetadata("task-1");
      String json = mapper.writeValueAsString(task);
      assertThat(json).isEqualTo("{\"taskId\":\"task-1\"}");

      var deserialized = mapper.readValue(json, TaskMetadata.class);
      assertThat(deserialized.taskId()).isEqualTo("task-1");
    }
  }

  @Nested
  class Request_type_round_trips {

    @Test
    void initialize_request_params() throws Exception {
      var params =
          new InitializeRequestParams(
              "2025-11-25",
              new ClientCapabilities(null, null, null),
              new Implementation("test-client", null, "1.0"),
              null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"protocolVersion\":\"2025-11-25\"").contains("\"clientInfo\"");

      var deserialized = mapper.readValue(json, InitializeRequestParams.class);
      assertThat(deserialized.protocolVersion()).isEqualTo("2025-11-25");
      assertThat(deserialized.clientInfo().name()).isEqualTo("test-client");
    }

    @Test
    void call_tool_request_params() throws Exception {
      var argsNode = mapper.readTree("{\"key\":\"value\"}");
      var params = new CallToolRequestParams("myTool", argsNode, null, null);
      String json = mapper.writeValueAsString(params);
      assertThat(json)
          .contains("\"name\":\"myTool\"")
          .contains("\"arguments\":{\"key\":\"value\"}");

      var deserialized = mapper.readValue(json, CallToolRequestParams.class);
      assertThat(deserialized.name()).isEqualTo("myTool");
      assertThat(deserialized.arguments().get("key").asString()).isEqualTo("value");
    }

    @Test
    void call_tool_request_params_with_task() throws Exception {
      var params =
          new CallToolRequestParams(
              "tool",
              null,
              new TaskMetadata("t1"),
              new RequestMeta(StringNode.valueOf("progress-tok")));
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"task\":{\"taskId\":\"t1\"}").contains("\"_meta\"");

      var deserialized = mapper.readValue(json, CallToolRequestParams.class);
      assertThat(deserialized.task().taskId()).isEqualTo("t1");
    }

    @Test
    void get_prompt_request_params() throws Exception {
      var params = new GetPromptRequestParams("myPrompt", Map.of("arg1", "val1"), null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"name\":\"myPrompt\"").contains("\"arg1\":\"val1\"");

      var deserialized = mapper.readValue(json, GetPromptRequestParams.class);
      assertThat(deserialized.name()).isEqualTo("myPrompt");
      assertThat(deserialized.arguments()).containsEntry("arg1", "val1");
    }

    @Test
    void resource_request_params() throws Exception {
      var params = new ResourceRequestParams("file:///test.txt", null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{\"uri\":\"file:///test.txt\"}");

      var deserialized = mapper.readValue(json, ResourceRequestParams.class);
      assertThat(deserialized.uri()).isEqualTo("file:///test.txt");
    }

    @Test
    void set_level_request_params() throws Exception {
      var params = new SetLevelRequestParams(LoggingLevel.WARNING, null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"level\":\"warning\"");

      var deserialized = mapper.readValue(json, SetLevelRequestParams.class);
      assertThat(deserialized.level()).isEqualTo(LoggingLevel.WARNING);
    }

    @Test
    void model_preferences() throws Exception {
      var prefs = new ModelPreferences(List.of(new ModelHint("claude-3")), 0.5, 0.8, 0.9);
      String json = mapper.writeValueAsString(prefs);
      assertThat(json)
          .contains("\"hints\"")
          .contains("\"name\":\"claude-3\"")
          .contains("\"costPriority\":0.5");

      var deserialized = mapper.readValue(json, ModelPreferences.class);
      assertThat(deserialized.hints()).hasSize(1);
      assertThat(deserialized.hints().getFirst().name()).isEqualTo("claude-3");
      assertThat(deserialized.costPriority()).isEqualTo(0.5);
    }

    @Test
    void create_message_request_params() throws Exception {
      var params =
          new CreateMessageRequestParams(
              List.of(new SamplingMessage(Role.USER, null)),
              null,
              "You are helpful",
              "thisServer",
              0.7,
              1024,
              null,
              null,
              null,
              null,
              null,
              null);
      String json = mapper.writeValueAsString(params);
      assertThat(json)
          .contains("\"systemPrompt\":\"You are helpful\"")
          .contains("\"includeContext\":\"thisServer\"")
          .contains("\"maxTokens\":1024");

      var deserialized = mapper.readValue(json, CreateMessageRequestParams.class);
      assertThat(deserialized.messages()).hasSize(1);
      assertThat(deserialized.systemPrompt()).isEqualTo("You are helpful");
      assertThat(deserialized.maxTokens()).isEqualTo(1024);
    }
  }

  @Nested
  class Complete_request_params {

    @Test
    void with_prompt_ref() throws Exception {
      var params =
          new CompleteRequestParams(
              new PromptReference("ref/prompt", "myPrompt"),
              new CompletionArgument("arg", "val"),
              null,
              null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"type\":\"ref/prompt\"").contains("\"name\":\"myPrompt\"");

      var deserialized = mapper.readValue(json, CompleteRequestParams.class);
      assertThat(deserialized.ref()).isInstanceOf(PromptReference.class);
      var ref = (PromptReference) deserialized.ref();
      assertThat(ref.name()).isEqualTo("myPrompt");
    }

    @Test
    void with_resource_template_ref() throws Exception {
      var params =
          new CompleteRequestParams(
              new ResourceTemplateReference("ref/resource", "file:///{path}"),
              new CompletionArgument("path", "/home"),
              new CompletionContext(Map.of("key", "value")),
              null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"type\":\"ref/resource\"").contains("\"uri\":\"file:///{path}\"");

      var deserialized = mapper.readValue(json, CompleteRequestParams.class);
      assertThat(deserialized.ref()).isInstanceOf(ResourceTemplateReference.class);
      assertThat(deserialized.context().arguments()).containsEntry("key", "value");
    }

    @Test
    void completion_argument_round_trip() throws Exception {
      var arg = new CompletionArgument("name", "value");
      String json = mapper.writeValueAsString(arg);
      assertThat(json).isEqualTo("{\"name\":\"name\",\"value\":\"value\"}");

      var deserialized = mapper.readValue(json, CompletionArgument.class);
      assertThat(deserialized.name()).isEqualTo("name");
      assertThat(deserialized.value()).isEqualTo("value");
    }
  }

  @Nested
  class Elicit_request_params {

    @Test
    void form_params_round_trip() throws Exception {
      var params = new ElicitRequestFormParams("form", "Please fill", null, null, null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"mode\":\"form\"").contains("\"message\":\"Please fill\"");

      var deserialized = mapper.readValue(json, ElicitRequestFormParams.class);
      assertThat(deserialized.mode()).isEqualTo("form");
      assertThat(deserialized.message()).isEqualTo("Please fill");
    }

    @Test
    void url_params_round_trip() throws Exception {
      var params =
          new ElicitRequestURLParams(
              "url", "Click link", "elicit-1", "https://example.com", null, null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"mode\":\"url\"").contains("\"elicitationId\":\"elicit-1\"");

      var deserialized = mapper.readValue(json, ElicitRequestURLParams.class);
      assertThat(deserialized.mode()).isEqualTo("url");
      assertThat(deserialized.url()).isEqualTo("https://example.com");
    }
  }

  @Nested
  class Notification_params {

    @Test
    void initialized_notification_omits_null_meta() throws Exception {
      var params = new InitializedNotificationParams(null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{}");
    }

    @Test
    void cancelled_notification_round_trip() throws Exception {
      var params = new CancelledNotificationParams("req-1", "timeout", null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).contains("\"requestId\":\"req-1\"").contains("\"reason\":\"timeout\"");

      var deserialized = mapper.readValue(json, CancelledNotificationParams.class);
      assertThat(deserialized.requestId()).isEqualTo("req-1");
      assertThat(deserialized.reason()).isEqualTo("timeout");
    }

    @Test
    void cancelled_notification_with_numeric_request_id() throws Exception {
      String json = "{\"requestId\":42,\"reason\":\"cancelled\"}";
      var deserialized = mapper.readValue(json, CancelledNotificationParams.class);
      assertThat(deserialized.requestId()).isEqualTo(42);
    }

    @Test
    void logging_message_notification_round_trip() throws Exception {
      var params =
          new LoggingMessageNotificationParams(LoggingLevel.ERROR, "myLogger", "error msg", null);
      String json = mapper.writeValueAsString(params);
      assertThat(json)
          .contains("\"level\":\"error\"")
          .contains("\"logger\":\"myLogger\"")
          .contains("\"data\":\"error msg\"");

      var deserialized = mapper.readValue(json, LoggingMessageNotificationParams.class);
      assertThat(deserialized.level()).isEqualTo(LoggingLevel.ERROR);
      assertThat(deserialized.logger()).isEqualTo("myLogger");
    }

    @Test
    void resource_updated_notification_round_trip() throws Exception {
      var params = new ResourceUpdatedNotificationParams("file:///updated.txt", null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{\"uri\":\"file:///updated.txt\"}");

      var deserialized = mapper.readValue(json, ResourceUpdatedNotificationParams.class);
      assertThat(deserialized.uri()).isEqualTo("file:///updated.txt");
    }

    @Test
    void elicitation_complete_notification_round_trip() throws Exception {
      var params = new ElicitationCompleteNotificationParams("elicit-42", null);
      String json = mapper.writeValueAsString(params);
      assertThat(json).isEqualTo("{\"elicitationId\":\"elicit-42\"}");

      var deserialized = mapper.readValue(json, ElicitationCompleteNotificationParams.class);
      assertThat(deserialized.elicitationId()).isEqualTo("elicit-42");
    }
  }

  @Test
  void mcp_methods_constants() {
    assertThat(McpMethods.INITIALIZE).isEqualTo("initialize");
    assertThat(McpMethods.TOOLS_CALL).isEqualTo("tools/call");
    assertThat(McpMethods.PROMPTS_GET).isEqualTo("prompts/get");
    assertThat(McpMethods.RESOURCES_READ).isEqualTo("resources/read");
    assertThat(McpMethods.SAMPLING_CREATE_MESSAGE).isEqualTo("sampling/createMessage");
    assertThat(McpMethods.COMPLETION_COMPLETE).isEqualTo("completion/complete");
    assertThat(McpMethods.ELICITATION_CREATE).isEqualTo("elicitation/create");
    assertThat(McpMethods.NOTIFICATIONS_INITIALIZED).isEqualTo("notifications/initialized");
    assertThat(McpMethods.NOTIFICATIONS_CANCELLED).isEqualTo("notifications/cancelled");
    assertThat(McpMethods.NOTIFICATIONS_PROGRESS).isEqualTo("notifications/progress");
    assertThat(McpMethods.NOTIFICATIONS_ELICITATION_COMPLETE)
        .isEqualTo("notifications/elicitation/complete");
  }
}
