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

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.buildServer;
import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.notification;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.McpServer;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2026-07-28 § Lifecycle — Notifications.
 *
 * <p>Verifies notification handling in the stateless model: {@code notifications/cancelled} is
 * acknowledged (with or without the now-optional {@code requestId}), and unknown notification
 * methods are silently ignored per JSON-RPC spec.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    server = buildServer();
  }

  @Test
  void notifications_cancelled_processed_without_error() {
    assertThatNoException()
        .isThrownBy(
            () ->
                server.handleNotification(
                    notification(
                        McpMethods.NOTIFICATIONS_CANCELLED,
                        Map.of("requestId", "42", "reason", "user clicked cancel"))));
  }

  @Test
  void notifications_cancelled_without_request_id_is_accepted() {
    // The 2026-07-28 revision made requestId optional on CancelledNotificationParams.
    assertThatNoException()
        .isThrownBy(
            () ->
                server.handleNotification(
                    notification(McpMethods.NOTIFICATIONS_CANCELLED, Map.of("reason", "gone"))));
  }

  @Test
  void unknown_notification_method_silently_ignored() {
    assertThatNoException()
        .isThrownBy(() -> server.handleNotification(notification("notifications/unknown_method")));
  }
}
