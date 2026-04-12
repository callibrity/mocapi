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

import com.callibrity.mocapi.model.LoggingCapability;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.logging.McpLoggingService;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Server / Utilities — Logging.
 *
 * <p>Verifies logging/setLevel updates the session's log level and persists across requests.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoggingComplianceTest {

  private McpServer server;
  private McpSessionStore sessionStore;

  @BeforeEach
  void setUp() {
    sessionStore = inMemorySessionStore();
    var caps = new ServerCapabilities(null, new LoggingCapability(), null, null, null);
    var sessionService = buildSessionService(sessionStore, caps);
    var loggingService = new McpLoggingService(sessionService);
    server = buildServer(sessionService, mock(McpResponseCorrelationService.class), loggingService);
  }

  @Test
  void set_level_updates_session_log_level() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId), call("logging/setLevel", Map.of("level", "debug")), transport);

    var result = captureResult(transport);
    assertThat(result).isInstanceOf(JsonRpcResult.class);

    var session = sessionStore.find(sessionId);
    assertThat(session).isPresent();
    assertThat(session.get().logLevel()).isEqualTo(LoggingLevel.DEBUG);
  }

  @Test
  void log_level_persists_across_subsequent_requests() {
    var sessionId = initializeAndGetSessionId(server);

    var transport1 = mock(McpTransport.class);
    server.handleCall(
        withSession(sessionId), call("logging/setLevel", Map.of("level", "error")), transport1);

    var session = sessionStore.find(sessionId);
    assertThat(session).isPresent();
    assertThat(session.get().logLevel()).isEqualTo(LoggingLevel.ERROR);

    var transport2 = mock(McpTransport.class);
    server.handleCall(
        withSession(sessionId), call("logging/setLevel", Map.of("level", "info")), transport2);

    session = sessionStore.find(sessionId);
    assertThat(session).isPresent();
    assertThat(session.get().logLevel()).isEqualTo(LoggingLevel.INFO);
  }
}
