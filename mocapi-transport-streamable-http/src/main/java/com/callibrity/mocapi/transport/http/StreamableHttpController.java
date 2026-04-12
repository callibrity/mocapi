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

import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;

import com.callibrity.mocapi.server.McpContext;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.SseEventMapper;
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
@Slf4j
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
public class StreamableHttpController {

  private final McpServer protocol;
  private final McpRequestValidator validator;
  private final McpSessionService sessionService;
  private final Odyssey odyssey;
  private final ObjectMapper objectMapper;
  private final byte[] masterKey;

  public StreamableHttpController(
      McpServer protocol,
      McpRequestValidator validator,
      McpSessionService sessionService,
      Odyssey odyssey,
      ObjectMapper objectMapper,
      byte[] masterKey) {
    Ciphers.validateAesGcmKey(masterKey);
    this.protocol = protocol;
    this.validator = validator;
    this.sessionService = sessionService;
    this.odyssey = odyssey;
    this.objectMapper = objectMapper;
    this.masterKey = masterKey.clone();
  }

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public ResponseEntity<Object> handlePost(
      @RequestBody JsonRpcMessage message,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String accept,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!acceptsJsonAndSse(accept)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }
    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    if (!validator.isValidProtocolVersion(protocolVersion)) {
      return jsonRpcError(
          HttpStatus.BAD_REQUEST, -32600, "Invalid MCP-Protocol-Version: " + protocolVersion);
    }

    boolean isInitialize =
        message instanceof JsonRpcCall call && "initialize".equals(call.method());
    if (!isInitialize) {
      if (sessionId == null) {
        return sessionError(message, HttpStatus.BAD_REQUEST, "MCP-Session-Id header is required");
      }
      if (sessionService.find(sessionId).isEmpty()) {
        return sessionError(message, HttpStatus.NOT_FOUND, "Session not found or expired");
      }
    }

    McpContext context = new SimpleContext(sessionId, protocolVersion);

    return switch (message) {
      case JsonRpcCall call -> handleCall(context, call, sessionId);
      case JsonRpcNotification notification -> handleNotification(context, notification, sessionId);
      case JsonRpcResponse response -> handleResponse(context, response, sessionId);
    };
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Object> handleUnreadableBody(HttpMessageNotReadableException ex) {
    String message = rootCauseMessage(ex);
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", VERSION);
    ObjectNode error = node.putObject("error");
    error.put("code", -32600);
    error.put("message", message);
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(node);
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<Object> handleGet(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestHeader(value = "Accept", required = false) String accept,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!acceptsSse(accept)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }
    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().build();
    }
    if (sessionService.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    try {
      if (lastEventId != null) {
        return ResponseEntity.ok().body(reconnect(sessionId, lastEventId));
      }
      SseEmitter emitter =
          odyssey.subscribe(
              sessionId, JsonRpcMessage.class, cfg -> cfg.mapper(encryptingMapper(sessionId)));
      return ResponseEntity.ok().body(emitter);
    } catch (IllegalArgumentException _) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping
  public ResponseEntity<Object> handleDelete(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    if (sessionId == null) {
      return simpleJsonError(HttpStatus.BAD_REQUEST, "MCP-Session-Id header is required");
    }
    if (sessionService.find(sessionId).isEmpty()) {
      return simpleJsonError(HttpStatus.NOT_FOUND, "Session not found or expired");
    }
    protocol.terminate(sessionId);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<Object> handleCall(
      McpContext context, JsonRpcCall call, String sessionId) {
    if (sessionId == null) {
      SynchronousTransport transport = new SynchronousTransport();
      protocol.handleCall(context, call, transport);
      return transport.toResponseEntity();
    }

    BufferingTransport transport = new BufferingTransport();
    Thread.ofVirtual().start(() -> protocol.handleCall(context, call, transport));

    JsonRpcMessage first;
    try {
      first = transport.poll(500, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    if (first instanceof JsonRpcResponse resp) {
      return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
    }

    // Streaming or blocked call — set up SSE and forward messages
    String streamName = UUID.randomUUID().toString();
    var publisher = odyssey.publisher(streamName, JsonRpcMessage.class);
    if (first != null) {
      publisher.publish(first);
    }

    Thread.ofVirtual()
        .start(
            () -> {
              try {
                JsonRpcMessage msg;
                while ((msg = transport.poll(30, TimeUnit.SECONDS)) != null) {
                  publisher.publish(msg);
                  if (msg instanceof JsonRpcResponse) {
                    publisher.complete();
                    return;
                  }
                }
                publisher.complete();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                publisher.complete();
              }
            });

    return ResponseEntity.ok()
        .body(
            odyssey.subscribe(
                streamName, JsonRpcMessage.class, cfg -> cfg.mapper(encryptingMapper(sessionId))));
  }

  private ResponseEntity<Object> handleNotification(
      McpContext context, JsonRpcNotification notification, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest().build();
    }
    protocol.handleNotification(context, notification);
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Object> handleResponse(
      McpContext context, JsonRpcResponse response, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest().build();
    }
    protocol.handleResponse(context, response);
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Object> jsonRpcError(HttpStatus status, int code, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", VERSION);
    ObjectNode error = node.putObject("error");
    error.put("code", code);
    error.put("message", message);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(node);
  }

  private ResponseEntity<Object> simpleJsonError(HttpStatus status, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("error", message);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(node);
  }

  private ResponseEntity<Object> sessionError(
      JsonRpcMessage message, HttpStatus status, String errorMessage) {
    if (message instanceof JsonRpcCall) {
      return jsonRpcError(status, -32600, errorMessage);
    }
    return simpleJsonError(status, errorMessage);
  }

  private SseEmitter reconnect(String sessionId, String lastEventId) {
    String plaintext = decrypt(sessionId, lastEventId);
    int colonIndex = plaintext.lastIndexOf(':');
    if (colonIndex < 0) {
      throw new IllegalArgumentException("Invalid encrypted event ID format");
    }
    String streamName = plaintext.substring(0, colonIndex);
    String rawEventId = plaintext.substring(colonIndex + 1);
    return odyssey.resume(
        streamName,
        JsonRpcMessage.class,
        rawEventId,
        cfg -> cfg.mapper(encryptingMapper(sessionId)));
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

  private static String rootCauseMessage(Throwable t) {
    Throwable cause = t;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  private static boolean acceptsJsonAndSse(String accept) {
    if (accept == null) return false;
    List<MediaType> types = MediaType.parseMediaTypes(accept);
    boolean json = false;
    boolean sse = false;
    for (MediaType mt : types) {
      if (mt.includes(MediaType.APPLICATION_JSON)) json = true;
      if (mt.includes(MediaType.TEXT_EVENT_STREAM)) sse = true;
    }
    return json && sse;
  }

  private static boolean acceptsSse(String accept) {
    if (accept == null) return false;
    return MediaType.parseMediaTypes(accept).stream()
        .anyMatch(mt -> mt.includes(MediaType.TEXT_EVENT_STREAM));
  }

  private record SimpleContext(String sessionId, String protocolVersion) implements McpContext {}
}
