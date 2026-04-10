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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RequestAndNotificationTypesSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void requestParamsRoundTrip() throws Exception {
    var params = new RequestParams(new RequestMeta("tok"));
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"_meta\"");
    assertThat(json).contains("\"progressToken\":\"tok\"");

    var deserialized = mapper.readValue(json, RequestParams.class);
    assertThat(deserialized.meta().progressToken()).isEqualTo("tok");
  }

  @Test
  void requestParamsNullMetaOmitted() throws Exception {
    var params = new RequestParams(null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void paginatedRequestParamsRoundTrip() throws Exception {
    var params = new PaginatedRequestParams("cursor-123", new RequestMeta("tok"));
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"cursor\":\"cursor-123\"");
    assertThat(json).contains("\"_meta\"");

    var deserialized = mapper.readValue(json, PaginatedRequestParams.class);
    assertThat(deserialized.cursor()).isEqualTo("cursor-123");
    assertThat(deserialized.meta().progressToken()).isEqualTo("tok");
  }

  @Test
  void paginatedRequestParamsNullFieldsOmitted() throws Exception {
    var params = new PaginatedRequestParams(null, null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void notificationParamsRoundTrip() throws Exception {
    var params = new NotificationParams(null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void taskMetadataRoundTrip() throws Exception {
    var task = new TaskMetadata("task-1");
    String json = mapper.writeValueAsString(task);
    assertThat(json).isEqualTo("{\"taskId\":\"task-1\"}");

    var deserialized = mapper.readValue(json, TaskMetadata.class);
    assertThat(deserialized.taskId()).isEqualTo("task-1");
  }

  @Test
  void initializeRequestParamsRoundTrip() throws Exception {
    var params =
        new InitializeRequestParams(
            "2025-11-25",
            new ClientCapabilities(null, null, null),
            new Implementation("test-client", null, "1.0"),
            null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"protocolVersion\":\"2025-11-25\"");
    assertThat(json).contains("\"clientInfo\"");

    var deserialized = mapper.readValue(json, InitializeRequestParams.class);
    assertThat(deserialized.protocolVersion()).isEqualTo("2025-11-25");
    assertThat(deserialized.clientInfo().name()).isEqualTo("test-client");
  }

  @Test
  void callToolRequestParamsRoundTrip() throws Exception {
    var argsNode = mapper.readTree("{\"key\":\"value\"}");
    var params = new CallToolRequestParams("myTool", argsNode, null, null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"name\":\"myTool\"");
    assertThat(json).contains("\"arguments\":{\"key\":\"value\"}");

    var deserialized = mapper.readValue(json, CallToolRequestParams.class);
    assertThat(deserialized.name()).isEqualTo("myTool");
    assertThat(deserialized.arguments().get("key").asText()).isEqualTo("value");
  }

  @Test
  void callToolRequestParamsWithTask() throws Exception {
    var params =
        new CallToolRequestParams(
            "tool", null, new TaskMetadata("t1"), new RequestMeta("progress-tok"));
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"task\":{\"taskId\":\"t1\"}");
    assertThat(json).contains("\"_meta\"");

    var deserialized = mapper.readValue(json, CallToolRequestParams.class);
    assertThat(deserialized.task().taskId()).isEqualTo("t1");
  }

  @Test
  void getPromptRequestParamsRoundTrip() throws Exception {
    var params = new GetPromptRequestParams("myPrompt", Map.of("arg1", "val1"), null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"name\":\"myPrompt\"");
    assertThat(json).contains("\"arg1\":\"val1\"");

    var deserialized = mapper.readValue(json, GetPromptRequestParams.class);
    assertThat(deserialized.name()).isEqualTo("myPrompt");
    assertThat(deserialized.arguments()).containsEntry("arg1", "val1");
  }

  @Test
  void resourceRequestParamsRoundTrip() throws Exception {
    var params = new ResourceRequestParams("file:///test.txt", null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{\"uri\":\"file:///test.txt\"}");

    var deserialized = mapper.readValue(json, ResourceRequestParams.class);
    assertThat(deserialized.uri()).isEqualTo("file:///test.txt");
  }

  @Test
  void setLevelRequestParamsRoundTrip() throws Exception {
    var params = new SetLevelRequestParams(LoggingLevel.WARNING, null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"level\":\"warning\"");

    var deserialized = mapper.readValue(json, SetLevelRequestParams.class);
    assertThat(deserialized.level()).isEqualTo(LoggingLevel.WARNING);
  }

  @Test
  void modelPreferencesRoundTrip() throws Exception {
    var prefs = new ModelPreferences(List.of(new ModelHint("claude-3")), 0.5, 0.8, 0.9);
    String json = mapper.writeValueAsString(prefs);
    assertThat(json).contains("\"hints\"");
    assertThat(json).contains("\"name\":\"claude-3\"");
    assertThat(json).contains("\"costPriority\":0.5");

    var deserialized = mapper.readValue(json, ModelPreferences.class);
    assertThat(deserialized.hints()).hasSize(1);
    assertThat(deserialized.hints().getFirst().name()).isEqualTo("claude-3");
    assertThat(deserialized.costPriority()).isEqualTo(0.5);
  }

  @Test
  void createMessageRequestParamsRoundTrip() throws Exception {
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
    assertThat(json).contains("\"systemPrompt\":\"You are helpful\"");
    assertThat(json).contains("\"includeContext\":\"thisServer\"");
    assertThat(json).contains("\"maxTokens\":1024");

    var deserialized = mapper.readValue(json, CreateMessageRequestParams.class);
    assertThat(deserialized.messages()).hasSize(1);
    assertThat(deserialized.systemPrompt()).isEqualTo("You are helpful");
    assertThat(deserialized.maxTokens()).isEqualTo(1024);
  }

  @Test
  void completeRequestParamsWithPromptRef() throws Exception {
    var params =
        new CompleteRequestParams(
            new PromptReference("ref/prompt", "myPrompt"),
            new CompletionArgument("arg", "val"),
            null,
            null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"type\":\"ref/prompt\"");
    assertThat(json).contains("\"name\":\"myPrompt\"");

    var deserialized = mapper.readValue(json, CompleteRequestParams.class);
    assertThat(deserialized.ref()).isInstanceOf(PromptReference.class);
    var ref = (PromptReference) deserialized.ref();
    assertThat(ref.name()).isEqualTo("myPrompt");
  }

  @Test
  void completeRequestParamsWithResourceTemplateRef() throws Exception {
    var params =
        new CompleteRequestParams(
            new ResourceTemplateReference("ref/resource", "file:///{path}"),
            new CompletionArgument("path", "/home"),
            new CompletionContext(Map.of("key", "value")),
            null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"type\":\"ref/resource\"");
    assertThat(json).contains("\"uri\":\"file:///{path}\"");

    var deserialized = mapper.readValue(json, CompleteRequestParams.class);
    assertThat(deserialized.ref()).isInstanceOf(ResourceTemplateReference.class);
    assertThat(deserialized.context().arguments()).containsEntry("key", "value");
  }

  @Test
  void completionArgumentRoundTrip() throws Exception {
    var arg = new CompletionArgument("name", "value");
    String json = mapper.writeValueAsString(arg);
    assertThat(json).isEqualTo("{\"name\":\"name\",\"value\":\"value\"}");

    var deserialized = mapper.readValue(json, CompletionArgument.class);
    assertThat(deserialized.name()).isEqualTo("name");
    assertThat(deserialized.value()).isEqualTo("value");
  }

  @Test
  void elicitRequestFormParamsRoundTrip() throws Exception {
    var params = new ElicitRequestFormParams("form", "Please fill", null, null, null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"mode\":\"form\"");
    assertThat(json).contains("\"message\":\"Please fill\"");

    var deserialized = mapper.readValue(json, ElicitRequestFormParams.class);
    assertThat(deserialized.mode()).isEqualTo("form");
    assertThat(deserialized.message()).isEqualTo("Please fill");
  }

  @Test
  void elicitRequestURLParamsRoundTrip() throws Exception {
    var params =
        new ElicitRequestURLParams(
            "url", "Click link", "elicit-1", "https://example.com", null, null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"mode\":\"url\"");
    assertThat(json).contains("\"elicitationId\":\"elicit-1\"");

    var deserialized = mapper.readValue(json, ElicitRequestURLParams.class);
    assertThat(deserialized.mode()).isEqualTo("url");
    assertThat(deserialized.url()).isEqualTo("https://example.com");
  }

  @Test
  void initializedNotificationParamsNullMetaOmitted() throws Exception {
    var params = new InitializedNotificationParams(null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{}");
  }

  @Test
  void cancelledNotificationParamsRoundTrip() throws Exception {
    var params = new CancelledNotificationParams("req-1", "timeout", null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"requestId\":\"req-1\"");
    assertThat(json).contains("\"reason\":\"timeout\"");

    var deserialized = mapper.readValue(json, CancelledNotificationParams.class);
    assertThat(deserialized.requestId()).isEqualTo("req-1");
    assertThat(deserialized.reason()).isEqualTo("timeout");
  }

  @Test
  void cancelledNotificationParamsWithNumericRequestId() throws Exception {
    String json = "{\"requestId\":42,\"reason\":\"cancelled\"}";
    var deserialized = mapper.readValue(json, CancelledNotificationParams.class);
    assertThat(deserialized.requestId()).isEqualTo(42);
  }

  @Test
  void loggingMessageNotificationParamsRoundTrip() throws Exception {
    var params =
        new LoggingMessageNotificationParams(LoggingLevel.ERROR, "myLogger", "error msg", null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).contains("\"level\":\"error\"");
    assertThat(json).contains("\"logger\":\"myLogger\"");
    assertThat(json).contains("\"data\":\"error msg\"");

    var deserialized = mapper.readValue(json, LoggingMessageNotificationParams.class);
    assertThat(deserialized.level()).isEqualTo(LoggingLevel.ERROR);
    assertThat(deserialized.logger()).isEqualTo("myLogger");
  }

  @Test
  void resourceUpdatedNotificationParamsRoundTrip() throws Exception {
    var params = new ResourceUpdatedNotificationParams("file:///updated.txt", null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{\"uri\":\"file:///updated.txt\"}");

    var deserialized = mapper.readValue(json, ResourceUpdatedNotificationParams.class);
    assertThat(deserialized.uri()).isEqualTo("file:///updated.txt");
  }

  @Test
  void elicitationCompleteNotificationParamsRoundTrip() throws Exception {
    var params = new ElicitationCompleteNotificationParams("elicit-42", null);
    String json = mapper.writeValueAsString(params);
    assertThat(json).isEqualTo("{\"elicitationId\":\"elicit-42\"}");

    var deserialized = mapper.readValue(json, ElicitationCompleteNotificationParams.class);
    assertThat(deserialized.elicitationId()).isEqualTo("elicit-42");
  }

  @Test
  void mcpMethodsConstants() {
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
