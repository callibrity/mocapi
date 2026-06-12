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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.transport.http.writer.DirectMessageWriter;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StreamableHttpControllerTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";

  @Mock private McpServer server;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private StreamableHttpController controller;

  @BeforeEach
  void set_up() {
    controller =
        new StreamableHttpController(
            server,
            new McpRequestValidator(List.of("localhost")),
            new McpHeaderValidator(),
            objectMapper,
            ContextSnapshotFactory.builder().build());
  }

  // --- helpers ---

  private void serverSends(JsonRpcMessage... messages) {
    doAnswer(
            invocation -> {
              McpTransport transport = invocation.getArgument(1);
              for (JsonRpcMessage message : messages) {
                transport.send(message);
              }
              return null;
            })
        .when(server)
        .handleCall(any(JsonRpcCall.class), any(McpTransport.class));
  }

  private static JsonRpcResult okResult() {
    return new JsonRpcResult(
        JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));
  }

  private ObjectNode callBody(String method) {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    request.put("id", 1);
    ObjectNode params = request.putObject("params");
    ObjectNode meta = params.putObject("_meta");
    meta.put(McpMetaKeys.PROTOCOL_VERSION, McpServer.PROTOCOL_VERSION);
    meta.putObject(McpMetaKeys.CLIENT_INFO).put("name", "test-client").put("version", "1.0");
    meta.putObject(McpMetaKeys.CLIENT_CAPABILITIES);
    return request;
  }

  private ObjectNode toolsCallBody(String toolName) {
    ObjectNode request = callBody("tools/call");
    ((ObjectNode) request.get("params")).put("name", toolName);
    return request;
  }

  private static HttpHeaders validHeadersFor(String method) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.ACCEPT, POST_ACCEPT);
    headers.set(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER, McpServer.PROTOCOL_VERSION);
    headers.set(McpHeaderValidator.MCP_METHOD_HEADER, method);
    return headers;
  }

  private ResponseEntity<Object> post(ObjectNode body, HttpHeaders headers)
      throws InterruptedException, ExecutionException, TimeoutException {
    return controller
        .handlePost(objectMapper.treeToValue(body, JsonRpcMessage.class), headers)
        .get(5, TimeUnit.SECONDS);
  }

  private static JsonRpcError errorBody(ResponseEntity<Object> response) {
    assertThat(response.getBody()).isInstanceOf(JsonRpcError.class);
    return (JsonRpcError) response.getBody();
  }

  @Nested
  class Stateless_post_happy_path {

    @Test
    void tools_call_returns_json_when_handler_sends_only_response() throws Exception {
      serverSends(okResult());
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var response = post(toolsCallBody("echo"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertThat(response.getBody()).isInstanceOf(JsonRpcResult.class);
      verify(server).handleCall(any(JsonRpcCall.class), any(McpTransport.class));
    }

    @Test
    void tools_call_returns_sse_when_handler_sends_progress_notification_first() throws Exception {
      serverSends(new JsonRpcNotification("2.0", "notifications/progress", null), okResult());
      HttpHeaders headers = validHeadersFor("tools/call");
      headers.set(McpHeaderValidator.MCP_NAME_HEADER, "echo");

      var response = post(toolsCallBody("echo"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
      assertThat(response.getHeaders().getFirst(DirectMessageWriter.X_ACCEL_BUFFERING_HEADER))
          .isEqualTo("no");
      assertThat(response.getBody()).isInstanceOf(SseEmitter.class);
    }

    @Test
    void incoming_mcp_session_id_header_is_ignored_and_never_echoed() throws Exception {
      serverSends(okResult());
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set("Mcp-Session-Id", "stale-session-from-an-old-client");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getFirst("Mcp-Session-Id")).isNull();
    }

    @Test
    void last_event_id_header_is_ignored() throws Exception {
      // Resumability is removed from the spec; the header has no effect.
      serverSends(okResult());
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set("Last-Event-ID", "some-old-event-id");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void unrecognized_mcp_param_headers_are_ignored() throws Exception {
      // x-mcp-header custom parameter headers are not implemented (ADR-0022).
      serverSends(okResult());
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set("Mcp-Param-Anything", "whatever");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Nested
  class Header_validation {

    @Test
    void missing_mcp_method_header_returns_400_header_mismatch_echoing_the_call_id()
        throws Exception {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.remove(McpHeaderValidator.MCP_METHOD_HEADER);

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonRpcError error = errorBody(response);
      assertThat(error.error().code()).isEqualTo(McpHeaderValidator.HEADER_MISMATCH_CODE);
      assertThat(error.error().message()).startsWith("HeaderMismatch");
      assertThat(error.id().asInt()).isEqualTo(1);
      verify(server, never()).handleCall(any(), any());
    }

    @Test
    void mismatched_protocol_version_header_returns_400_header_mismatch() throws Exception {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code())
          .isEqualTo(McpHeaderValidator.HEADER_MISMATCH_CODE);
      verify(server, never()).handleCall(any(), any());
    }

    @Test
    void missing_mcp_name_header_on_tools_call_returns_400_header_mismatch() throws Exception {
      var response = post(toolsCallBody("echo"), validHeadersFor("tools/call"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code())
          .isEqualTo(McpHeaderValidator.HEADER_MISMATCH_CODE);
    }

    @Test
    void legacy_initialize_post_gets_a_clean_modern_error() throws Exception {
      // A 2025-era client POSTs initialize with no Mcp-Method routing header.
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "initialize");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("protocolVersion", "2025-11-25");
      params.putObject("capabilities");
      params.putObject("clientInfo").put("name", "legacy").put("version", "1.0");
      HttpHeaders headers = new HttpHeaders();
      headers.set(HttpHeaders.ACCEPT, POST_ACCEPT);
      headers.set(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25");

      var response = post(request, headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code())
          .isEqualTo(McpHeaderValidator.HEADER_MISMATCH_CODE);
      verify(server, never()).handleCall(any(), any());
    }
  }

  @Nested
  class Error_status_mapping {

    @Test
    void unknown_rpc_method_maps_to_404() throws Exception {
      serverSends(
          new JsonRpcError(-32601, "Method not found", JsonNodeFactory.instance.numberNode(1)));

      var response = post(callBody("no/such/method"), validHeadersFor("no/such/method"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(errorBody(response).error().code()).isEqualTo(-32601);
    }

    @Test
    void unsupported_protocol_version_maps_to_400() throws Exception {
      serverSends(
          new JsonRpcError(
              -32004, "Unsupported protocol version", JsonNodeFactory.instance.numberNode(1)));
      ObjectNode body = callBody("tools/list");
      ((ObjectNode) body.get("params").get("_meta"))
          .put(McpMetaKeys.PROTOCOL_VERSION, "1999-01-01");
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(McpHeaderValidator.MCP_PROTOCOL_VERSION_HEADER, "1999-01-01");

      var response = post(body, headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code()).isEqualTo(-32004);
    }

    @Test
    void invalid_params_maps_to_400() throws Exception {
      // e.g. the server rejecting a missing _meta envelope.
      serverSends(
          new JsonRpcError(
              -32602, "Missing required _meta envelope", JsonNodeFactory.instance.numberNode(1)));
      ObjectNode body = objectMapper.createObjectNode();
      body.put("jsonrpc", "2.0");
      body.put("method", "tools/list");
      body.put("id", 1);

      var response = post(body, validHeadersFor("tools/list"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code()).isEqualTo(-32602);
    }
  }

  @Nested
  class Notifications {

    @Test
    void accepted_notification_returns_202_with_no_body() throws Exception {
      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/cancelled");

      var response = post(notification, validHeadersFor("notifications/cancelled"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
      assertThat(response.getBody()).isNull();
      verify(server).handleNotification(any(JsonRpcNotification.class));
    }

    @Test
    void notification_with_header_mismatch_returns_400_and_is_not_dispatched() throws Exception {
      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", "2.0");
      notification.put("method", "notifications/cancelled");

      var response = post(notification, validHeadersFor("notifications/progress"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code())
          .isEqualTo(McpHeaderValidator.HEADER_MISMATCH_CODE);
      verify(server, never()).handleNotification(any());
    }
  }

  @Nested
  class Method_not_allowed {

    @Test
    void get_and_delete_return_405_allowing_only_post() {
      var response = controller.handleNonPost();

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
      assertThat(response.getHeaders().getFirst(HttpHeaders.ALLOW)).isEqualTo("POST");
      assertThat(response.getBody()).isNull();
    }
  }

  @Nested
  class Post_validation {

    @Test
    void rejects_accept_header_without_both_media_types() throws Exception {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(HttpHeaders.ACCEPT, "application/json");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void rejects_missing_accept_header() throws Exception {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.remove(HttpHeaders.ACCEPT);

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void rejects_invalid_origin() throws Exception {
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(HttpHeaders.ORIGIN, "http://evil.example.com");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void accepts_allowed_origin() throws Exception {
      serverSends(okResult());
      HttpHeaders headers = validHeadersFor("tools/list");
      headers.set(HttpHeaders.ORIGIN, "http://localhost:8080");

      var response = post(callBody("tools/list"), headers);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void json_rpc_response_body_returns_400_invalid_request() throws Exception {
      // No server-initiated requests in 2026-07-28 — clients have nothing to respond to.
      ObjectNode body = objectMapper.createObjectNode();
      body.put("jsonrpc", "2.0");
      body.put("id", 1);
      body.putObject("result");

      var response = post(body, validHeadersFor("tools/list"));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().code()).isEqualTo(-32600);
    }
  }

  @Nested
  class Handler_failures {

    @Test
    void handler_exception_completes_future_exceptionally() throws Exception {
      doThrow(new RuntimeException("handler blew up")).when(server).handleCall(any(), any());

      CompletableFuture<ResponseEntity<Object>> future =
          controller.handlePost(
              objectMapper.treeToValue(callBody("tools/list"), JsonRpcMessage.class),
              validHeadersFor("tools/list"));

      assertThatThrownBy(() -> future.get(5, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(RuntimeException.class)
          .hasMessageContaining("handler blew up");
    }
  }

  @Nested
  class Exception_handling {

    @Test
    void handle_unreadable_body_returns_400_with_parse_error() {
      var cause = new RuntimeException("Unexpected token");
      var ex = new HttpMessageNotReadableException("Could not read JSON", cause, null);

      var response = controller.handleUnreadableBody(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      JsonRpcError error = errorBody(response);
      assertThat(error.error().code()).isEqualTo(-32700);
      assertThat(error.error().message()).contains("Parse error").contains("Unexpected token");
    }

    @Test
    void handle_unreadable_body_handles_self_referential_cause_chain() {
      var self =
          new RuntimeException("self-cycle") {
            @Override
            public synchronized Throwable getCause() {
              return this;
            }
          };
      var ex = new HttpMessageNotReadableException("wrapper", self, null);

      var response = controller.handleUnreadableBody(ex);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertThat(errorBody(response).error().message()).contains("self-cycle");
    }

    @Test
    void handle_unreadable_body_uses_root_cause_message() {
      var root = new RuntimeException("root cause");
      var mid = new RuntimeException("mid", root);
      var ex = new HttpMessageNotReadableException("wrapper", mid, null);

      var response = controller.handleUnreadableBody(ex);

      assertThat(errorBody(response).error().message()).contains("root cause");
    }
  }
}
