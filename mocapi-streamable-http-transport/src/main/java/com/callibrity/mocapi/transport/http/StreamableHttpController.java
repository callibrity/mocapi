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

import static com.callibrity.mocapi.model.McpMethods.INITIALIZE;
import static com.callibrity.mocapi.transport.http.StreamableHttpTransport.SESSION_ID_HEADER;
import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult.ProtocolVersionMismatch;
import com.callibrity.mocapi.server.McpContextResult.SessionIdRequired;
import com.callibrity.mocapi.server.McpContextResult.SessionNotFound;
import com.callibrity.mocapi.server.McpContextResult.ValidContext;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.transport.http.sse.SseStreamFactory;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thin HTTP adapter for the MCP Streamable HTTP transport. Delegates all protocol logic to {@link
 * McpServer}; all SSE/encryption plumbing lives behind {@link SseStreamFactory}.
 */
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
public class StreamableHttpController {

  public static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
  public static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";
  public static final String INVALID_ORIGIN_MESSAGE = "Forbidden: Invalid Origin";

  private final McpServer server;
  private final McpRequestValidator validator;
  private final SseStreamFactory sseStreamFactory;
  private final ObjectMapper objectMapper;
  private final ContextSnapshotFactory contextSnapshotFactory;

  public StreamableHttpController(
      McpServer server,
      McpRequestValidator validator,
      SseStreamFactory sseStreamFactory,
      ObjectMapper objectMapper,
      ContextSnapshotFactory contextSnapshotFactory) {
    this.server = server;
    this.validator = validator;
    this.sseStreamFactory = sseStreamFactory;
    this.objectMapper = objectMapper;
    this.contextSnapshotFactory = contextSnapshotFactory;
  }

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public CompletableFuture<ResponseEntity<Object>> handlePost(
      @RequestBody JsonRpcMessage message,
      @RequestHeader(value = MCP_PROTOCOL_VERSION_HEADER, required = false) String protocolVersion,
      @RequestHeader(value = SESSION_ID_HEADER, required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String accept,
      @RequestHeader(value = "Origin", required = false) String origin) {
    if (!acceptsJsonAndSse(accept)) {
      return CompletableFuture.completedFuture(
          jsonRpcError(
              HttpStatus.NOT_ACCEPTABLE,
              -32000,
              "Not Acceptable: Client must accept both application/json and text/event-stream"));
    }
    if (!validator.isValidOrigin(origin)) {
      return CompletableFuture.completedFuture(
          jsonRpcError(HttpStatus.FORBIDDEN, -32000, INVALID_ORIGIN_MESSAGE));
    }

    return switch (message) {
      case JsonRpcCall call -> {
        if (INITIALIZE.equals(call.method())) {
          yield handleCall(McpContext.empty(), call);
        }
        yield withContextAsync(sessionId, protocolVersion, ctx -> handleCall(ctx, call));
      }
      case JsonRpcNotification notification ->
          withContextAsync(
              sessionId, protocolVersion, ctx -> handleNotification(ctx, notification));
      case JsonRpcResponse response ->
          withContextAsync(sessionId, protocolVersion, ctx -> handleResponse(ctx, response));
    };
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Object> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return jsonRpcError(HttpStatus.BAD_REQUEST, -32700, "Parse error: " + rootCauseMessage(ex));
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<Object> handleGet(
      @RequestHeader(SESSION_ID_HEADER) String sessionId,
      @RequestHeader(value = MCP_PROTOCOL_VERSION_HEADER, required = false) String protocolVersion,
      @RequestHeader(value = LAST_EVENT_ID_HEADER, required = false) String lastEventId,
      @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
      @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin) {
    if (!acceptsSse(accept)) {
      return jsonRpcError(
          HttpStatus.NOT_ACCEPTABLE,
          -32000,
          "Not Acceptable: Client must accept text/event-stream");
    }
    if (!validator.isValidOrigin(origin)) {
      return jsonRpcError(HttpStatus.FORBIDDEN, -32000, INVALID_ORIGIN_MESSAGE);
    }

    return withContext(sessionId, protocolVersion, ctx -> handleGetStream(ctx, lastEventId));
  }

  @DeleteMapping
  public ResponseEntity<Object> handleDelete(
      @RequestHeader(SESSION_ID_HEADER) String sessionId,
      @RequestHeader(value = MCP_PROTOCOL_VERSION_HEADER, required = false) String protocolVersion,
      @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin) {

    if (!validator.isValidOrigin(origin)) {
      return jsonRpcError(HttpStatus.FORBIDDEN, -32000, INVALID_ORIGIN_MESSAGE);
    }

    return withContext(
        sessionId,
        protocolVersion,
        ctx -> {
          server.terminate(ctx);
          return ResponseEntity.noContent().build();
        });
  }

  private ResponseEntity<Object> withContext(
      String sessionId,
      String protocolVersion,
      Function<McpContext, ResponseEntity<Object>> action) {
    return switch (server.createContext(sessionId, protocolVersion)) {
      case ValidContext(var ctx) -> action.apply(ctx);
      case SessionIdRequired(var code, var msg) -> jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
      case SessionNotFound(var code, var msg) -> jsonRpcError(HttpStatus.NOT_FOUND, code, msg);
      case ProtocolVersionMismatch(var code, var msg) ->
          jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
    };
  }

  private CompletableFuture<ResponseEntity<Object>> withContextAsync(
      String sessionId,
      String protocolVersion,
      Function<McpContext, CompletableFuture<ResponseEntity<Object>>> action) {
    return switch (server.createContext(sessionId, protocolVersion)) {
      case ValidContext(var ctx) -> action.apply(ctx);
      case SessionIdRequired(var code, var msg) ->
          CompletableFuture.completedFuture(jsonRpcError(HttpStatus.BAD_REQUEST, code, msg));
      case SessionNotFound(var code, var msg) ->
          CompletableFuture.completedFuture(jsonRpcError(HttpStatus.NOT_FOUND, code, msg));
      case ProtocolVersionMismatch(var code, var msg) ->
          CompletableFuture.completedFuture(jsonRpcError(HttpStatus.BAD_REQUEST, code, msg));
    };
  }

  private ResponseEntity<Object> handleGetStream(McpContext context, String lastEventId) {
    try {
      var stream =
          lastEventId != null
              ? sseStreamFactory.resumeStream(context, lastEventId)
              : sseStreamFactory.sessionStream(context);
      return ResponseEntity.ok().body(stream.createEmitter());
    } catch (IllegalArgumentException _) {
      return ResponseEntity.badRequest().build();
    }
  }

  private CompletableFuture<ResponseEntity<Object>> handleCall(
      McpContext context, JsonRpcCall call) {
    var transport = new StreamableHttpTransport(() -> sseStreamFactory.responseStream(context));
    ContextSnapshot snapshot = contextSnapshotFactory.captureAll();
    Thread.ofVirtual()
        .start(
            snapshot.wrap(
                () -> {
                  try {
                    server.handleCall(context, call, transport);
                  } catch (Exception e) {
                    transport.response().completeExceptionally(e);
                  }
                }));
    return transport.response();
  }

  private CompletableFuture<ResponseEntity<Object>> handleNotification(
      McpContext context, JsonRpcNotification notification) {
    server.handleNotification(context, notification);
    return CompletableFuture.completedFuture(ResponseEntity.accepted().build());
  }

  private CompletableFuture<ResponseEntity<Object>> handleResponse(
      McpContext context, JsonRpcResponse response) {
    server.handleResponse(context, response);
    return CompletableFuture.completedFuture(ResponseEntity.accepted().build());
  }

  private ResponseEntity<Object> jsonRpcError(HttpStatus status, int code, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", VERSION);
    ObjectNode error = node.putObject("error");
    error.put("code", code);
    error.put("message", message);
    node.putNull("id");
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(node);
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

  private static boolean acceptsSse(String accept) {
    if (accept == null) return false;
    for (MediaType t : MediaType.parseMediaTypes(accept)) {
      if (MediaType.TEXT_EVENT_STREAM.equals(t)) return true;
    }
    return false;
  }
}
