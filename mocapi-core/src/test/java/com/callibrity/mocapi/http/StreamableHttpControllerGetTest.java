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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.InitializeResponse;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.InMemoryMcpSessionStore;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionIdParamResolver;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.stream.McpStreamContextParamResolver;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcDispatcher;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
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
    when(notificationStream.subscribe()).thenReturn(new SseEmitter());
    when(notificationStream.resumeAfter(anyString())).thenReturn(new SseEmitter());
    when(registry.channel(anyString())).thenReturn(notificationStream);

    OdysseyStream ephemeralStream = mock(OdysseyStream.class);
    when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.ephemeral()).thenReturn(ephemeralStream);

    sessionStore = new InMemoryMcpSessionStore();
    ObjectMapper objectMapper = new ObjectMapper();

    byte[] masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    sessionService = new McpSessionService(sessionStore, masterKey, SESSION_TIMEOUT);

    McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));
    JsonRpcDispatcher dispatcher = new DefaultJsonRpcDispatcher(List.of());
    McpStreamContextParamResolver streamContextResolver = new McpStreamContextParamResolver();
    McpSessionIdParamResolver sessionIdResolver = new McpSessionIdParamResolver();

    MailboxFactory mailboxFactory = mock(MailboxFactory.class);
    SchemaGenerator schemaGenerator =
        new SchemaGenerator(
            new SchemaGeneratorConfigBuilder(
                    objectMapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .build());

    controller =
        new StreamableHttpController(
            dispatcher,
            validator,
            sessionService,
            registry,
            objectMapper,
            streamContextResolver,
            sessionIdResolver,
            mailboxFactory,
            schemaGenerator,
            Duration.ofMinutes(5));
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
  void shouldPublishPrimingEventAndSubscribeWithoutLastEventId() {
    String sessionId = createSession();
    controller.handleGet(sessionId, null, null, SSE_ACCEPT, null);

    verify(notificationStream).publishJson(Map.of());
    verify(notificationStream).subscribe();
  }

  @Test
  void shouldResumeAfterLastEventId() {
    String sessionId = createSession();
    String lastEventId = "some-event-id";
    controller.handleGet(sessionId, null, lastEventId, SSE_ACCEPT, null);

    verify(notificationStream).resumeAfter(lastEventId);
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
