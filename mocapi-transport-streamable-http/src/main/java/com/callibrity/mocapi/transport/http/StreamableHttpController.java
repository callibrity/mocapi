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
import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpContextResult.ProtocolVersionMismatch;
import com.callibrity.mocapi.server.McpContextResult.SessionIdRequired;
import com.callibrity.mocapi.server.McpContextResult.SessionNotFound;
import com.callibrity.mocapi.server.McpContextResult.ValidContext;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberConfig;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thin HTTP adapter for the MCP Streamable HTTP transport. Delegates all protocol logic to {@link
 * McpServer} and handles only HTTP/SSE transport concerns.
 */
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
public class StreamableHttpController {

  public static final String SESSION_ID_HEADER = "MCP-Session-Id";
  public static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
  public static final String LAST_EVENT_ID_HEADER = "Last-Event-ID";
  public static final String INVALID_ORIGIN_MESSAGE = "Forbidden: Invalid Origin";
  private static final String PRIMING_EVENT_ID = "PRIMING";
  private final McpServer server;
  private final McpRequestValidator validator;
  private final Odyssey odyssey;
  private final ObjectMapper objectMapper;
  private final byte[] masterKey;

  public StreamableHttpController(
      McpServer server,
      McpRequestValidator validator,
      Odyssey odyssey,
      ObjectMapper objectMapper,
      byte[] masterKey) {
    Ciphers.validateAesGcmKey(masterKey);
    this.server = server;
    this.validator = validator;
    this.odyssey = odyssey;
    this.objectMapper = objectMapper;
    this.masterKey = masterKey.clone();
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
      @RequestHeader(value = "Origin", required = false) String origin) {

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
    String sessionId = context.sessionId();
    try {
      if (lastEventId != null) {
        return ResponseEntity.ok().body(reconnect(sessionId, lastEventId));
      }
      SseEmitter emitter =
          odyssey.stream(sessionId, JsonRpcMessage.class)
              .subscribe(cfg -> cfg.mapper(encryptingMapper(sessionId)));
      return ResponseEntity.ok().body(emitter);
    } catch (IllegalArgumentException _) {
      return ResponseEntity.badRequest().build();
    }
  }

  private CompletableFuture<ResponseEntity<Object>> handleCall(
      McpContext context, JsonRpcCall call) {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    String sessionId = context.sessionId();
    BiConsumer<SubscriberConfig<JsonRpcMessage>, OdysseyStream<JsonRpcMessage>>
        subscribeConfigurer =
            (cfg, stream) ->
                cfg.mapper(encryptingMapper(sessionId))
                    .onSubscribe(
                        e ->
                            e.send(
                                SseEmitter.event()
                                    .id(encrypt(sessionId, stream.name() + ":" + PRIMING_EVENT_ID))
                                    .data("")));
    var transport = new LazyHttpTransport(future, odyssey, subscribeConfigurer);
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                server.handleCall(context, call, transport);
              } catch (Exception e) {
                future.completeExceptionally(e);
              }
            });
    return future;
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

  private SseEmitter reconnect(String sessionId, String lastEventId) {
    String plaintext = decrypt(sessionId, lastEventId);
    int colonIndex = plaintext.lastIndexOf(':');
    if (colonIndex < 0) {
      throw new IllegalArgumentException("Invalid encrypted event ID format");
    }
    String streamName = plaintext.substring(0, colonIndex);
    String rawEventId = plaintext.substring(colonIndex + 1);
    var mapper = encryptingMapper(sessionId);
    var stream = odyssey.stream(streamName, JsonRpcMessage.class);
    if (PRIMING_EVENT_ID.equals(rawEventId)) {
      return stream.subscribe(cfg -> cfg.mapper(mapper));
    }
    return stream.resume(rawEventId, cfg -> cfg.mapper(mapper));
  }

  private SseEventMapper<JsonRpcMessage> encryptingMapper(String sessionId) {
    return event -> {
      String plaintext = event.streamName() + ":" + event.id();
      String encryptedId = encrypt(sessionId, plaintext);
      SseEmitter.SseEventBuilder builder =
          SseEmitter.event().id(encryptedId).data(objectMapper.writeValueAsString(event.data()));
      if (event.eventType() != null) {
        builder.name(event.eventType());
      }
      return builder;
    };
  }

  private String encrypt(String sessionId, String plaintext) {
    try {
      byte[] encrypted =
          Ciphers.encryptAesGcm(masterKey, sessionId, plaintext.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  private String decrypt(String sessionId, String ciphertext) {
    byte[] combined;
    try {
      combined = Base64.getDecoder().decode(ciphertext);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Malformed Base64 in encrypted token", e);
    }
    try {
      byte[] plaintext = Ciphers.decryptAesGcm(masterKey, sessionId, combined);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalArgumentException("Invalid or tampered encrypted token", e);
    }
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
    var types = Set.copyOf(MediaType.parseMediaTypes(accept));
    return types.contains(MediaType.APPLICATION_JSON)
        && types.contains(MediaType.TEXT_EVENT_STREAM);
  }

  private static boolean acceptsSse(String accept) {
    if (accept == null) return false;
    return Set.copyOf(MediaType.parseMediaTypes(accept)).contains(MediaType.TEXT_EVENT_STREAM);
  }
}
