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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult;
import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class StreamableHttpControllerTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";
  private static final String SSE_ACCEPT = "text/event-stream";

  @Mock private McpServer protocol;
  @Mock private Odyssey odyssey;
  @Mock private OdysseyPublisher<JsonRpcMessage> publisher;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private StreamableHttpController controller;
  private byte[] masterKey;

  @BeforeEach
  void setUp() {
    masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));
    controller =
        new StreamableHttpController(protocol, validator, odyssey, objectMapper, masterKey);
  }

  @Nested
  class PostInitialize {

    @Test
    void initializeReturnsJsonWithSessionHeader() {
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
                transport.emit(new McpEvent.SessionInitialized("new-session", "2025-11-25"));
                transport.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode().put("protocolVersion", "2025-11-25"),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(protocol)
          .handleCall(any(McpContext.class), any(), any(McpTransport.class));

      ObjectNode request = initializeRequest();
      var response = post(request, null, null, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getFirst("MCP-Session-Id")).isEqualTo("new-session");
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertThat(response.getBody()).isInstanceOf(JsonRpcResult.class);
    }

    @Test
    void initializeDelegatesToProtocolWithSynchronousTransport() {
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
                assertThat(transport).isInstanceOf(SynchronousTransport.class);
                transport.emit(new McpEvent.SessionInitialized("s1", "2025-11-25"));
                transport.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(protocol)
          .handleCall(any(), any(), any());

      post(initializeRequest(), null, null, POST_ACCEPT, null);

      verify(protocol).handleCall(any(), any(), any(SynchronousTransport.class));
    }
  }

  @Nested
  class PostWithSession {

    @BeforeEach
    void setUpSession() {
      when(protocol.createContext(anyString(), any())).thenReturn(validContext("session-1"));
    }

    @Test
    void callWithSessionReturnsSse() {
      SseEmitter emitter = new SseEmitter();
      when(odyssey.publisher(anyString(), eq(JsonRpcMessage.class))).thenReturn(publisher);
      when(odyssey.subscribe(anyString(), eq(JsonRpcMessage.class), any())).thenReturn(emitter);

      ObjectNode request = callRequest("tools/list");
      var response = post(request, null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void callWithSessionUsesOdysseyTransport() throws Exception {
      CountDownLatch latch = new CountDownLatch(1);
      when(odyssey.publisher(anyString(), eq(JsonRpcMessage.class))).thenReturn(publisher);
      when(odyssey.subscribe(anyString(), eq(JsonRpcMessage.class), any()))
          .thenReturn(new SseEmitter());
      doAnswer(
              invocation -> {
                latch.countDown();
                return null;
              })
          .when(protocol)
          .handleCall(any(), any(), any());

      post(callRequest("ping"), null, "session-1", POST_ACCEPT, null);

      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      verify(protocol).handleCall(any(), any(), any(OdysseyTransport.class));
    }

    @Test
    void notificationWithSessionReturns202() {
      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/initialized");

      var response = post(notification, null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      verify(protocol).handleNotification(any(), any());
    }

    @Test
    void responseWithSessionReturns202() {
      ObjectNode jsonRpcResponse = objectMapper.createObjectNode();
      jsonRpcResponse.put("jsonrpc", "2.0");
      jsonRpcResponse.put("id", 1);
      jsonRpcResponse.putObject("result");

      var response = post(jsonRpcResponse, null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      verify(protocol).handleResponse(any(), any());
    }
  }

  @Nested
  class PostValidation {

    @Test
    void rejectsInvalidAcceptHeader() {
      var response = post(callRequest("ping"), null, "s", "application/json", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void rejectsInvalidOrigin() {
      var response = post(callRequest("ping"), null, "s", POST_ACCEPT, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void notificationWithoutSessionReturns400() {
      when(protocol.createContext(any(), any()))
          .thenReturn(
              new McpContextResult.SessionIdRequired(
                  -32000, "Bad Request: MCP-Session-Id header is required"));
      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/initialized");

      var response = post(notification, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void responseWithoutSessionReturns400() {
      when(protocol.createContext(any(), any()))
          .thenReturn(
              new McpContextResult.SessionIdRequired(
                  -32000, "Bad Request: MCP-Session-Id header is required"));
      ObjectNode jsonRpcResponse = objectMapper.createObjectNode();
      jsonRpcResponse.put("jsonrpc", "2.0");
      jsonRpcResponse.put("id", 1);
      jsonRpcResponse.putObject("result");

      var response = post(jsonRpcResponse, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  class GetEndpoint {

    @Test
    void subscribesToNotificationChannel() {
      when(protocol.createContext(eq("session-1"), any())).thenReturn(validContext("session-1"));
      SseEmitter emitter = new SseEmitter();
      when(odyssey.subscribe(eq("session-1"), eq(JsonRpcMessage.class), any())).thenReturn(emitter);

      var response = controller.handleGet("session-1", null, null, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void rejectsInvalidAcceptHeader() {
      var response = controller.handleGet("s", null, null, "application/json", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void rejectsInvalidOrigin() {
      var response = controller.handleGet("s", null, null, SSE_ACCEPT, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void resumeWithLastEventIdDelegatesToOdyssey() {
      when(protocol.createContext(eq("session-1"), any())).thenReturn(validContext("session-1"));
      SseEmitter emitter = new SseEmitter();
      when(odyssey.resume(anyString(), eq(JsonRpcMessage.class), anyString(), any()))
          .thenReturn(emitter);

      String plaintext = "stream-name:event-42";
      String encryptedId = encrypt("session-1", plaintext);

      var response = controller.handleGet("session-1", null, encryptedId, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isSameAs(emitter);

      ArgumentCaptor<String> streamCaptor = ArgumentCaptor.forClass(String.class);
      ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
      verify(odyssey)
          .resume(streamCaptor.capture(), eq(JsonRpcMessage.class), eventIdCaptor.capture(), any());
      assertThat(streamCaptor.getValue()).isEqualTo("stream-name");
      assertThat(eventIdCaptor.getValue()).isEqualTo("event-42");
    }
  }

  @Nested
  class DeleteEndpoint {

    @Test
    void delegatesToProtocolTerminate() {
      when(protocol.createContext(eq("session-1"), any())).thenReturn(validContext("session-1"));
      var response = controller.handleDelete("session-1", null, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
      verify(protocol).terminate(any(McpContext.class));
    }

    @Test
    void rejectsInvalidOrigin() {
      var response = controller.handleDelete("s", null, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  // --- helpers ---

  private static McpContextResult validContext(String sessionId) {
    return new McpContextResult.ValidContext(new StubMcpContext(sessionId));
  }

  private record StubMcpContext(String sessionId) implements McpContext {
    @Override
    public String protocolVersion() {
      return "2025-11-25";
    }

    @Override
    public Optional<com.callibrity.mocapi.server.session.McpSession> session() {
      return Optional.empty();
    }
  }

  private ResponseEntity<Object> post(
      ObjectNode body, String protocolVersion, String sessionId, String accept, String origin) {
    return controller.handlePost(
        objectMapper.treeToValue(body, JsonRpcMessage.class),
        protocolVersion,
        sessionId,
        accept,
        origin);
  }

  private ObjectNode initializeRequest() {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", "initialize");
    request.put("id", 1);
    ObjectNode params = request.putObject("params");
    params.put("protocolVersion", "2025-11-25");
    params.putObject("capabilities");
    ObjectNode clientInfo = params.putObject("clientInfo");
    clientInfo.put("name", "test-client");
    clientInfo.put("version", "1.0");
    return request;
  }

  private ObjectNode callRequest(String method) {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    request.put("id", 1);
    return request;
  }

  private String encrypt(String sessionId, String plaintext) {
    try {
      byte[] encrypted =
          Ciphers.encryptAesGcm(
              masterKey, sessionId, plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(encrypted);
    } catch (java.security.GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }
}
