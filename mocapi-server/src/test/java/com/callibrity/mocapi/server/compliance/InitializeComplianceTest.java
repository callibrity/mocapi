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

import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MCP 2025-11-25 § Lifecycle — Initialize.
 *
 * <p>Verifies the server's initialize handshake: protocol version negotiation, capability
 * reporting, session creation, and event emission. All tests use a mock {@link McpTransport} — zero
 * HTTP, zero Spring context.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InitializeComplianceTest {

  private McpSessionStore sessionStore;
  private McpServer server;

  @BeforeEach
  void setUp() {
    sessionStore = inMemorySessionStore();
    server =
        buildServer(
            sessionStore, new ServerCapabilities(null, null, null, null, null), call -> null);
  }

  @Test
  void returns_protocol_version() {
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    var result = captureResult(transport);
    assertThat(result.result().path("protocolVersion").asString()).isEqualTo(PROTOCOL_VERSION);
  }

  @Test
  void returns_server_info_with_name_version_and_title() {
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    var result = captureResult(transport);
    var serverInfo = result.result().path("serverInfo");
    assertThat(serverInfo.path("name").asString()).isEqualTo("test-server");
    assertThat(serverInfo.path("version").asString()).isEqualTo("1.0.0");
    assertThat(serverInfo.path("title").asString()).isEqualTo("Test Server");
  }

  @Test
  void returns_capabilities() {
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    var result = captureResult(transport);
    assertThat(result.result().has("capabilities")).isTrue();
  }

  @Test
  void emits_session_initialized_event_with_session_id_and_protocol_version() {
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    var event = captureEvent(transport);
    assertThat(event).isInstanceOf(McpEvent.SessionInitialized.class);
    var init = (McpEvent.SessionInitialized) event;
    assertThat(init.sessionId()).isNotNull().isNotEmpty();
    assertThat(init.protocolVersion()).isEqualTo(PROTOCOL_VERSION);
  }

  @Test
  void creates_session_in_store() {
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    var sessionId = ((McpEvent.SessionInitialized) captureEvent(transport)).sessionId();
    assertThat(sessionStore.find(sessionId)).isPresent();
  }

  @Test
  void does_not_require_session_id() {
    var transport = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport);

    assertThat(captureMessage(transport)).isInstanceOf(JsonRpcResult.class);
  }

  @Test
  void second_initialize_with_same_transport_creates_new_session() {
    var transport1 = mock(McpTransport.class);
    var transport2 = mock(McpTransport.class);

    server.handleCall(noSession(), initializeCall(), transport1);
    server.handleCall(noSession(), initializeCall(), transport2);

    var session1 = ((McpEvent.SessionInitialized) captureEvent(transport1)).sessionId();
    var session2 = ((McpEvent.SessionInitialized) captureEvent(transport2)).sessionId();
    assertThat(session1).isNotEqualTo(session2);
  }

  @Nested
  class Capabilities_reflect_registrations {

    @Test
    void empty_tool_registry_has_null_tools_capability() {
      var transport = mock(McpTransport.class);

      server.handleCall(noSession(), initializeCall(), transport);

      var result = captureResult(transport);
      assertThat(result.result().path("capabilities").has("tools")).isFalse();
    }

    @Test
    void empty_resource_registry_has_null_resources_capability() {
      var transport = mock(McpTransport.class);

      server.handleCall(noSession(), initializeCall(), transport);

      var result = captureResult(transport);
      assertThat(result.result().path("capabilities").has("resources")).isFalse();
    }

    @Test
    void empty_prompt_registry_has_null_prompts_capability() {
      var transport = mock(McpTransport.class);

      server.handleCall(noSession(), initializeCall(), transport);

      var result = captureResult(transport);
      assertThat(result.result().path("capabilities").has("prompts")).isFalse();
    }

    @Test
    void registered_tools_reflected_in_capabilities() {
      var serverWithTools =
          buildServerWithCapabilities(
              new ServerCapabilities(new ToolsCapability(null), null, null, null, null));
      var transport = mock(McpTransport.class);

      serverWithTools.handleCall(noSession(), initializeCall(), transport);

      var result = captureResult(transport);
      assertThat(result.result().path("capabilities").has("tools")).isTrue();
    }

    @Test
    void registered_resources_reflected_in_capabilities() {
      var serverWithResources =
          buildServerWithCapabilities(
              new ServerCapabilities(null, null, null, new ResourcesCapability(null, null), null));
      var transport = mock(McpTransport.class);

      serverWithResources.handleCall(noSession(), initializeCall(), transport);

      var result = captureResult(transport);
      assertThat(result.result().path("capabilities").has("resources")).isTrue();
    }

    @Test
    void registered_prompts_reflected_in_capabilities() {
      var serverWithPrompts =
          buildServerWithCapabilities(
              new ServerCapabilities(null, null, null, null, new PromptsCapability(null)));
      var transport = mock(McpTransport.class);

      serverWithPrompts.handleCall(noSession(), initializeCall(), transport);

      var result = captureResult(transport);
      assertThat(result.result().path("capabilities").has("prompts")).isTrue();
    }

    private McpServer buildServerWithCapabilities(ServerCapabilities caps) {
      return ComplianceTestSupport.buildServer(inMemorySessionStore(), caps, call -> null);
    }
  }
}
