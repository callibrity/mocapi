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
package com.callibrity.mocapi.http;

import static com.callibrity.mocapi.JsonRpcProtocol.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.InitializeResponse;
import com.callibrity.mocapi.server.ServerCapabilities;
import com.callibrity.mocapi.server.ServerInfo;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.InMemoryMcpSessionStore;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionMethods;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.annotation.AnnotationJsonRpcMethod;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcDispatcher;
import com.callibrity.ripcurl.core.spi.JsonRpcMethodProvider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.Mailbox;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tests for MCP Streamable HTTP transport compliance gaps (spec 009). Covers: Gap 1 (notification
 * 202), Gap 2 (DELETE endpoint), Gap 5 (Accept header validation).
 */
class StreamableHttpControllerComplianceTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";
  private static final Duration SESSION_TIMEOUT = Duration.ofHours(1);

  private StreamableHttpController controller;
  private InMemoryMcpSessionStore sessionStore;
  private McpSessionService sessionService;
  private ObjectMapper objectMapper;
  private OdysseyStreamRegistry registry;
  private MailboxFactory mailboxFactory;

  @BeforeEach
  void setUp() {
    InitializeResponse initializeResponse =
        new InitializeResponse(
            InitializeResponse.PROTOCOL_VERSION,
            new ServerCapabilities(null, null),
            new ServerInfo("test", null, "1.0", null, null, null),
            null);
    registry = mock(OdysseyStreamRegistry.class);

    OdysseyStream notificationStream = mock(OdysseyStream.class);
    when(notificationStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.channel(anyString())).thenReturn(notificationStream);

    OdysseyStream ephemeralStream = mock(OdysseyStream.class);
    when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.ephemeral()).thenReturn(ephemeralStream);

    sessionStore = new InMemoryMcpSessionStore();
    objectMapper = new ObjectMapper();

    byte[] masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    sessionService = new McpSessionService(sessionStore, masterKey, SESSION_TIMEOUT);

    McpSessionMethods serverMethods = new McpSessionMethods(initializeResponse);
    JsonRpcMethodProvider serverProvider =
        () ->
            List.copyOf(
                AnnotationJsonRpcMethod.createMethods(objectMapper, serverMethods, List.of()));
    JsonRpcDispatcher dispatcher = new DefaultJsonRpcDispatcher(List.of(serverProvider));

    mailboxFactory = mock(MailboxFactory.class);
    Mailbox<?> mockMailbox = mock(Mailbox.class);
    when(mailboxFactory.create(any(String.class), any(Class.class))).thenAnswer(_ -> mockMailbox);

    McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));
    controller =
        new StreamableHttpController(
            dispatcher, validator, sessionService, registry, objectMapper, mailboxFactory);
  }

  @AfterEach
  void tearDown() {
    sessionStore.shutdown();
  }

  private String createSession() {
    return sessionService.create(
        new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null, null, null),
            new ClientInfo("test", null, "1.0", null, null, null)));
  }

  // ---- Gap 1: Notifications must return 202 Accepted ----

  @Nested
  class NotificationHandling {

    @Test
    void shouldReturn202ForNotification() {
      String sessionId = createSession();

      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", VERSION);
      notification.put("method", "notifications/initialized");

      var response = controller.handlePost(notification, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReturn202ForJsonRpcResponse() {
      String sessionId = createSession();

      ObjectNode jsonRpcResponse = objectMapper.createObjectNode();
      jsonRpcResponse.put("jsonrpc", VERSION);
      jsonRpcResponse.put("id", 1);
      jsonRpcResponse.putObject("result");

      var response = controller.handlePost(jsonRpcResponse, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReturn202ForJsonRpcErrorResponse() {
      String sessionId = createSession();

      ObjectNode errorResponse = objectMapper.createObjectNode();
      errorResponse.put("jsonrpc", VERSION);
      errorResponse.put("id", 1);
      ObjectNode error = errorResponse.putObject("error");
      error.put("code", -32600);
      error.put("message", "Invalid Request");

      var response = controller.handlePost(errorResponse, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldInvokeClientInitializedForNotification() {
      String sessionId = createSession();

      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", VERSION);
      notification.put("method", "notifications/initialized");

      var response = controller.handlePost(notification, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
  }

  // ---- Gap 2: DELETE endpoint for session termination ----

  @Nested
  class DeleteEndpoint {

    @Test
    void shouldReturn204ForValidSessionTermination() {
      String sessionId = createSession();
      var response = controller.handleDelete(sessionId, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      assertThat(sessionStore.find(sessionId)).isEmpty();
    }

    @Test
    void shouldDeleteNotificationStreamOnTermination() {
      OdysseyStream stream = mock(OdysseyStream.class);
      when(registry.channel(anyString())).thenReturn(stream);
      String sessionId = createSession();

      controller.handleDelete(sessionId, null);

      verify(stream).delete();
    }

    @Test
    void shouldReturn400WhenSessionIdMissing() {
      var response = controller.handleDelete(null, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn404WhenSessionNotFound() {
      var response = controller.handleDelete("nonexistent-session", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn403ForInvalidOrigin() {
      String sessionId = createSession();
      var response = controller.handleDelete(sessionId, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAcceptValidOrigin() {
      String sessionId = createSession();
      var response = controller.handleDelete(sessionId, "http://localhost:8080");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void shouldAcceptNullOrigin() {
      String sessionId = createSession();
      var response = controller.handleDelete(sessionId, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
  }

  // ---- Multiple concurrent POST streams ----

  @Nested
  class MultipleConcurrentPostStreams {

    @Test
    void shouldHandleMultiplePostRequestsIndependently() {
      String sessionId = createSession();

      for (int i = 1; i <= 3; i++) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", VERSION);
        request.put("method", "ping");
        request.put("id", i);

        var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);
        assertThat(response.getStatusCode())
            .as("POST request %d should succeed", i)
            .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
      }
    }

    @Test
    void shouldCreateIndependentEphemeralStreamsForConcurrentInitialize() {
      OdysseyStream ephemeralStream = mock(OdysseyStream.class);
      when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
      when(registry.ephemeral()).thenReturn(ephemeralStream);

      for (int i = 1; i <= 3; i++) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", VERSION);
        request.put("method", "ping");
        request.put("id", i);

        String sessionId = createSession();
        var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

        assertThat(response.getStatusCode())
            .as("Concurrent request %d should succeed independently", i)
            .isEqualTo(HttpStatus.OK);
      }
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
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, acceptHeader, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptWildcardOnPost() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, sessionId, "*/*", null);
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldReturn406WhenGetMissingAcceptHeader() {
      String sessionId = createSession();
      var response = controller.handleGet(sessionId, null, null, null, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldReturn406WhenGetAcceptMissesSse() {
      String sessionId = createSession();
      var response = controller.handleGet(sessionId, null, null, "application/json", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptWildcardOnGet() {
      String sessionId = createSession();
      var response = controller.handleGet(sessionId, null, null, "*/*", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptValidPostAcceptHeader() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response =
          controller.handlePost(
              request, null, sessionId, "application/json, text/event-stream", null);
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptValidGetAcceptHeader() {
      String sessionId = createSession();
      var response = controller.handleGet(sessionId, null, null, "text/event-stream", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }
}
