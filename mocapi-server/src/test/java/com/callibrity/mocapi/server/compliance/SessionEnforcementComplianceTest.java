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
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Lifecycle — Session Management.
 *
 * <p>Verifies session enforcement: calls/notifications/responses without valid sessions are
 * rejected or silently dropped. Also verifies terminate behavior.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionEnforcementComplianceTest {

  private McpSessionStore sessionStore;
  private McpServer server;

  @BeforeEach
  void setUp() {
    sessionStore = inMemorySessionStore();
    server = buildServer(sessionStore, new ServerCapabilities(null, null, null, null, null));
  }

  @Nested
  class Calls {

    @Test
    void without_session_id_returns_error() {
      var transport = mock(McpTransport.class);

      server.handleCall(noSession(), call("tools/list"), transport);

      var error = captureError(transport);
      assertThat(error.error().message()).containsIgnoringCase("session");
    }

    @Test
    void with_unknown_session_id_returns_error() {
      var transport = mock(McpTransport.class);

      server.handleCall(withSession("nonexistent"), call("tools/list"), transport);

      var error = captureError(transport);
      assertThat(error.error().message()).containsIgnoringCase("session");
    }

    @Test
    void with_valid_session_dispatches_normally() {
      var sessionId = initializeAndGetSessionId(server);
      var transport = mock(McpTransport.class);

      server.handleCall(withSession(sessionId), call("tools/list"), transport);

      var msg = captureMessage(transport);
      assertThat(msg).isNotNull();
    }
  }

  @Nested
  class Notifications {

    @Test
    void without_session_id_silently_rejected() {
      server.handleNotification(noSession(), notification("notifications/initialized"));
    }

    @Test
    void with_unknown_session_id_silently_rejected() {
      server.handleNotification(
          withSession("nonexistent"), notification("notifications/initialized"));
    }
  }

  @Nested
  class Responses {

    @Test
    void without_session_id_still_delivered() {
      var response = new com.callibrity.ripcurl.core.JsonRpcResult(null, null);
      server.handleResponse(noSession(), response);
    }
  }

  @Nested
  class Terminate {

    @Test
    void deletes_session_from_store() {
      var sessionId = initializeAndGetSessionId(server);

      server.terminate(sessionId);

      assertThat(sessionStore.find(sessionId)).isEmpty();
    }

    @Test
    void unknown_session_is_noop() {
      server.terminate("does-not-exist");
    }

    @Test
    void subsequent_calls_with_terminated_session_return_error() {
      var sessionId = initializeAndGetSessionId(server);
      server.terminate(sessionId);

      var transport = mock(McpTransport.class);
      server.handleCall(withSession(sessionId), call("tools/list"), transport);

      var error = captureError(transport);
      assertThat(error.error().message()).containsIgnoringCase("session");
    }
  }
}
