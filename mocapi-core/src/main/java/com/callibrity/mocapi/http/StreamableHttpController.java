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

import static com.callibrity.ripcurl.core.JsonRpcProtocol.VERSION;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.session.McpSessionStream;
import com.callibrity.mocapi.tools.McpToolMethods;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.substrate.mailbox.Mailbox;
import org.jwcarman.substrate.mailbox.MailboxExpiredException;
import org.jwcarman.substrate.mailbox.MailboxFactory;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Thin HTTP adapter for the MCP Streamable HTTP transport. Delegates JSON-RPC dispatch to RipCurl's
 * {@link JsonRpcDispatcher} and handles only HTTP/SSE/Odyssey transport concerns.
 */
@Slf4j
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
@RequiredArgsConstructor
public class StreamableHttpController {

  private static final String INITIALIZE = "initialize";
  private static final String ERROR_KEY = "error";
  private static final String SESSION_REQUIRED = "MCP-Session-Id header is required";

  private final JsonRpcDispatcher dispatcher;
  private final McpRequestValidator validator;
  private final McpSessionService sessionService;
  private final ObjectMapper objectMapper;
  private final MailboxFactory mailboxFactory;

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public ResponseEntity<Object> handlePost(
      @RequestBody JsonRpcMessage message,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String accept,
      @RequestHeader(value = "Origin", required = false) String origin) {

    log.debug("Received POST: {}", message);
    if (!acceptsJsonAndSse(accept)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    ResponseEntity<Object> entity =
        switch (message) {
          case JsonRpcCall call -> handleCall(call, protocolVersion, sessionId, origin);
          case JsonRpcNotification notification -> handleNotification(notification, sessionId);
          case JsonRpcResult result -> handleClientResult(result, sessionId);
          case JsonRpcError error -> handleClientError(error, sessionId);
        };
    log.debug("Handled POST: {}", entity.getBody());
    return entity;
  }

  /**
   * Maps Spring's body-read failures (malformed JSON or structurally unrecognizable JSON-RPC
   * messages) to a JSON-RPC {@code -32600} error response envelope, preserving the wire-format
   * contract for clients that expect a JSON-RPC error rather than a bare HTTP 400.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Object> handleUnreadableBody(HttpMessageNotReadableException ex) {
    String message = rootCauseMessage(ex);
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .body(errorResponse(null, -32600, message));
  }

  private static String rootCauseMessage(Throwable t) {
    Throwable cause = t;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<Object> handleGet(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
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
    String version = protocolVersion != null ? protocolVersion : InitializeResult.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest().build();
    }

    try {
      if (lastEventId != null) {
        return ResponseEntity.ok().body(sessionService.reconnectStream(sessionId, lastEventId));
      }
      McpSessionStream channel = sessionService.notificationStream(sessionId);
      return ResponseEntity.ok().body(channel.subscribe());
    } catch (IllegalArgumentException _) {
      return ResponseEntity.badRequest().build();
    }
  }

  @DeleteMapping
  public ResponseEntity<Object> handleDelete(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, "Invalid origin"));
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, SESSION_REQUIRED));
    }
    if (sessionService.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of(ERROR_KEY, "Session not found or expired"));
    }
    sessionService.delete(sessionId);
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<Object> handleCall(
      JsonRpcCall call, String protocolVersion, String sessionId, String origin) {
    JsonNode id = call.id();

    String version = protocolVersion != null ? protocolVersion : InitializeResult.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(errorResponse(id, -32600, "Invalid MCP-Protocol-Version: " + version));
    }
    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(errorResponse(id, -32600, "Invalid origin"));
    }

    boolean isInitialize = INITIALIZE.equals(call.method());
    McpSession session = null;
    if (!isInitialize) {
      if (sessionId == null) {
        return ResponseEntity.badRequest().body(errorResponse(id, -32600, SESSION_REQUIRED));
      }
      var sessionOpt = sessionService.find(sessionId);
      if (sessionOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse(id, -32600, "Session not found or expired"));
      }
      session = sessionOpt.get().withSessionId(sessionId);
    }

    return dispatch(isInitialize, call, session);
  }

  private ResponseEntity<Object> dispatch(
      boolean isInitialize, JsonRpcCall call, McpSession session) {
    JsonRpcResponse response;
    if (session != null) {
      response =
          ScopedValue.where(McpRequestId.CURRENT, call.id())
              .where(McpSession.CURRENT, session)
              .call(() -> dispatcher.dispatch(call));
    } else {
      response =
          ScopedValue.where(McpRequestId.CURRENT, call.id()).call(() -> dispatcher.dispatch(call));
    }

    return switch (response) {
      case null -> ResponseEntity.accepted().build();
      case JsonRpcResult result -> handleResult(result, isInitialize, call.params());
      case JsonRpcError error ->
          ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(error);
    };
  }

  private ResponseEntity<Object> handleResult(
      JsonRpcResult result, boolean isInitialize, JsonNode params) {
    var emitter = result.getMetadata(McpToolMethods.SSE_EMITTER_KEY, SseEmitter.class);
    if (emitter.isPresent()) {
      var builder = ResponseEntity.ok();
      if (isInitialize) {
        builder.header("MCP-Session-Id", createSessionFromParams(params));
      }
      return builder.body(emitter.get());
    }

    var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
    if (isInitialize) {
      builder.header("MCP-Session-Id", createSessionFromParams(params));
    }
    return builder.body(result);
  }

  private ResponseEntity<Object> handleNotification(
      JsonRpcNotification notification, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(ERROR_KEY, SESSION_REQUIRED));
    }
    if ("notifications/initialized".equals(notification.method())) {
      sessionService.find(sessionId).ifPresent(_ -> dispatcher.dispatch(notification));
    }
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Object> handleClientResult(JsonRpcResult result, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(ERROR_KEY, SESSION_REQUIRED));
    }
    deliverToMailboxIfPresent(result.id(), result);
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Object> handleClientError(JsonRpcError error, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(ERROR_KEY, SESSION_REQUIRED));
    }
    deliverToMailboxIfPresent(error.id(), error);
    return ResponseEntity.accepted().build();
  }

  private void deliverToMailboxIfPresent(JsonNode idNode, JsonRpcResponse response) {
    if (idNode == null || idNode.isNull()) {
      return;
    }
    try {
      Mailbox<JsonRpcResponse> mailbox =
          mailboxFactory.connect(idNode.asString(), JsonRpcResponse.class);
      mailbox.deliver(response);
    } catch (MailboxExpiredException e) {
      // Orphan client response — no tool was waiting on this correlation ID, or the tool's
      // mailbox has already expired. Silently drop: the JSON-RPC spec allows unsolicited
      // responses to be ignored, and mocapi has nowhere to route them.
      log.debug("Dropped orphan client response for id {}: {}", idNode.asString(), e.getMessage());
    }
  }

  private String createSessionFromParams(JsonNode params) {
    String protocolVersion = params.path("protocolVersion").asString();
    ClientCapabilities capabilities =
        objectMapper.treeToValue(params.get("capabilities"), ClientCapabilities.class);
    Implementation clientInfo =
        objectMapper.treeToValue(params.get("clientInfo"), Implementation.class);
    McpSession session = new McpSession(protocolVersion, capabilities, clientInfo);
    String newSessionId = sessionService.create(session);
    log.info("Created new session {} for initialize request", newSessionId);
    return newSessionId;
  }

  private ObjectNode errorResponse(JsonNode id, int code, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", VERSION);
    if (id != null) {
      node.set("id", id);
    }
    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    node.set(ERROR_KEY, error);
    return node;
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
}
