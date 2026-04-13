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

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.mocapi.server.tools.McpTool;
import com.callibrity.mocapi.server.tools.McpToolContext;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;

/**
 * MCP 2025-11-25 § Server / Tools + Client / Elicitation + Client / Sampling.
 *
 * <p>Verifies interactive tool behavior: McpToolContext is available, progress and log
 * notifications are sent, and progress tokens from request _meta are included. Elicitation and
 * sampling tests require async coordination and are tested at the service level in
 * McpToolsServiceTest.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolsCallInteractiveComplianceTest {

  private McpServer server;
  private McpSessionStore sessionStore;

  @BeforeEach
  void setUp() {
    var inputSchema = MAPPER.createObjectNode().put("type", "object");
    inputSchema.putObject("properties").putObject("name").put("type", "string");

    McpTool interactiveTool =
        new McpTool() {
          @Override
          public Tool descriptor() {
            return new Tool("interactive", null, "Interactive tool", inputSchema, null);
          }

          @Override
          public Object call(JsonNode arguments) {
            McpToolContext ctx = McpToolContext.CURRENT.get();
            ctx.sendProgress(1, 2);
            ctx.log(LoggingLevel.INFO, "interactive", "Processing");
            ctx.sendProgress(2, 2);
            return Map.of("done", true);
          }
        };

    McpTool contextCheckTool =
        new McpTool() {
          @Override
          public Tool descriptor() {
            return new Tool("context-check", null, "Checks context", inputSchema, null);
          }

          @Override
          public Object call(JsonNode arguments) {
            return Map.of("hasContext", McpToolContext.CURRENT.isBound());
          }
        };

    sessionStore = inMemorySessionStore();
    var toolsService =
        new McpToolsService(
            List.of(() -> List.of(interactiveTool, contextCheckTool)),
            MAPPER,
            mock(McpResponseCorrelationService.class));

    server =
        buildServer(
            sessionStore,
            new ServerCapabilities(new ToolsCapability(null), null, null, null, null),
            toolsService);
  }

  @Test
  void interactive_tool_receives_context() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "context-check", "arguments", Map.of("name", "x"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().path("structuredContent").path("hasContext").booleanValue())
        .isTrue();
  }

  @Test
  void send_progress_emits_progress_notification_via_transport() {
    var sessionId = initializeAndGetSessionId(server);
    bindSessionForCapture(sessionId);

    var transport = mock(McpTransport.class);
    server.handleCall(
        withSession(sessionId),
        call(
            "tools/call",
            Map.of(
                "name", "interactive",
                "arguments", Map.of("name", "Alice"),
                "_meta", Map.of("progressToken", "tok-1"))),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var notifications =
        captor.getAllValues().stream()
            .filter(m -> m instanceof JsonRpcNotification)
            .map(m -> (JsonRpcNotification) m)
            .filter(n -> "notifications/progress".equals(n.method()))
            .toList();
    assertThat(notifications).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  void log_emits_message_notification_via_transport() {
    var sessionId = initializeAndGetSessionId(server);
    bindSessionForCapture(sessionId);

    var transport = mock(McpTransport.class);
    server.handleCall(
        withSession(sessionId),
        call(
            "tools/call",
            Map.of(
                "name", "interactive",
                "arguments", Map.of("name", "Alice"),
                "_meta", Map.of("progressToken", "tok-1"))),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var logNotifications =
        captor.getAllValues().stream()
            .filter(m -> m instanceof JsonRpcNotification)
            .map(m -> (JsonRpcNotification) m)
            .filter(n -> "notifications/message".equals(n.method()))
            .toList();
    assertThat(logNotifications).hasSize(1);
  }

  @Test
  void progress_token_from_meta_included_in_progress_notifications() {
    var sessionId = initializeAndGetSessionId(server);
    bindSessionForCapture(sessionId);

    var transport = mock(McpTransport.class);
    server.handleCall(
        withSession(sessionId),
        call(
            "tools/call",
            Map.of(
                "name", "interactive",
                "arguments", Map.of("name", "Alice"),
                "_meta", Map.of("progressToken", "my-token"))),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var progressNotifications =
        captor.getAllValues().stream()
            .filter(m -> m instanceof JsonRpcNotification)
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
    var sessionId = initializeAndGetSessionId(server);
    bindSessionForCapture(sessionId);

    var transport = mock(McpTransport.class);
    server.handleCall(
        withSession(sessionId),
        call(
            "tools/call",
            Map.of(
                "name", "interactive",
                "arguments", Map.of("name", "Alice"),
                "_meta", Map.of("progressToken", "tok-1"))),
        transport);

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(4)).send(captor.capture());
    var messages = captor.getAllValues();

    var lastMessage = messages.getLast();
    assertThat(lastMessage).isInstanceOf(com.callibrity.ripcurl.core.JsonRpcResult.class);
  }

  private void bindSessionForCapture(String sessionId) {
    // Ensure session has DEBUG level so log notifications pass through
    sessionStore
        .find(sessionId)
        .ifPresent(
            session -> {
              var updated = session.withLogLevel(LoggingLevel.DEBUG);
              sessionStore.update(sessionId, updated);
            });
  }
}
