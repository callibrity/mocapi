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

import com.callibrity.mocapi.client.ClientCapabilities;
import com.callibrity.mocapi.client.ClientInfo;
import com.callibrity.mocapi.server.McpProtocol;
import com.callibrity.mocapi.server.McpRequestValidator;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpSession;
import com.callibrity.mocapi.server.McpSessionStore;
import com.callibrity.mocapi.server.McpStreamContext;
import java.time.Duration;
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
  private final McpSessionStore sessionStore;
  private final OdysseyStreamRegistry registry;
  private final ObjectMapper objectMapper;
  private final Duration sessionTimeout;

  public McpStreamingController(
      McpProtocol protocol,
      McpSessionStore sessionStore,
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper,
      Duration sessionTimeout) {
    this.protocol = protocol;
    this.sessionStore = sessionStore;
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.sessionTimeout = sessionTimeout;
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
    boolean isInitialize = INITIALIZE.equals(method);

    if (!isInitialize && sessionId == null) {
      return badRequest(protocol.messages().errorResponse(idNode, -32600, SESSION_REQUIRED));
    }

    if (!isInitialize) {
      if (sessionStore.find(sessionId).isEmpty()) {
        return notFound(protocol.messages().errorResponse(idNode, -32600, NO_SESSION));
      }
      sessionStore.touch(sessionId, sessionTimeout);
    }

    return dispatch(isInitialize, sessionId, method, body.get("params"), idNode);
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
    if (sessionStore.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERR, NO_SESSION));
    }
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!protocol.validator().isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(Map.of(ERR, "Invalid MCP-Protocol-Version: " + version));
    }

    sessionStore.touch(sessionId, sessionTimeout);
    OdysseyStream stream = registry.channel(sessionId);
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
    if (sessionStore.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERR, NO_SESSION));
    }
    sessionStore.delete(sessionId);
    registry.channel(sessionId).delete();
    return ResponseEntity.noContent().build();
  }

  // --- Transport helpers ---

  private ResponseEntity<Object> dispatch(
      boolean isInitialize, String sessionId, String method, JsonNode params, JsonNode id) {
    return switch (protocol.dispatch(method, params, id)) {
      case McpProtocol.DispatchResult.JsonResult(var response) -> {
        var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
        if (isInitialize) {
          String newSessionId = createSessionFromParams(params);
          builder.header("MCP-Session-Id", newSessionId);
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
        if (isInitialize) {
          String newSessionId = createSessionFromParams(params);
          builder.header("MCP-Session-Id", newSessionId);
        }
        yield builder.body(emitter);
      }
    };
  }

  private String createSessionFromParams(JsonNode params) {
    String protocolVersion = params.path("protocolVersion").asString();
    ClientCapabilities capabilities =
        objectMapper.treeToValue(params.get("capabilities"), ClientCapabilities.class);
    ClientInfo clientInfo = objectMapper.treeToValue(params.get("clientInfo"), ClientInfo.class);
    McpSession session = new McpSession(protocolVersion, capabilities, clientInfo);
    String newSessionId = sessionStore.save(session, sessionTimeout);
    log.info("Created new session {} for initialize request", newSessionId);
    return newSessionId;
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
        sessionStore.find(sessionId).ifPresent(_ -> protocol.dispatch(method, null, null));
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
