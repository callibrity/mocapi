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
import com.callibrity.mocapi.server.ValidationResult;
import com.callibrity.mocapi.server.session.McpSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Lifecycle — Session Management.
 *
 * <p>Verifies session validation, dispatch with valid sessions, and terminate behavior.
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
  class Validate {

    @Test
    void initialize_returns_valid_without_session() {
      assertThat(server.validate(noSession(), initializeCall()))
          .isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void non_initialize_without_session_returns_missing_session_id() {
      assertThat(server.validate(noSession(), call("tools/list")))
          .isInstanceOf(ValidationResult.MissingSessionId.class);
    }

    @Test
    void notification_without_session_returns_missing_session_id() {
      assertThat(server.validate(noSession(), notification("notifications/initialized")))
          .isInstanceOf(ValidationResult.MissingSessionId.class);
    }

    @Test
    void returns_valid_for_active_session() {
      var sessionId = initializeAndGetSessionId(server);
      assertThat(server.validate(withSession(sessionId), call("tools/list")))
          .isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void returns_unknown_session_for_nonexistent_session() {
      assertThat(server.validate(withSession("nonexistent"), call("tools/list")))
          .isInstanceOf(ValidationResult.UnknownSession.class);
    }

    @Test
    void returns_unknown_session_after_terminate() {
      var sessionId = initializeAndGetSessionId(server);
      server.terminate(sessionId);
      assertThat(server.validate(withSession(sessionId), call("tools/list")))
          .isInstanceOf(ValidationResult.UnknownSession.class);
    }

    @Test
    void context_validate_returns_valid_for_active_session() {
      var sessionId = initializeAndGetSessionId(server);
      assertThat(server.validate(withSession(sessionId)))
          .isInstanceOf(ValidationResult.Valid.class);
    }

    @Test
    void context_validate_returns_unknown_for_nonexistent_session() {
      assertThat(server.validate(withSession("nonexistent")))
          .isInstanceOf(ValidationResult.UnknownSession.class);
    }
  }

  @Nested
  class Calls {

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
  class Terminate {

    @Test
    void deletes_session_from_store() {
      var sessionId = initializeAndGetSessionId(server);

      server.terminate(sessionId);

      assertThat(sessionStore.find(sessionId)).isEmpty();
    }
  }
}
