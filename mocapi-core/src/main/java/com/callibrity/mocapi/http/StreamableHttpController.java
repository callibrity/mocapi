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

import com.callibrity.mocapi.server.InitializeResponse;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionService;
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
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.Mailbox;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
  private static final String SESSION_REQUIRED = "MCP-Session-Id header is required";

  private final JsonRpcDispatcher dispatcher;
  private final McpRequestValidator validator;
  private final McpSessionService sessionService;
  private final OdysseyStreamRegistry registry;
  private final ObjectMapper objectMapper;
  private final MailboxFactory mailboxFactory;

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public ResponseEntity<Object> handlePost(
      @RequestBody JsonNode body,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String accept,
      @RequestHeader(value = "Origin", required = false) String origin) {

    log.debug("Received POST: {}", body);
    if (!acceptsJsonAndSse(accept)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    JsonRpcMessage message;
    try {
      message = JsonRpcMessage.parse(body);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(errorResponse(null, -32600, e.getMessage()));
    }

    return switch (message) {
      case JsonRpcCall call -> handleCall(call, protocolVersion, sessionId, origin);
      case JsonRpcNotification notification -> handleNotification(notification, sessionId);
      case JsonRpcResult result -> handleClientResult(result, sessionId);
      case JsonRpcError error -> handleClientError(error, sessionId);
    };
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
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid origin"));
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of("error", SESSION_REQUIRED));
    }
    if (sessionService.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Session not found or expired"));
    }
    String version =
        protocolVersion != null ? protocolVersion : InitializeResponse.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Invalid MCP-Protocol-Version: " + version));
    }

    OdysseyStream stream = registry.channel(sessionId);
    if (lastEventId != null) {
      return ResponseEntity.ok().body(stream.resumeAfter(lastEventId));
    }
    stream.publishJson(Map.of());
    return ResponseEntity.ok().body(stream.subscribe());
  }

  @DeleteMapping
  public ResponseEntity<Object> handleDelete(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid origin"));
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of("error", SESSION_REQUIRED));
    }
    if (sessionService.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Session not found or expired"));
    }
    sessionService.delete(sessionId);
    registry.channel(sessionId).delete();
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<Object> handleCall(
      JsonRpcCall call, String protocolVersion, String sessionId, String origin) {
    JsonNode id = call.id();

    String version =
        protocolVersion != null ? protocolVersion : InitializeResponse.PROTOCOL_VERSION;
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
    ScopedValue.Carrier carrier = ScopedValue.where(McpRequestId.CURRENT, call.id());
    if (session != null) {
      carrier = carrier.where(McpSession.CURRENT, session);
    }
    JsonRpcResponse response = carrier.call(() -> dispatcher.dispatch(call));

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
          .body(Map.of("error", SESSION_REQUIRED));
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
          .body(Map.of("error", SESSION_REQUIRED));
    }
    JsonNode idNode = result.id();
    if (idNode != null && !idNode.isNull()) {
      Mailbox<JsonNode> mailbox =
          mailboxFactory.create("elicit:" + idNode.asString(), JsonNode.class);
      mailbox.deliver(result.result());
    }
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Object> handleClientError(JsonRpcError error, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("error", SESSION_REQUIRED));
    }
    JsonNode idNode = error.id();
    if (idNode != null && !idNode.isNull()) {
      ObjectNode errorWrapper = objectMapper.createObjectNode();
      errorWrapper.set("error", objectMapper.valueToTree(error.error()));
      Mailbox<JsonNode> mailbox =
          mailboxFactory.create("elicit:" + idNode.asString(), JsonNode.class);
      mailbox.deliver(errorWrapper);
    }
    return ResponseEntity.accepted().build();
  }

  private String createSessionFromParams(JsonNode params) {
    String protocolVersion = params.path("protocolVersion").asString();
    ClientCapabilities capabilities =
        objectMapper.treeToValue(params.get("capabilities"), ClientCapabilities.class);
    ClientInfo clientInfo = objectMapper.treeToValue(params.get("clientInfo"), ClientInfo.class);
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
    node.set("error", error);
    return node;
  }

  private static boolean acceptsJsonAndSse(String accept) {
    if (accept == null) return false;
    List<MediaType> types = MediaType.parseMediaTypes(accept);
    boolean json = false, sse = false;
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
