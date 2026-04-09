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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.InitializeResponse;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.InMemoryMcpSessionStore;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcDispatcher;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.odyssey.core.StreamSubscriberBuilder;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class StreamableHttpControllerGetTest {

  private static final String SSE_ACCEPT = "text/event-stream";
  private static final Duration SESSION_TIMEOUT = Duration.ofHours(1);

  private StreamableHttpController controller;
  private InMemoryMcpSessionStore sessionStore;
  private McpSessionService sessionService;
  private OdysseyStreamRegistry registry;
  private OdysseyStream notificationStream;

  @BeforeEach
  void setUp() {
    registry = mock(OdysseyStreamRegistry.class);

    notificationStream = mock(OdysseyStream.class);
    StreamSubscriberBuilder subscriberBuilder = mock(StreamSubscriberBuilder.class);
    when(subscriberBuilder.mapper(any())).thenReturn(subscriberBuilder);
    when(subscriberBuilder.subscribe()).thenReturn(new SseEmitter());
    when(subscriberBuilder.resumeAfter(anyString())).thenReturn(new SseEmitter());
    when(notificationStream.subscriber()).thenReturn(subscriberBuilder);
    when(registry.channel(anyString())).thenReturn(notificationStream);

    OdysseyStream ephemeralStream = mock(OdysseyStream.class);
    when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.ephemeral()).thenReturn(ephemeralStream);

    sessionStore = new InMemoryMcpSessionStore();
    ObjectMapper objectMapper = new ObjectMapper();

    byte[] masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    sessionService = new McpSessionService(sessionStore, masterKey, SESSION_TIMEOUT, registry);

    McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));
    JsonRpcDispatcher dispatcher = new DefaultJsonRpcDispatcher(List.of());
    MailboxFactory mailboxFactory = mock(MailboxFactory.class);

    controller =
        new StreamableHttpController(
            dispatcher, validator, sessionService, objectMapper, mailboxFactory);
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

  @Test
  void shouldReturn400WhenSessionIdMissing() {
    var response = controller.handleGet(null, null, null, SSE_ACCEPT, null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldReturn404WhenSessionNotFound() {
    var response = controller.handleGet("nonexistent-session", null, null, SSE_ACCEPT, null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldReturn400ForInvalidProtocolVersion() {
    String sessionId = createSession();
    var response = controller.handleGet(sessionId, "invalid-version", null, SSE_ACCEPT, null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldReturnSseEmitterForValidSession() {
    String sessionId = createSession();
    var response = controller.handleGet(sessionId, null, null, SSE_ACCEPT, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
  }

  @Test
  void shouldReturnSseEmitterWhenSubscribing() {
    String sessionId = createSession();
    var response = controller.handleGet(sessionId, null, null, SSE_ACCEPT, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
  }

  @Test
  void shouldReturn400ForTamperedLastEventId() {
    String sessionId = createSession();
    var response = controller.handleGet(sessionId, null, "tampered-id", SSE_ACCEPT, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldReconnectWithValidEncryptedLastEventId() {
    String sessionId = createSession();
    String streamKey = "odyssey:channel:" + sessionId;
    String rawEventId = "raw-event-123";
    OdysseyStream stream = mock(OdysseyStream.class);
    StreamSubscriberBuilder builder = mock(StreamSubscriberBuilder.class);
    when(builder.mapper(any())).thenReturn(builder);
    when(builder.resumeAfter(rawEventId)).thenReturn(new SseEmitter());
    when(stream.subscriber()).thenReturn(builder);
    when(registry.stream(streamKey)).thenReturn(stream);

    String encryptedId = sessionService.encrypt(sessionId, streamKey + ":" + rawEventId);
    var response = controller.handleGet(sessionId, null, encryptedId, SSE_ACCEPT, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
    verify(builder).resumeAfter(rawEventId);
  }

  @Test
  void shouldAcceptValidProtocolVersion() {
    String sessionId = createSession();
    var response =
        controller.handleGet(
            sessionId, InitializeResponse.PROTOCOL_VERSION, null, SSE_ACCEPT, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
  }

  @Test
  void shouldReturn403ForInvalidOrigin() {
    String sessionId = createSession();
    var response =
        controller.handleGet(sessionId, null, null, SSE_ACCEPT, "http://evil.example.com");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void shouldAcceptValidOrigin() {
    String sessionId = createSession();
    var response = controller.handleGet(sessionId, null, null, SSE_ACCEPT, "http://localhost:8080");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldAcceptNullOrigin() {
    String sessionId = createSession();
    var response = controller.handleGet(sessionId, null, null, SSE_ACCEPT, null);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
  }
}
