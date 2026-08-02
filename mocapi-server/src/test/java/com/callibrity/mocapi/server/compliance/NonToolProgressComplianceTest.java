/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.compliance;

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.buildServer;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.callWithMeta;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.envelope;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.mrtrEngine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.api.prompts.McpPromptContext;
import com.callibrity.mocapi.api.resources.McpResourceContext;
import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.prompts.GetPromptHandler;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.ObjectNode;

/**
 * Progress notifications from non-tool handlers (ADR-0025). The 2026-07-28 progress token rides any
 * request's {@code _meta}, and the Streamable HTTP transport's JSON-vs-SSE switch is
 * method-agnostic — so {@code prompts/get} and {@code resources/read} handlers can report progress
 * (and thereby stream) exactly as tools do, through their {@code McpPromptContext} / {@code
 * McpResourceContext}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NonToolProgressComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    GetPromptHandler progressPrompt =
        new GetPromptHandler(
            new Prompt("progress-prompt", "ProgressPrompt", "Reports progress", null, List.of()),
            null,
            null,
            args -> {
              var p = McpPromptContext.CURRENT.get().percentProgress();
              p.complete(0.5, "halfway");
              p.complete(1.0, "done");
              return new GetPromptResult(
                  "ProgressPrompt",
                  List.of(new PromptMessage(Role.USER, new TextContent("ok", null))),
                  ResultTypes.COMPLETE);
            },
            List.of(),
            List.of());
    var promptsService = new McpPromptsService(List.of(progressPrompt), mrtrEngine());

    ReadResourceHandler progressResource =
        new ReadResourceHandler(
            new Resource("file:///progress", "Progress", "Reports progress", "text/plain"),
            null,
            null,
            ignored -> {
              var p = McpResourceContext.CURRENT.get().countingProgress(2L);
              p.emit("step 1");
              p.emit("step 2");
              return new ReadResourceResult(
                  List.of(new TextResourceContents("file:///progress", "text/plain", "done")),
                  0L,
                  CacheScope.PRIVATE,
                  ResultTypes.COMPLETE);
            },
            List.of());
    var resourcesService =
        new McpResourcesService(
            List.of(ResourceContributor.of(List.of(progressResource), List.of())), mrtrEngine());

    server = buildServer(promptsService, resourcesService);
  }

  private static ObjectNode envelopeWithProgressToken(String token) {
    ObjectNode meta = envelope();
    meta.put("progressToken", token);
    return meta;
  }

  private static List<JsonRpcNotification> progressNotifications(McpTransport transport) {
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
    return captor.getAllValues().stream()
        .filter(JsonRpcNotification.class::isInstance)
        .map(JsonRpcNotification.class::cast)
        .filter(n -> "notifications/progress".equals(n.method()))
        .toList();
  }

  @Test
  void a_prompt_handler_emits_progress_notifications_with_the_meta_token() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        callWithMeta(
            "prompts/get", Map.of("name", "progress-prompt"), envelopeWithProgressToken("p-tok")),
        transport);

    var notifications = progressNotifications(transport);
    assertThat(notifications).hasSize(2);
    assertThat(notifications.get(0).params().path("progressToken").asString()).isEqualTo("p-tok");
    assertThat(notifications.get(0).params().path("progress").asDouble()).isEqualTo(0.5);
    assertThat(notifications.get(0).params().path("total").asDouble()).isEqualTo(1.0);
    assertThat(notifications.get(1).params().path("progress").asDouble()).isEqualTo(1.0);
  }

  @Test
  void a_resource_handler_emits_progress_notifications_with_the_meta_token() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        callWithMeta(
            "resources/read",
            Map.of("uri", "file:///progress"),
            envelopeWithProgressToken("r-tok")),
        transport);

    var notifications = progressNotifications(transport);
    assertThat(notifications).hasSize(2);
    assertThat(notifications.get(0).params().path("progressToken").asString()).isEqualTo("r-tok");
    assertThat(notifications.get(0).params().path("progress").asDouble()).isEqualTo(1.0);
    assertThat(notifications.get(1).params().path("progress").asDouble()).isEqualTo(2.0);
  }

  @Test
  void a_prompt_handler_without_a_progress_token_sends_no_notifications() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        callWithMeta("prompts/get", Map.of("name", "progress-prompt"), envelope()), transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getAllValues())
        .noneMatch(
            m -> m instanceof JsonRpcNotification n && "notifications/progress".equals(n.method()));
  }
}
