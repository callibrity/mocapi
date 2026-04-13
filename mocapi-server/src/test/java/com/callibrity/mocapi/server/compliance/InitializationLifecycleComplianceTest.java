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
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.ping.McpPingService;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Lifecycle — Initialization handshake enforcement and notification handlers for
 * {@code notifications/initialized}, {@code notifications/cancelled}, and {@code
 * notifications/roots/list_changed}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InitializationLifecycleComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, null),
            new McpPingService());
  }

  @Test
  void request_before_notifications_initialized_is_rejected() {
    var sessionId = initializeWithoutCompletingHandshake(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("tools/list"), transport);

    JsonRpcError error = captureError(transport);
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_REQUEST);
    assertThat(error.error().message()).containsIgnoringCase("not initialized");
  }

  @Test
  void ping_succeeds_before_notifications_initialized() {
    var sessionId = initializeWithoutCompletingHandshake(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("ping"), transport);

    assertThat(captureMessage(transport)).isInstanceOf(JsonRpcResult.class);
  }

  @Test
  void request_succeeds_after_notifications_initialized() {
    var sessionId = initializeWithoutCompletingHandshake(server);
    server.handleNotification(
        withSession(sessionId, server), notification(McpMethods.NOTIFICATIONS_INITIALIZED));

    var transport = mock(McpTransport.class);
    server.handleCall(withSession(sessionId, server), call("ping"), transport);

    assertThat(captureMessage(transport)).isInstanceOf(JsonRpcResult.class);
  }

  @Test
  void notifications_cancelled_is_accepted() {
    var sessionId = initializeAndGetSessionId(server);

    server.handleNotification(
        withSession(sessionId, server),
        notification(
            McpMethods.NOTIFICATIONS_CANCELLED,
            java.util.Map.of("requestId", 42, "reason", "user cancelled")));
  }

  @Test
  void notifications_roots_list_changed_is_accepted() {
    var sessionId = initializeAndGetSessionId(server);

    server.handleNotification(
        withSession(sessionId, server), notification(McpMethods.NOTIFICATIONS_ROOTS_LIST_CHANGED));
  }

  @Test
  void non_initialized_notification_is_silently_dropped_before_handshake() {
    var sessionId = initializeWithoutCompletingHandshake(server);

    server.handleNotification(
        withSession(sessionId, server), notification(McpMethods.NOTIFICATIONS_ROOTS_LIST_CHANGED));
  }
}
