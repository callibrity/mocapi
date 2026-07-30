/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.transport.http.sse.PerRequestSseStream;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * The MCP 2026-07-28 Streamable HTTP endpoint: POST-only, stateless, one response stream per
 * request (ADR-0019, ADR-0020).
 *
 * <ul>
 *   <li>{@code GET} and {@code DELETE} → {@code 405 Method Not Allowed}: there is no standalone SSE
 *       stream and no session to terminate.
 *   <li>An incoming {@code Mcp-Session-Id} header is ignored — never minted, never echoed. {@code
 *       Last-Event-ID} is ignored — the spec removed SSE resumability.
 *   <li>Routing headers ({@code MCP-Protocol-Version}, {@code Mcp-Method}, {@code Mcp-Name}) are
 *       validated against the body before dispatch ({@link McpHeaderValidator}); failures are
 *       {@code 400} + JSON-RPC {@code -32020 HeaderMismatch}. Body envelope semantics ({@code
 *       -32602}/{@code -32022}) remain the server's job.
 *   <li>Unrecognized {@code Mcp-Param-*} headers are ignored — mocapi designates no custom
 *       parameter headers (ADR-0022).
 *   <li>Direct JSON replies carry an HTTP status mapped from the JSON-RPC error code ({@link
 *       HttpStatusMapping}); notably unknown method ({@code -32601}) → {@code 404}.
 * </ul>
 */
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
public class StreamableHttpController {

  public static final String INVALID_ORIGIN_MESSAGE = "Forbidden: Invalid Origin";

  private final McpServer server;
  private final McpRequestValidator validator;
  private final McpHeaderValidator headerValidator;
  private final ObjectMapper objectMapper;
  private final ContextSnapshotFactory contextSnapshotFactory;
  private final long streamTimeoutMillis;

  public StreamableHttpController(
      McpServer server,
      McpRequestValidator validator,
      McpHeaderValidator headerValidator,
      ObjectMapper objectMapper,
      ContextSnapshotFactory contextSnapshotFactory,
      long streamTimeoutMillis) {
    this.server = server;
    this.validator = validator;
    this.headerValidator = headerValidator;
    this.objectMapper = objectMapper;
    this.contextSnapshotFactory = contextSnapshotFactory;
    this.streamTimeoutMillis = streamTimeoutMillis;
  }

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public CompletableFuture<ResponseEntity<Object>> handlePost(
      @RequestBody JsonRpcMessage message, @RequestHeader HttpHeaders headers) {
    if (!acceptsJsonAndSse(headers.getFirst(HttpHeaders.ACCEPT))) {
      return CompletableFuture.completedFuture(
          jsonRpcError(
              HttpStatus.NOT_ACCEPTABLE,
              -32000,
              "Not Acceptable: Client must accept both application/json and text/event-stream",
              null));
    }
    if (!validator.isValidOrigin(headers.getFirst(HttpHeaders.ORIGIN))) {
      return CompletableFuture.completedFuture(
          jsonRpcError(HttpStatus.FORBIDDEN, -32000, INVALID_ORIGIN_MESSAGE, null));
    }

    return switch (message) {
      case JsonRpcCall call -> handleCall(headers, call);
      case JsonRpcNotification notification -> handleNotification(headers, notification);
      case JsonRpcResponse _ ->
          CompletableFuture.completedFuture(
              jsonRpcError(
                  HttpStatus.BAD_REQUEST,
                  JsonRpcProtocol.INVALID_REQUEST,
                  "Invalid Request: MCP 2026-07-28 has no server-initiated requests, so clients"
                      + " have no responses to deliver",
                  null));
    };
  }

