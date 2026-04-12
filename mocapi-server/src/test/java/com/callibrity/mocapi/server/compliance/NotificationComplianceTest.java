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

import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.ping.McpPingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Lifecycle — Notifications.
 *
 * <p>Verifies notification handling: notifications/initialized is processed, and unknown
 * notification methods are silently ignored per JSON-RPC spec.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    var dispatcher = buildDispatcher(MAPPER, new McpPingService());
    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, null),
            dispatcher);
  }

  @Test
  void notifications_initialized_processed_without_error() {
    var sessionId = initializeAndGetSessionId(server);

    server.handleNotification(withSession(sessionId), notification("notifications/initialized"));
  }

  @Test
  void unknown_notification_method_silently_ignored() {
    var sessionId = initializeAndGetSessionId(server);

    server.handleNotification(withSession(sessionId), notification("notifications/unknown_method"));
  }
}
