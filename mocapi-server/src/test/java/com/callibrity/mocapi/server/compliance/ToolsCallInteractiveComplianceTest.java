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
package com.callibrity.mocapi.server.compliance;

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.MAPPER;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.buildServer;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.call;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.callWithMeta;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.captureResult;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.StructuredResultMapper;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.ObjectNode;

/**
 * MCP 2026-07-28 § Server / Tools — interactive behavior.
 *
 * <p>Verifies that McpToolContext is available during tools/call, progress notifications flow on
 * the request's transport, and the progress token from the request {@code _meta} is echoed.
 * (Elicitation is MRTR replay — capability gating is unit-tested on DefaultMcpToolContext; the
 * replay engine arrives in Phase 4.)
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolsCallInteractiveComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    var inputSchema = MAPPER.createObjectNode().put("type", "object");
    inputSchema.putObject("properties").putObject("name").put("type", "string");

    var interactiveDescriptor =
        new Tool("interactive", null, "Interactive tool", inputSchema, null);
    CallToolHandler interactiveTool =
        new CallToolHandler(
            interactiveDescriptor,
            null,
            null,
            arguments -> {
              McpToolContext ctx = McpToolContext.CURRENT.get();
              var progress = ctx.longProgress(2L);
              progress.emit(1);
              progress.emit(2);
              return Map.of("done", true);
            },
            List.of(),
            new StructuredResultMapper(MAPPER));

    var contextCheckDescriptor =
        new Tool("context-check", null, "Checks context", inputSchema, null);
    CallToolHandler contextCheckTool =
        new CallToolHandler(
            contextCheckDescriptor,
            null,
            null,
            arguments -> Map.of("hasContext", McpToolContext.CURRENT.isBound()),
            List.of(),
            new StructuredResultMapper(MAPPER));

    var toolsService =
        new McpToolsService(
            List.of(interactiveTool, contextCheckTool), MAPPER, ComplianceTestSupport.mrtrEngine());

    server = buildServer(toolsService);
  }

  private static ObjectNode envelopeWithProgressToken(String token) {
    ObjectNode meta = envelope();
    meta.put("progressToken", token);
    return meta;
  }

  @Test
  void interactive_tool_receives_context() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        call("tools/call", Map.of("name", "context-check", "arguments", Map.of("name", "x"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().path("structuredContent").path("hasContext").booleanValue())
        .isTrue();
  }

  @Test
  void send_progress_emits_progress_notifications_via_transport() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        callWithMeta(
            "tools/call",
            Map.of("name", "interactive", "arguments", Map.of("name", "Alice")),
            envelopeWithProgressToken("tok-1")),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var notifications =
        captor.getAllValues().stream()
            .filter(JsonRpcNotification.class::isInstance)
            .map(m -> (JsonRpcNotification) m)
            .filter(n -> "notifications/progress".equals(n.method()))
            .toList();
    assertThat(notifications).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void progress_token_from_meta_included_in_progress_notifications() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        callWithMeta(
            "tools/call",
            Map.of("name", "interactive", "arguments", Map.of("name", "Alice")),
            envelopeWithProgressToken("my-token")),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var progressNotifications =
        captor.getAllValues().stream()
            .filter(JsonRpcNotification.class::isInstance)
            .map(m -> (JsonRpcNotification) m)
            .filter(n -> "notifications/progress".equals(n.method()))
            .toList();
    assertThat(progressNotifications).isNotEmpty();
    for (var notification : progressNotifications) {
      assertThat(notification.params().path("progressToken").asString()).isEqualTo("my-token");
    }
  }

  @Test
  void interactive_tool_returns_result_after_notifications() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        callWithMeta(
            "tools/call",
            Map.of("name", "interactive", "arguments", Map.of("name", "Alice")),
            envelopeWithProgressToken("tok-1")),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var messages = captor.getAllValues();

    assertThat(messages.getLast()).isInstanceOf(JsonRpcResult.class);
  }

  @Test
  void tool_without_progress_token_sends_no_progress_notifications() {
    var transport = mock(McpTransport.class);

    server.handleCall(
        call("tools/call", Map.of("name", "interactive", "arguments", Map.of("name", "Alice"))),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getAllValues()).hasSize(1);
    assertThat(captor.getValue()).isInstanceOf(JsonRpcResult.class);
  }
}
