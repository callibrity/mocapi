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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.api.tools.McpLogger;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.LoggingMessageNotificationParams;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link McpLogger} that publishes {@code notifications/message} entries through an {@link
 * McpTransport}. Entries below the session's current log level (when a session is bound) are
 * silently dropped; with no session bound, every level is considered enabled.
 */
public final class DefaultMcpLogger implements McpLogger {

  private final McpTransport transport;
  private final ObjectMapper objectMapper;
  private final String loggerName;

  public DefaultMcpLogger(McpTransport transport, ObjectMapper objectMapper, String loggerName) {
    this.transport = transport;
    this.objectMapper = objectMapper;
    this.loggerName = loggerName;
  }

  @Override
  public boolean isEnabled(LoggingLevel level) {
    if (McpSession.CURRENT.isBound()) {
      return level.ordinal() >= McpSession.CURRENT.get().logLevel().ordinal();
    }
    return true;
  }

  @Override
  public void log(LoggingLevel level, String message) {
    if (!isEnabled(level)) {
      return;
    }
    var params = new LoggingMessageNotificationParams(level, loggerName, message, null);
    transport.send(
        new JsonRpcNotification(
            "2.0", McpMethods.NOTIFICATIONS_MESSAGE, objectMapper.valueToTree(params)));
  }
}
