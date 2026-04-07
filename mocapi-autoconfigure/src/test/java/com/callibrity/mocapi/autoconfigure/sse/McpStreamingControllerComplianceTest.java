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
package com.callibrity.mocapi.autoconfigure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpServer;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tests for MCP Streamable HTTP transport compliance gaps (spec 009). Covers: Gap 1 (notification
 * 202), Gap 2 (DELETE endpoint), Gap 5 (Accept header validation).
 */
class McpStreamingControllerComplianceTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";

  private McpStreamingController controller;
  private McpSessionManager sessionManager;
  private ObjectMapper objectMapper;
  private OdysseyStreamRegistry registry;

  @BeforeEach
  void setUp() {
    McpServer mcpServer = new McpServer(List.of(), null, null);
    registry = mock(OdysseyStreamRegistry.class);

    OdysseyStream notificationStream = mock(OdysseyStream.class);
    when(notificationStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.channel(anyString())).thenReturn(notificationStream);

    OdysseyStream ephemeralStream = mock(OdysseyStream.class);
    when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.ephemeral()).thenReturn(ephemeralStream);

    sessionManager = new McpSessionManager(registry);
    objectMapper = new ObjectMapper();
    controller =
        new McpStreamingController(
            mcpServer, sessionManager, registry, objectMapper, List.of("localhost"), null);
  }

  // ---- Gap 1: Notifications must return 202 Accepted ----

  @Nested
  class NotificationHandling {

    @Test
    void shouldReturn202ForNotification() {
      McpSession session = sessionManager.createSession();

      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/initialized");

      var response =
          controller.handlePost(notification, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReturn202ForJsonRpcResponse() {
      McpSession session = sessionManager.createSession();

      ObjectNode jsonRpcResponse = objectMapper.createObjectNode();
      jsonRpcResponse.put("jsonrpc", "2.0");
      jsonRpcResponse.put("id", 1);
      jsonRpcResponse.putObject("result");

      var response =
          controller.handlePost(jsonRpcResponse, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReturn202ForJsonRpcErrorResponse() {
      McpSession session = sessionManager.createSession();

      ObjectNode errorResponse = objectMapper.createObjectNode();
      errorResponse.put("jsonrpc", "2.0");
      errorResponse.put("id", 1);
      ObjectNode error = errorResponse.putObject("error");
      error.put("code", -32600);
      error.put("message", "Invalid Request");

      var response =
          controller.handlePost(errorResponse, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldInvokeClientInitializedForNotification() {
      McpSession session = sessionManager.createSession();

      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/initialized");

      var response =
          controller.handlePost(notification, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
  }

  // ---- Gap 2: DELETE endpoint for session termination ----

  @Nested
  class DeleteEndpoint {

    @Test
    void shouldReturn204ForValidSessionTermination() {
      McpSession session = sessionManager.createSession();
      var response = controller.handleDelete(session.getSessionId());

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(sessionManager.getSession(session.getSessionId())).isEmpty();
    }

    @Test
    void shouldDeleteNotificationStreamOnTermination() {
      OdysseyStream stream = mock(OdysseyStream.class);
      when(registry.channel(anyString())).thenReturn(stream);
      McpSession session = sessionManager.createSession();

      controller.handleDelete(session.getSessionId());

      verify(stream).delete();
    }

    @Test
    void shouldReturn400WhenSessionIdMissing() {
      var response = controller.handleDelete(null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn404WhenSessionNotFound() {
      var response = controller.handleDelete("nonexistent-session");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  // ---- Gap 5: Accept header validation ----

  @Nested
  class AcceptHeaderValidation {

    static Stream<String> invalidPostAcceptHeaders() {
      return Stream.of("text/event-stream", "application/json");
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("invalidPostAcceptHeaders")
    void shouldReturn406WhenPostAcceptHeaderIsInvalid(String acceptHeader) {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, acceptHeader, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptWildcardOnPost() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, "*/*", null);
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldReturn406WhenGetMissingAcceptHeader() {
      McpSession session = sessionManager.createSession();
      var response = controller.handleGet(session.getSessionId(), null, null, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldReturn406WhenGetAcceptMissesSse() {
      McpSession session = sessionManager.createSession();
      var response = controller.handleGet(session.getSessionId(), null, null, "application/json");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptWildcardOnGet() {
      McpSession session = sessionManager.createSession();
      var response = controller.handleGet(session.getSessionId(), null, null, "*/*");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptValidPostAcceptHeader() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, null, "application/json, text/event-stream", null);
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptValidGetAcceptHeader() {
      McpSession session = sessionManager.createSession();
      var response = controller.handleGet(session.getSessionId(), null, null, "text/event-stream");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }
}
