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

import com.callibrity.mocapi.server.McpServer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class McpStreamingControllerGetTest {

  private static final String SSE_ACCEPT = "text/event-stream";

  private McpStreamingController controller;
  private McpSessionManager sessionManager;

  @BeforeEach
  void setUp() {
    McpServer mcpServer = new McpServer(List.of(), null, null);
    sessionManager = new McpSessionManager();
    ObjectMapper objectMapper = new ObjectMapper();
    controller =
        new McpStreamingController(
            mcpServer,
            sessionManager,
            new SyncTaskExecutor(),
            objectMapper,
            List.of("localhost"),
            null);
  }

  @Test
  void shouldReturn400WhenSessionIdMissing() {
    var response = controller.handleGet(null, null, null, SSE_ACCEPT);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldReturn404WhenSessionNotFound() {
    var response = controller.handleGet("nonexistent-session", null, null, SSE_ACCEPT);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldReturn400ForInvalidProtocolVersion() {
    McpSession session = sessionManager.createSession();
    var response =
        controller.handleGet(session.getSessionId(), "invalid-version", null, SSE_ACCEPT);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void shouldReturnSseEmitterForValidSession() {
    McpSession session = sessionManager.createSession();
    var response = controller.handleGet(session.getSessionId(), null, null, SSE_ACCEPT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
  }

  @Test
  void shouldRegisterNotificationEmitter() {
    McpSession session = sessionManager.createSession();
    controller.handleGet(session.getSessionId(), null, null, SSE_ACCEPT);

    assertThat(session.getNotificationEmitterCount()).isEqualTo(1);
  }

  @Test
  void shouldAcceptValidProtocolVersion() {
    McpSession session = sessionManager.createSession();
    var response =
        controller.handleGet(session.getSessionId(), McpServer.PROTOCOL_VERSION, null, SSE_ACCEPT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
  }

  @Test
  void shouldHandleLastEventIdHeader() {
    McpSession session = sessionManager.createSession();
    var response =
        controller.handleGet(session.getSessionId(), null, "some-previous-event-id", SSE_ACCEPT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
  }
}
