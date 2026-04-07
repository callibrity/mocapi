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

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.exception.McpException;
import com.callibrity.mocapi.server.exception.McpInternalErrorException;
import com.callibrity.mocapi.tools.McpToolsCapability;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
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
 * MCP Streaming Controller implementing the MCP 2025-11-25 Streamable HTTP transport.
 *
 * <p>Handles POST (client requests), GET (server notifications), and DELETE (session termination).
 * Methods that don't need streaming return {@code application/json}; methods that declare an {@link
 * McpStreamContext} parameter return {@code text/event-stream} via SSE.
 */
@Slf4j
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
public class McpStreamingController {

  private final McpServer mcpServer;
  private final McpSessionManager sessionManager;
  private final OdysseyStreamRegistry registry;
  private final ObjectMapper objectMapper;
  private final List<String> allowedOrigins;

  private final Map<String, McpMethodHandler> methodHandlers;

  private static final String ERROR_KEY = "error";
  private static final String JSONRPC_VERSION = "2.0";
  private static final String JSONRPC_FIELD = "jsonrpc";
  private static final String SESSION_NOT_FOUND = "Session not found or expired";
  private static final String INITIALIZE_METHOD = "initialize";

  /**
   * A method handler is either a simple JSON handler (no stream context needed) or a streaming
   * handler (requires an {@link McpStreamContext} for sending intermediate messages).
   */
  sealed interface McpMethodHandler {
    record Json(Function<JsonNode, Object> handler) implements McpMethodHandler {}

    record Streaming(BiFunction<JsonNode, McpStreamContext, Object> handler)
        implements McpMethodHandler {}
  }

  public McpStreamingController(
      McpServer mcpServer,
      McpSessionManager sessionManager,
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper,
      List<String> allowedOrigins,
      McpToolsCapability toolsCapability) {
    this.mcpServer = mcpServer;
    this.sessionManager = sessionManager;
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.allowedOrigins = allowedOrigins;

    this.methodHandlers = buildMethodHandlers(toolsCapability);
  }

  private Map<String, McpMethodHandler> buildMethodHandlers(McpToolsCapability toolsCapability) {
    Map<String, McpMethodHandler> handlers = new LinkedHashMap<>();
    handlers.put(INITIALIZE_METHOD, new McpMethodHandler.Json(this::initializeServer));
    handlers.put("ping", new McpMethodHandler.Json(_ -> mcpServer.ping()));
    handlers.put(
        "notifications/initialized",
        new McpMethodHandler.Json(
            _ -> {
              mcpServer.clientInitialized();
              return null;
            }));

    if (toolsCapability != null) {
      handlers.put(
          "tools/list",
          new McpMethodHandler.Json(
              params ->
                  toolsCapability.listTools(
                      params != null ? params.path("cursor").asString(null) : null)));
      handlers.put(
          "tools/call", new McpMethodHandler.Json(params -> callTool(toolsCapability, params)));
    }

    return Map.copyOf(handlers);
  }

  @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
  public ResponseEntity<Object> handlePost(
      @RequestBody JsonNode requestBody,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String acceptHeader,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!isValidPostAcceptHeader(acceptHeader)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    ResponseEntity<Object> envelopeError = validateJsonRpcEnvelope(requestBody);
    if (envelopeError != null) {
      return envelopeError;
    }

    JsonNode idNode = requestBody.get("id");
    JsonNode methodNode = requestBody.get("method");

    if (isNotificationOrResponse(methodNode, idNode, requestBody)) {
      return handleNotificationOrResponse(requestBody, sessionId, methodNode != null);
    }

    ResponseEntity<Object> headerError = validateHeaders(protocolVersion, origin, idNode);
    if (headerError != null) {
      return headerError;
    }

    String method = methodNode.asString();

    if (!INITIALIZE_METHOD.equals(method) && sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(createErrorResponse(idNode, -32600, "MCP-Session-Id header is required"));
    }

    McpSession session = resolveSession(method, sessionId);
    if (session == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_JSON)
          .body(createErrorResponse(idNode, -32600, SESSION_NOT_FOUND));
    }

