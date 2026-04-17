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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult;
import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.DeliveredEvent;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberConfig;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
  @Mock private OdysseyStream<JsonRpcMessage> stream;
  @Mock private SubscriberConfig<JsonRpcMessage> subscriberConfig;
  @Captor private ArgumentCaptor<SseEventMapper<JsonRpcMessage>> sseEventMapperCaptor;

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
    void initializeReturnsJsonWithSessionHeader() throws Exception {
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
    void initializeDelegatesHandleCallToProtocol() throws Exception {
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
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

      verify(protocol).handleCall(any(), any(), any(LazyHttpTransport.class));
    }
  }

  @Nested
  class PostWithSession {

    @BeforeEach
    void setUpSession() {
      when(protocol.createContext(anyString(), any())).thenReturn(validContext("session-1"));
    }

    @Test
    void toolsCallReturnsSseWhenHandlerSendsNotificationFirst() throws Exception {
      SseEmitter emitter = new SseEmitter();
      when(odyssey.stream(anyString(), eq(JsonRpcMessage.class))).thenReturn(stream);
      when(stream.subscribe(any())).thenReturn(emitter);
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
                transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
                transport.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(protocol)
          .handleCall(any(), any(), any());

      ObjectNode request = callRequest("tools/call");
      var response = post(request, null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
      assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void toolsCallReturnsJsonWhenHandlerSendsOnlyResponse() throws Exception {
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
                transport.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(protocol)
          .handleCall(any(), any(), any());

      var response = post(callRequest("tools/call"), null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void nonToolsCallReturnsJson() throws Exception {
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
                transport.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(protocol)
          .handleCall(any(), any(), any());

      var response = post(callRequest("ping"), null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      verify(protocol).handleCall(any(), any(), any(LazyHttpTransport.class));
    }

    @Test
    void notificationWithSessionReturns202() throws Exception {
      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/initialized");

      var response = post(notification, null, "session-1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      verify(protocol).handleNotification(any(), any());
    }

    @Test
    void responseWithSessionReturns202() throws Exception {
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
    void rejectsInvalidAcceptHeader() throws Exception {
      var response = post(callRequest("ping"), null, "s", "application/json", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void rejectsInvalidOrigin() throws Exception {
      var response = post(callRequest("ping"), null, "s", POST_ACCEPT, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void notificationWithoutSessionReturns400() throws Exception {
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
    void responseWithoutSessionReturns400() throws Exception {
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

    @Test
    void postWithSessionNotFoundReturns404() throws Exception {
      when(protocol.createContext(any(), any()))
          .thenReturn(new McpContextResult.SessionNotFound(-32001, "Session not found"));

      var response = post(callRequest("ping"), null, "unknown", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void postWithProtocolVersionMismatchReturns400() throws Exception {
      when(protocol.createContext(any(), any()))
          .thenReturn(
              new McpContextResult.ProtocolVersionMismatch(-32002, "Protocol version mismatch"));

      var response = post(callRequest("ping"), "wrong-version", "s1", POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  class GetEndpoint {

    @Test
    void subscribesToNotificationChannel() {
      when(protocol.createContext("session-1", null)).thenReturn(validContext("session-1"));
      SseEmitter emitter = new SseEmitter();
      when(odyssey.stream("session-1", JsonRpcMessage.class)).thenReturn(stream);
      when(stream.subscribe(any())).thenReturn(emitter);

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
      when(protocol.createContext("session-1", null)).thenReturn(validContext("session-1"));
      SseEmitter emitter = new SseEmitter();
      when(odyssey.stream("stream-name", JsonRpcMessage.class)).thenReturn(stream);
      when(stream.resume(eq("event-42"), any())).thenReturn(emitter);

      String plaintext = "stream-name:event-42";
      String encryptedId = encrypt("session-1", plaintext);

      var response = controller.handleGet("session-1", null, encryptedId, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isSameAs(emitter);
      verify(odyssey).stream("stream-name", JsonRpcMessage.class);
      verify(stream).resume(eq("event-42"), any());
    }

    @Test
    void primingEventIdTriggersSubscribeNotResume() {
      when(protocol.createContext("session-1", null)).thenReturn(validContext("session-1"));
      SseEmitter emitter = new SseEmitter();
      when(odyssey.stream("stream-name", JsonRpcMessage.class)).thenReturn(stream);
      when(stream.subscribe(any())).thenReturn(emitter);

      String plaintext = "stream-name:PRIMING";
      String encryptedId = encrypt("session-1", plaintext);

      var response = controller.handleGet("session-1", null, encryptedId, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isSameAs(emitter);
      verify(stream).subscribe(any());
      verify(stream, never()).resume(anyString(), any());
    }

    @Test
    void lastEventIdWithNoColonReturns400() {
      when(protocol.createContext("session-1", null)).thenReturn(validContext("session-1"));

      String plaintext = "no-colon-in-this-value";
      String encryptedId = encrypt("session-1", plaintext);

      var response = controller.handleGet("session-1", null, encryptedId, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedBase64InLastEventIdReturns400() {
      when(protocol.createContext("session-1", null)).thenReturn(validContext("session-1"));

      var response =
          controller.handleGet("session-1", null, "not!!!valid~base64", SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getWithSessionNotFoundReturns404() {
      when(protocol.createContext("unknown", null))
          .thenReturn(new McpContextResult.SessionNotFound(-32001, "Session not found"));

      var response = controller.handleGet("unknown", null, null, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getWithProtocolVersionMismatchReturns400() {
      when(protocol.createContext("session-1", "wrong-version"))
          .thenReturn(
              new McpContextResult.ProtocolVersionMismatch(-32002, "Protocol version mismatch"));

      var response = controller.handleGet("session-1", "wrong-version", null, SSE_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  @Nested
  class DeleteEndpoint {

    @Test
    void delegatesToProtocolTerminate() {
      when(protocol.createContext("session-1", null)).thenReturn(validContext("session-1"));
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

  @Nested
  class SubscriptionWiring {

    @BeforeEach
    void setUpSession() {
      when(protocol.createContext(anyString(), any())).thenReturn(validContext("session-1"));
    }

    @Test
    void primingHookSendsEncryptedPrimingEventBeforeJournalEvents() throws Exception {
      stubSubscribeApplyingCustomizerTo(subscriberConfig);
      stubToolCallSendingNotificationThenResponse();

      post(callRequest("tools/call"), null, "session-1", POST_ACCEPT, null);

      ArgumentCaptor<SubscriberConfig.SubscribeHook> hookCaptor =
          ArgumentCaptor.forClass(SubscriberConfig.SubscribeHook.class);
      verify(subscriberConfig).onSubscribe(hookCaptor.capture());

      SseEmitter emitter = mock(SseEmitter.class);
      hookCaptor.getValue().accept(emitter);
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void encryptingMapperBuildsSseEventFromDeliveredEvent() throws Exception {
      stubSubscribeApplyingCustomizerTo(subscriberConfig);
      stubToolCallSendingNotificationThenResponse();

      post(callRequest("tools/call"), null, "session-1", POST_ACCEPT, null);

      verify(subscriberConfig).mapper(sseEventMapperCaptor.capture());
      SseEventMapper<JsonRpcMessage> mapper = sseEventMapperCaptor.getValue();

      JsonRpcMessage payload =
          new JsonRpcResult(
              JsonNodeFactory.instance.objectNode().put("k", "v"),
              JsonNodeFactory.instance.numberNode(1));
      DeliveredEvent<JsonRpcMessage> withType =
          new DeliveredEvent<>(
              "evt-1", "stream-xyz", Instant.now(), "progress", payload, java.util.Map.of());
      SseEmitter.SseEventBuilder builder = mapper.map(withType);
      assertThat(builder).isNotNull();

      DeliveredEvent<JsonRpcMessage> withoutType =
          new DeliveredEvent<>(
              "evt-2", "stream-xyz", Instant.now(), null, payload, java.util.Map.of());
      assertThat(mapper.map(withoutType)).isNotNull();
    }

    private void stubSubscribeApplyingCustomizerTo(SubscriberConfig<JsonRpcMessage> config) {
      when(config.mapper(any())).thenReturn(config);
      when(config.onSubscribe(any())).thenReturn(config);
      when(odyssey.stream(anyString(), eq(JsonRpcMessage.class))).thenReturn(stream);
      when(stream.subscribe(any()))
          .thenAnswer(
              inv -> {
                SubscriberCustomizer<JsonRpcMessage> customizer = inv.getArgument(0);
                customizer.accept(config);
                return new SseEmitter();
              });
    }

    private void stubToolCallSendingNotificationThenResponse() {
      doAnswer(
              invocation -> {
                McpTransport transport = invocation.getArgument(2);
                transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
                transport.send(
                    new JsonRpcResult(
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.numberNode(1)));
                return null;
              })
          .when(protocol)
          .handleCall(any(), any(), any());
    }
  }

  @Nested
  class ExceptionHandling {

    @Test
    void handleUnreadableBodyReturns400WithParseError() {
      var cause = new RuntimeException("Unexpected token");
      var ex = new HttpMessageNotReadableException("Could not read JSON", cause, null);

      var response = controller.handleUnreadableBody(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(response.getBody()).isNotNull();
      assertThat(response.getBody().toString()).contains("Parse error");
      assertThat(response.getBody().toString()).contains("Unexpected token");
    }

    @Test
    void handleUnreadableBodyUsesRootCauseMessage() {
      var root = new RuntimeException("root cause");
      var mid = new RuntimeException("mid", root);
      var ex = new HttpMessageNotReadableException("wrapper", mid, null);

      var response = controller.handleUnreadableBody(ex);

      assertThat(response.getBody().toString()).contains("root cause");
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
      ObjectNode body, String protocolVersion, String sessionId, String accept, String origin)
      throws InterruptedException, ExecutionException, TimeoutException {
    CompletableFuture<ResponseEntity<Object>> future =
        controller.handlePost(
            objectMapper.treeToValue(body, JsonRpcMessage.class),
            protocolVersion,
            sessionId,
            accept,
            origin);
    return future.get(5, TimeUnit.SECONDS);
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
