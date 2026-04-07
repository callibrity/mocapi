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

import com.callibrity.mocapi.server.McpProtocol;
import com.callibrity.mocapi.server.McpRequestValidator;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpStreamContext;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
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
 * Thin HTTP adapter for the MCP Streamable HTTP transport. Delegates all protocol logic to {@link
 * McpProtocol} and handles only HTTP/SSE/Odyssey transport concerns.
 */
@Slf4j
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
public class McpStreamingController {

  private static final String INITIALIZE = "initialize";
  private static final String ERR = "error";
  private static final String NO_SESSION = "Session not found or expired";
  private static final String SESSION_REQUIRED = "MCP-Session-Id header is required";

  private final McpProtocol protocol;
  private final McpSessionManager sessionManager;
  private final OdysseyStreamRegistry registry;
  private final ObjectMapper objectMapper;

  public McpStreamingController(
      McpProtocol protocol,
      McpSessionManager sessionManager,
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper) {
    this.protocol = protocol;
    this.sessionManager = sessionManager;
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public ResponseEntity<Object> handlePost(
      @RequestBody JsonNode body,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String accept,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!acceptsJsonAndSse(accept)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    var envelope = protocol.validator().validateJsonRpcEnvelope(body);
    if (!envelope.valid()) {
      return badRequest(
          protocol
              .messages()
              .errorResponse(body.get("id"), envelope.errorCode(), envelope.errorMessage()));
    }

    if (McpRequestValidator.isNotificationOrResponse(body)) {
      return handleNotificationOrResponse(body, sessionId);
    }

    JsonNode idNode = body.get("id");
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!protocol.validator().isValidProtocolVersion(version)) {
      return badRequest(
          protocol
              .messages()
              .errorResponse(idNode, -32600, "Invalid MCP-Protocol-Version: " + version));
    }
    if (!protocol.validator().isValidOrigin(origin)) {
      return forbidden(protocol.messages().errorResponse(idNode, -32600, "Invalid origin"));
    }

    String method = body.get("method").asString();
    if (!INITIALIZE.equals(method) && sessionId == null) {
      return badRequest(protocol.messages().errorResponse(idNode, -32600, SESSION_REQUIRED));
    }

    McpSession session = resolveSession(method, sessionId);
    if (session == null) {
      return notFound(protocol.messages().errorResponse(idNode, -32600, NO_SESSION));
    }

    return dispatch(session, method, body.get("params"), idNode);
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
    if (!protocol.validator().isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERR, "Invalid origin"));
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of(ERR, SESSION_REQUIRED));
    }
    McpSession session = sessionManager.getSession(sessionId).orElse(null);
    if (session == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERR, NO_SESSION));
    }
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!protocol.validator().isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(Map.of(ERR, "Invalid MCP-Protocol-Version: " + version));
    }

    OdysseyStream stream = session.getNotificationStream();
    if (lastEventId != null) {
      return ResponseEntity.ok().body(stream.resumeAfter(lastEventId));
    }
    stream.publishRaw("");
    return ResponseEntity.ok().body(stream.subscribe());
  }

  @DeleteMapping
  public ResponseEntity<Object> handleDelete(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!protocol.validator().isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERR, "Invalid origin"));
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of(ERR, SESSION_REQUIRED));
    }
    if (!sessionManager.terminateSession(sessionId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERR, NO_SESSION));
    }
    return ResponseEntity.noContent().build();
  }

  // --- Transport helpers ---

  private ResponseEntity<Object> dispatch(
      McpSession session, String method, JsonNode params, JsonNode id) {
    return switch (protocol.dispatch(method, params, id)) {
      case McpProtocol.DispatchResult.JsonResult(var response) -> {
        var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
        if (INITIALIZE.equals(method)) {
          builder.header("MCP-Session-Id", session.getSessionId());
        }
        yield builder.body(response);
      }
      case McpProtocol.DispatchResult.StreamingDispatch(var handler, var rid) -> {
        OdysseyStream stream = registry.ephemeral();
        stream.publishRaw("");
        SseEmitter emitter = stream.subscribe();
        McpStreamContext ctx = new DefaultMcpStreamContext(stream, objectMapper);
        Thread.ofVirtual()
            .start(
                () -> {
                  ObjectNode response =
                      protocol.executeStreaming(handler, ctx, method, params, rid);
                  stream.publishJson(response);
                  stream.close();
                });
        var builder = ResponseEntity.ok();
        if (INITIALIZE.equals(method)) {
          builder.header("MCP-Session-Id", session.getSessionId());
        }
        yield builder.body(emitter);
      }
    };
  }

  private McpSession resolveSession(String method, String sessionId) {
    if (INITIALIZE.equals(method)) {
      McpSession session = sessionManager.createSession();
      log.info("Created new session {} for initialize request", session.getSessionId());
      return session;
    }
    return sessionId != null ? sessionManager.getSession(sessionId).orElse(null) : null;
  }

  private ResponseEntity<Object> handleNotificationOrResponse(JsonNode body, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(ERR, SESSION_REQUIRED));
    }
    JsonNode methodNode = body.get("method");
    if (methodNode != null) {
      String method = methodNode.asString();
      if ("notifications/initialized".equals(method)) {
        sessionManager.getSession(sessionId).ifPresent(_ -> protocol.dispatch(method, null, null));
      }
    }
    return ResponseEntity.accepted().build();
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

  private static ResponseEntity<Object> badRequest(ObjectNode body) {
    return ResponseEntity.badRequest().body(body);
  }

  private static ResponseEntity<Object> forbidden(ObjectNode body) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
  }

  private static ResponseEntity<Object> notFound(ObjectNode body) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