  /**
   * The spec allows only POST on the MCP endpoint: GET (the old standalone SSE stream) and DELETE
   * (the old session termination) are gone with sessions and resumability.
   */
  @RequestMapping(method = {RequestMethod.GET, RequestMethod.DELETE})
  public ResponseEntity<Object> handleNonPost() {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .header(HttpHeaders.ALLOW, "POST")
        .build();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Object> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return jsonRpcError(
        HttpStatus.BAD_REQUEST,
        JsonRpcProtocol.PARSE_ERROR,
        "Parse error: " + rootCauseMessage(ex),
        null);
  }

  // java:S1181 — catching Error here is deliberate, not defensive overreach. An Error escaping
  // dispatch on the raw virtual thread would otherwise strand the response future until the
  // servlet async timeout (~30s of held connection, then an HTML 503 instead of JSON-RPC). The
  // catch aborts the transport to release the connection and RETHROWS, so the Error stays fatal
  // and is never converted into a handled JSON-RPC error. Pinned by the two regression tests in
  // StreamableHttpControllerTest.Handler_failures; see the fix(transport) commit for the field
  // incident (NoSuchMethodError from classpath skew) that motivated it.
  @SuppressWarnings("java:S1181")
  private CompletableFuture<ResponseEntity<Object>> handleCall(
      HttpHeaders headers, JsonRpcCall call) {
    Optional<String> headerFailure = headerValidator.validate(headers, call);
    if (headerFailure.isPresent()) {
      return CompletableFuture.completedFuture(headerMismatch(headerFailure.get(), call.id()));
    }
    var transport =
        new StreamableHttpTransport(
            () -> new PerRequestSseStream(objectMapper, streamTimeoutMillis));
    ContextSnapshot snapshot = contextSnapshotFactory.captureAll();
    Thread.ofVirtual()
        .start(
            snapshot.wrap(
                () -> {
                  // Asymmetric catches, deliberately. An Exception is the normal JSON-RPC error
                  // path: abort() turns it into an error reply (pre-commit) or closes the
                  // committed SSE stream (post-commit) — handled, nothing to rethrow. An Error
                  // (NoSuchMethodError, LinkageError, StackOverflowError...) must also reach
                  // abort() — otherwise the response future never completes and the connection
                  // strands until the servlet async timeout — but it is NOT handled: it is
                  // rethrown so it stays fatal to the dispatch thread and surfaces to the
                  // uncaught-exception handler instead of being laundered into a -32603.
                  try {
                    server.handleCall(call, transport);
                  } catch (Exception e) {
                    transport.abort(e);
                  } catch (Error e) {
                    transport.abort(e);
                    throw e;
                  }
                }));
    return transport.response();
  }

  private CompletableFuture<ResponseEntity<Object>> handleNotification(
      HttpHeaders headers, JsonRpcNotification notification) {
    Optional<String> headerFailure = headerValidator.validate(headers, notification);
    if (headerFailure.isPresent()) {
      return CompletableFuture.completedFuture(headerMismatch(headerFailure.get(), null));
    }
    server.handleNotification(notification);
    return CompletableFuture.completedFuture(ResponseEntity.accepted().build());
  }

  private ResponseEntity<Object> headerMismatch(String message, JsonNode id) {
    return jsonRpcError(
        HttpStatus.BAD_REQUEST, McpHeaderValidator.HEADER_MISMATCH_CODE, message, id);
  }

  private ResponseEntity<Object> jsonRpcError(
      HttpStatus status, int code, String message, JsonNode id) {
    JsonNode errorId = id == null ? JsonNodeFactory.instance.nullNode() : id;
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new JsonRpcError(code, message, errorId));
  }

  private static String rootCauseMessage(Throwable t) {
    Throwable cause = t;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  private static boolean acceptsJsonAndSse(String accept) {
    if (accept == null) return false;
    boolean json = false;
    boolean sse = false;
    for (MediaType t : MediaType.parseMediaTypes(accept)) {
      if (!json && MediaType.APPLICATION_JSON.equals(t)) json = true;
      if (!sse && MediaType.TEXT_EVENT_STREAM.equals(t)) sse = true;
      if (json && sse) return true;
    }
    return false;
  }
}
