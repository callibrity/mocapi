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

import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.McpContextResult;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.ping.McpPingService;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Utilities — Ping.
 *
 * <p>Verifies that ping returns an empty result and requires a valid session.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PingComplianceTest {

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
  void ping_returns_empty_result() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("ping"), transport);

    var result = captureResult(transport);
    assertThat(result).isInstanceOf(JsonRpcResult.class);
  }

  @Test
  void ping_requires_session() {
    assertThat(server.createContext(null, null))
        .isInstanceOf(McpContextResult.SessionIdRequired.class);
  }

  @Test
  void ping_works_before_notifications_initialized() {
    var sessionId = initializeWithoutCompletingHandshake(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("ping"), transport);

    var result = captureResult(transport);
    assertThat(result).isInstanceOf(JsonRpcResult.class);
  }
}