    return processRequest(session, method, requestBody.get("params"), idNode);
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<Object> handleGet(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestHeader(value = "Accept", required = false) String acceptHeader,
      @RequestHeader(value = "Origin", required = false) String origin) {

    if (!isValidGetAcceptHeader(acceptHeader)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    ResponseEntity<Object> originError = validateOrigin(origin);
    if (originError != null) {
      return originError;
    }

    if (sessionId == null) {
      log.warn("GET request missing MCP-Session-Id header");
      return ResponseEntity.badRequest()
          .body(Map.of(ERROR_KEY, "MCP-Session-Id header is required"));
    }

    McpSession session = sessionManager.getSession(sessionId).orElse(null);
    if (session == null) {
      log.warn("GET request with unknown session: {}", sessionId);
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, SESSION_NOT_FOUND));
    }

    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!isValidProtocolVersion(version)) {
      log.warn("Invalid protocol version on GET: {}", version);
      return ResponseEntity.badRequest()
          .body(Map.of(ERROR_KEY, "Invalid MCP-Protocol-Version: " + version));
    }

    OdysseyStream stream = session.getNotificationStream();
    SseEmitter emitter;
    if (lastEventId != null) {
      emitter = stream.resumeAfter(lastEventId);
    } else {
      stream.publishRaw("");
      emitter = stream.subscribe();
    }

    return ResponseEntity.ok().body(emitter);
  }

  @DeleteMapping
  public ResponseEntity<Object> handleDelete(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Origin", required = false) String origin) {

    ResponseEntity<Object> originError = validateOrigin(origin);
    if (originError != null) {
      return originError;
    }

    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .body(Map.of(ERROR_KEY, "MCP-Session-Id header is required"));
    }

    if (!sessionManager.terminateSession(sessionId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERROR_KEY, SESSION_NOT_FOUND));
    }

    return ResponseEntity.noContent().build();
  }

  // --- Request validation ---

  private ResponseEntity<Object> validateJsonRpcEnvelope(JsonNode requestBody) {
    if (!JSONRPC_VERSION.equals(requestBody.path(JSONRPC_FIELD).asString(null))) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse(requestBody.get("id"), -32600, "jsonrpc must be \"2.0\""));
    }

    JsonNode idNode = requestBody.get("id");
    if (idNode != null && !idNode.isNull() && !idNode.isString() && !idNode.isNumber()) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse(null, -32600, "id must be a string, number, or null"));
    }

    return null;
  }

  private ResponseEntity<Object> validateHeaders(
      String protocolVersion, String origin, JsonNode idNode) {
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!isValidProtocolVersion(version)) {
      log.warn("Invalid protocol version: {}", version);
      return ResponseEntity.badRequest()
          .body(createErrorResponse(idNode, -32600, "Invalid MCP-Protocol-Version: " + version));
    }

    if (origin != null && !isValidOrigin(origin)) {
      log.warn("Invalid origin: {}", origin);
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse(idNode, -32600, "Invalid origin"));
    }

    return null;
  }

  private static boolean isNotificationOrResponse(
      JsonNode methodNode, JsonNode idNode, JsonNode requestBody) {
    boolean isNotification = methodNode != null && idNode == null;
    boolean isResponse =
        methodNode == null && (requestBody.has("result") || requestBody.has(ERROR_KEY));
    return isNotification || isResponse;
  }

  // --- Session resolution ---

  private McpSession resolveSession(String method, String sessionId) {
    if (INITIALIZE_METHOD.equals(method)) {
      McpSession session = sessionManager.createSession();
      log.info("Created new session {} for initialize request", session.getSessionId());
      return session;
    }

    if (sessionId != null) {
      Optional<McpSession> session = sessionManager.getSession(sessionId);
      if (session.isEmpty()) {
        log.warn("Session not found or expired: {}", sessionId);
      }
      return session.orElse(null);
    }

    log.warn("Non-initialize request without MCP-Session-Id header for method: {}", method);
    return null;
  }

  // --- Request processing ---

  private ResponseEntity<Object> processRequest(
      McpSession session, String method, JsonNode params, JsonNode idNode) {
    McpMethodHandler handler = methodHandlers.get(method);
    if (handler == null) {
      log.warn("Method not found: {}", method);
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(createErrorResponse(idNode, -32601, "Method not found: " + method));
    }

    return switch (handler) {
      case McpMethodHandler.Json(var fn) -> processJsonRequest(session, method, params, idNode, fn);
      case McpMethodHandler.Streaming(var fn) ->
          processStreamingRequest(session, method, params, idNode, fn);
    };
  }

  private ResponseEntity<Object> processJsonRequest(
      McpSession session,
      String method,
      JsonNode params,
      JsonNode idNode,
      Function<JsonNode, Object> handler) {
    try {
      Object result = handler.apply(params);
      ObjectNode response = buildJsonRpcResponse(idNode, result);

      ResponseEntity.BodyBuilder builder =
          ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
      if (INITIALIZE_METHOD.equals(method)) {
        builder.header("MCP-Session-Id", session.getSessionId());
      }
      return builder.body(response);
    } catch (McpException e) {
      log.warn("MCP error processing {}: {}", method, e.getMessage());
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(createErrorResponse(idNode, e.getCode(), e.getMessage()));
    } catch (IllegalArgumentException e) {
      log.warn("Invalid argument processing {}: {}", method, e.getMessage());
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(createErrorResponse(idNode, -32601, "Method not found: " + method));
    } catch (RuntimeException e) {
      log.error("Error processing JSON-RPC request", e);
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(createErrorResponse(idNode, -32603, "Internal error"));
    }
  }

  private ResponseEntity<Object> processStreamingRequest(
      McpSession session,
      String method,
      JsonNode params,
      JsonNode idNode,
      BiFunction<JsonNode, McpStreamContext, Object> handler) {
    OdysseyStream stream = registry.ephemeral();
    stream.publishRaw("");
    SseEmitter emitter = stream.subscribe();
    McpStreamContext context = new McpStreamContext(stream, objectMapper);

    Thread.ofVirtual()
        .start(() -> executeStreamingMethod(stream, context, method, params, idNode, handler));

    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (INITIALIZE_METHOD.equals(method)) {
      builder.header("MCP-Session-Id", session.getSessionId());
    }
    return builder.body(emitter);
  }

  private void executeStreamingMethod(
      OdysseyStream stream,
      McpStreamContext context,
      String method,
      JsonNode params,
      JsonNode idNode,
      BiFunction<JsonNode, McpStreamContext, Object> handler) {
    try {
      Object result = handler.apply(params, context);
      stream.publishJson(buildJsonRpcResponse(idNode, result));
      stream.close();
    } catch (McpException e) {
      log.warn("MCP error processing {}: {}", method, e.getMessage());
      stream.publishJson(createErrorResponse(idNode, e.getCode(), e.getMessage()));
      stream.close();
    } catch (IllegalArgumentException _) {
      log.warn("Method not found: {}", method);
      stream.publishJson(createErrorResponse(idNode, -32601, "Method not found: " + method));
      stream.close();
    } catch (RuntimeException e) {
      log.error("Error processing JSON-RPC request", e);
      stream.publishJson(createErrorResponse(idNode, -32603, "Internal error"));
      stream.close();
    }
  }

  private ObjectNode buildJsonRpcResponse(JsonNode idNode, Object result) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put(JSONRPC_FIELD, JSONRPC_VERSION);
    if (idNode != null) {
      response.set("id", idNode);
    }
    if (result != null) {
      response.set("result", objectMapper.valueToTree(result));
    }
    return response;
  }

  private McpServer.InitializeResponse initializeServer(JsonNode params) {
    try {
      return mcpServer.initialize(
          params.path("protocolVersion").asString(),
          objectMapper.treeToValue(
              params.get("capabilities"), com.callibrity.mocapi.client.ClientCapabilities.class),
          objectMapper.treeToValue(
              params.get("clientInfo"), com.callibrity.mocapi.client.ClientInfo.class));
    } catch (tools.jackson.core.JacksonException e) {
      throw new McpInternalErrorException("Failed to deserialize initialize params", e);
    }
  }

  private McpToolsCapability.CallToolResponse callTool(
      McpToolsCapability toolsCapability, JsonNode params) {
    JsonNode argsNode = params.path("arguments");
    ObjectNode arguments =
        argsNode.isObject() ? (ObjectNode) argsNode : objectMapper.createObjectNode();
    return toolsCapability.callTool(params.path("name").asString(), arguments);
  }

  // --- Notification / response handling ---

  private static final String SESSION_ID_REQUIRED = "MCP-Session-Id header is required";

  private ResponseEntity<Object> handleNotificationOrResponse(
      JsonNode requestBody, String sessionId, boolean isNotification) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of(ERROR_KEY, SESSION_ID_REQUIRED));
    }

    if (isNotification) {
      String method = requestBody.get("method").asString();
      log.debug("Received notification: {}", method);

      if ("notifications/initialized".equals(method)) {
        sessionManager.getSession(sessionId).ifPresent(_ -> mcpServer.clientInitialized());
      }
    } else {
      log.debug("Received JSON-RPC response, acknowledging with 202");
    }

    return ResponseEntity.accepted().build();
  }

  // --- Validation helpers ---

  private ResponseEntity<Object> validateOrigin(String origin) {
    if (origin != null && !isValidOrigin(origin)) {
      log.warn("Invalid origin: {}", origin);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERROR_KEY, "Invalid origin"));
    }
    return null;
  }

  private ObjectNode createErrorResponse(JsonNode id, int code, String message) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put(JSONRPC_FIELD, JSONRPC_VERSION);
    if (id != null) {
      response.set("id", id);
    }

    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    response.set(ERROR_KEY, error);

    return response;
  }

  private boolean isValidProtocolVersion(String version) {
    return McpServer.PROTOCOL_VERSION.equals(version)
        || "2025-06-18".equals(version)
        || "2025-03-26".equals(version)
        || "2024-11-05".equals(version);
  }

  private boolean isValidOrigin(String origin) {
    try {
      String host = URI.create(origin).getHost();
      return host != null && allowedOrigins.contains(host);
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private boolean isValidPostAcceptHeader(String acceptHeader) {
    if (acceptHeader == null) {
      return false;
    }
    List<MediaType> mediaTypes = MediaType.parseMediaTypes(acceptHeader);
    boolean hasJson = false;
    boolean hasSse = false;
    for (MediaType mt : mediaTypes) {
      if (mt.includes(MediaType.APPLICATION_JSON)) {
        hasJson = true;
      }
      if (mt.includes(MediaType.TEXT_EVENT_STREAM)) {
        hasSse = true;
      }
    }
    return hasJson && hasSse;
  }

  private boolean isValidGetAcceptHeader(String acceptHeader) {
    if (acceptHeader == null) {
      return false;
    }
    return MediaType.parseMediaTypes(acceptHeader).stream()
        .anyMatch(mt -> mt.includes(MediaType.TEXT_EVENT_STREAM));
  }
}
