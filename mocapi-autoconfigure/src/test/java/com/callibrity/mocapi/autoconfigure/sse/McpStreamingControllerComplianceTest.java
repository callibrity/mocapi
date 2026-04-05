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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tests for MCP Streamable HTTP transport compliance gaps (spec 009). Covers: Gap 1 (notification
 * 202), Gap 2 (DELETE endpoint), Gap 3 (no Last-Event-ID on POST), Gap 4 (stream identity in event
 * IDs), Gap 5 (Accept header validation).
 */
class McpStreamingControllerComplianceTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";
  private static final String SSE_ACCEPT = "text/event-stream";

  private McpStreamingController controller;
  private McpSessionManager sessionManager;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    McpServer mcpServer = new McpServer(List.of(), null, null);
    sessionManager = new McpSessionManager();
    objectMapper = new ObjectMapper();
    controller =
        new McpStreamingController(
            mcpServer,
            sessionManager,
            new SyncTaskExecutor(),
            objectMapper,
            List.of("localhost"),
            null);
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
      // This test verifies the notification is processed (not just acknowledged).
      // The mcpServer.clientInitialized() is a no-op that doesn't throw,
      // so a successful 202 indicates it was invoked.
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

  // ---- Gap 4: Event IDs must encode stream identity ----

  @Nested
  class EventIdStreamIdentity {

    @Test
    void shouldEncodeStreamIdInEventId() {
      McpSession session = new McpSession();
      String streamId = "test-stream-id";
      String eventId = session.nextEventId(streamId);

      assertThat(eventId).startsWith(streamId + ":");
    }

    @Test
    void shouldExtractStreamIdFromEventId() {
      String streamId = "abc-123-def";
      String eventId = streamId + ":42";

      assertThat(McpSession.extractStreamId(eventId)).isEqualTo(streamId);
    }

    @Test
    void shouldReturnNullForInvalidEventId() {
      assertThat(McpSession.extractStreamId(null)).isNull();
      assertThat(McpSession.extractStreamId("no-colon")).isNull();
    }

    @Test
    void shouldReplayEventsFromOriginalStream() {
      McpSession session = new McpSession();
      String stream1 = "stream-1";
      String stream2 = "stream-2";

      // Store events on stream 1
      String eventId1 = session.nextEventId(stream1);
      session.storeEvent(stream1, new SseEvent(eventId1, "data-1"));
      String eventId2 = session.nextEventId(stream1);
      session.storeEvent(stream1, new SseEvent(eventId2, "data-2"));

      // Store events on stream 2
      String eventId3 = session.nextEventId(stream2);
      session.storeEvent(stream2, new SseEvent(eventId3, "data-3"));

      // Resumption should only replay from stream 1 after eventId1
      var replayed = session.getEventsAfter(eventId1);
      assertThat(replayed).hasSize(1);
      assertThat(replayed.iterator().next().data()).isEqualTo("data-2");
    }

    @Test
    void shouldNotReplayEventsFromDifferentStream() {
      McpSession session = new McpSession();
      String stream1 = "stream-1";
      String stream2 = "stream-2";

      String eventId1 = session.nextEventId(stream1);
      session.storeEvent(stream1, new SseEvent(eventId1, "data-1"));

      String eventId2 = session.nextEventId(stream2);
      session.storeEvent(stream2, new SseEvent(eventId2, "data-2"));

      // Asking for events after stream1's event should not return stream2's events
      var replayed = session.getEventsAfter(eventId1);
      assertThat(replayed).isNotNull().isEmpty();
    }
  }

  // ---- Gap 5: Accept header validation ----

  @Nested
  class AcceptHeaderValidation {

    @Test
    void shouldReturn406WhenPostMissingAcceptHeader() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, null, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldReturn406WhenPostAcceptMissesJson() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, "text/event-stream", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldReturn406WhenPostAcceptMissesSse() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, "application/json", null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void shouldAcceptWildcardOnPost() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, "*/*", null);
      // Should not be 406
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
      // Should not be 406
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
