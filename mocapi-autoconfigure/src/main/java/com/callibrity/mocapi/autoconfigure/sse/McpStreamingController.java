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
import com.callibrity.mocapi.tools.McpToolsCapability;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * MCP Streaming Controller implementing the MCP 2025-11-25 Streamable HTTP transport.
 *
 * <p>Handles both POST (client requests) and GET (server notifications) with SSE streaming.
 * Implements session management, event ID tracking, and stream resumability per the MCP spec.
 */
@Slf4j
@RestController
@RequestMapping("${mocapi.endpoint:/mcp}")
@RequiredArgsConstructor
public class McpStreamingController {

  // ------------------------------ FIELDS ------------------------------

  private final McpServer mcpServer;
  private final McpSessionManager sessionManager;
  private final TaskExecutor taskExecutor;
  private final ObjectMapper objectMapper;
  private final List<String> allowedOrigins;

  @Autowired(required = false)
  private McpToolsCapability toolsCapability;

  private static final String DEFAULT_PROTOCOL_VERSION = McpServer.PROTOCOL_VERSION;

  // -------------------------- OTHER METHODS --------------------------

  @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<?> handlePost(
      @RequestBody JsonNode requestBody,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Accept", required = false) String acceptHeader,
      @RequestHeader(value = "Origin", required = false) String origin) {

    // Gap 5: Validate Accept header — must include both application/json and text/event-stream
    if (!isValidPostAcceptHeader(acceptHeader)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    // Validate JSON-RPC envelope
    JsonNode jsonrpcNode = requestBody.path("jsonrpc");
    if (!"2.0".equals(jsonrpcNode.asText(null))) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse(requestBody.get("id"), -32600, "jsonrpc must be \"2.0\""));
    }

    JsonNode idNode = requestBody.get("id");

    // Gap 1: Detect JSON-RPC notifications and responses early
    JsonNode methodNode = requestBody.get("method");
    boolean isNotification = methodNode != null && idNode == null;
    boolean isResponse =
        methodNode == null && (requestBody.has("result") || requestBody.has("error"));

    if (isNotification || isResponse) {
      return handleNotificationOrResponse(requestBody, sessionId, isNotification);
    }

    // From here on, this is a JSON-RPC request (has method and id)
    if (methodNode == null || methodNode.isMissingNode() || methodNode.asText("").isEmpty()) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse(idNode, -32600, "method is required"));
    }

    if (idNode != null && !idNode.isNull() && !idNode.isTextual() && !idNode.isNumber()) {
      return ResponseEntity.badRequest()
          .body(createErrorResponse(null, -32600, "id must be a string, number, or null"));
    }

    String method = methodNode.asText();
    JsonNode params = requestBody.get("params");

    // Validate protocol version
    String version = protocolVersion != null ? protocolVersion : DEFAULT_PROTOCOL_VERSION;
    if (!isValidProtocolVersion(version)) {
      log.warn("Invalid protocol version: {}", version);
      return ResponseEntity.badRequest()
          .body(createErrorResponse(idNode, -32600, "Invalid MCP-Protocol-Version: " + version));
    }

    // Validate Origin header
    if (origin != null && !isValidOrigin(origin)) {
      log.warn("Invalid origin: {}", origin);
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(createErrorResponse(idNode, -32600, "Invalid origin"));
    }

    // Get or create session
    McpSession session;
    boolean isInitialize = "initialize".equals(method);

    if (isInitialize) {
      session = sessionManager.createSession();
      log.info("Created new session {} for initialize request", session.getSessionId());
    } else if (sessionId != null) {
      session = sessionManager.getSession(sessionId).orElse(null);
      if (session == null) {
        log.warn("Session not found or expired: {}", sessionId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse(idNode, -32600, "Session not found or expired"));
      }
    } else {
      session = sessionManager.createSession();
      log.debug("Created anonymous session {} for request {}", session.getSessionId(), method);
    }

    // Create stream emitter
    McpStreamEmitter streamEmitter = new McpStreamEmitter(session);

    // Send priming event per MCP spec
    streamEmitter.sendPrimingEvent();

    // Process request asynchronously
    taskExecutor.execute(
        () -> {
          try {
            Object result = invokeMethod(method, params);

            // Build JSON-RPC response
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (idNode != null) {
              response.set("id", idNode);
            }
            if (result != null) {
              response.set("result", objectMapper.valueToTree(result));
            }

            streamEmitter.sendAndComplete(response);
          } catch (IllegalArgumentException e) {
            log.warn("Method not found: {}", method);
            ObjectNode errorResponse =
                createErrorResponse(idNode, -32601, "Method not found: " + method);
            streamEmitter.sendAndComplete(errorResponse);
          } catch (Exception e) {
            log.error("Error processing JSON-RPC request", e);
            ObjectNode errorResponse = createErrorResponse(idNode, -32603, "Internal error");
            streamEmitter.sendAndComplete(errorResponse);
          }
        });

    // Build response with session ID header for initialize
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (isInitialize) {
      builder.header("MCP-Session-Id", session.getSessionId());
    }

    return builder.body(streamEmitter.getEmitter());
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<?> handleGet(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestHeader(value = "Accept", required = false) String acceptHeader) {

    // Gap 5: Validate Accept header — must include text/event-stream
    if (!isValidGetAcceptHeader(acceptHeader)) {
      return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    if (sessionId == null) {
      log.warn("GET request missing MCP-Session-Id header");
      return ResponseEntity.badRequest().body(Map.of("error", "MCP-Session-Id header is required"));
    }

    McpSession session = sessionManager.getSession(sessionId).orElse(null);
    if (session == null) {
      log.warn("GET request with unknown session: {}", sessionId);
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Session not found or expired"));
    }

    String version = protocolVersion != null ? protocolVersion : DEFAULT_PROTOCOL_VERSION;
    if (!isValidProtocolVersion(version)) {
      log.warn("Invalid protocol version on GET: {}", version);
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Invalid MCP-Protocol-Version: " + version));
    }

    McpStreamEmitter streamEmitter = new McpStreamEmitter(session);
    streamEmitter.sendPrimingEvent();

    // Gap 4: Replay stored events from the *original* stream identified by Last-Event-ID
    if (lastEventId != null) {
      var replayEvents = session.getEventsAfter(lastEventId);
      if (replayEvents != null) {
        for (SseEvent event : replayEvents) {
          streamEmitter.send(event.data());
        }
      }
    }

    // Register the emitter for server-initiated notifications
    session.registerNotificationEmitter(streamEmitter);

    return ResponseEntity.ok().body(streamEmitter.getEmitter());
  }

  // Gap 2: DELETE endpoint for session termination
  @DeleteMapping
  public ResponseEntity<?> handleDelete(
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId) {

    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of("error", "MCP-Session-Id header is required"));
    }

    if (!sessionManager.terminateSession(sessionId)) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Session not found or expired"));
    }

    return ResponseEntity.noContent().build();
  }

  /** Invokes the appropriate MCP method based on the method name. */
  private Object invokeMethod(String method, JsonNode params) throws Exception {
    return switch (method) {
      case "initialize" ->
          mcpServer.initialize(
              params.path("protocolVersion").asText(),
              objectMapper.treeToValue(
                  params.get("capabilities"),
                  com.callibrity.mocapi.client.ClientCapabilities.class),
              objectMapper.treeToValue(
                  params.get("clientInfo"), com.callibrity.mocapi.client.ClientInfo.class));
      case "ping" -> mcpServer.ping();
      case "notifications/initialized" -> {
        mcpServer.clientInitialized();
        yield null;
      }
      case "tools/list" ->
          Optional.ofNullable(toolsCapability)
              .map(cap -> cap.listTools(params != null ? params.path("cursor").asText(null) : null))
              .orElseThrow(() -> new IllegalArgumentException("Tools capability not available"));
      case "tools/call" -> {
        JsonNode argsNode = params.path("arguments");
        ObjectNode arguments =
            argsNode.isObject() ? (ObjectNode) argsNode : objectMapper.createObjectNode();
        yield Optional.ofNullable(toolsCapability)
            .map(cap -> cap.callTool(params.path("name").asText(), arguments))
            .orElseThrow(() -> new IllegalArgumentException("Tools capability not available"));
      }
      default -> throw new IllegalArgumentException("Unknown method: " + method);
    };
  }

  private ObjectNode createErrorResponse(JsonNode id, int code, String message) {
    ObjectNode response = objectMapper.createObjectNode();
    response.put("jsonrpc", "2.0");
    if (id != null) {
      response.set("id", id);
    }

    ObjectNode error = objectMapper.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    response.set("error", error);

    return response;
  }

  private boolean isValidProtocolVersion(String version) {
    return McpServer.PROTOCOL_VERSION.equals(version)
        || "2025-06-18".equals(version)
        || "2025-03-26".equals(version)
        || "2024-11-05".equals(version);
  }

  private boolean isValidOrigin(String origin) {
    if (origin == null) {
      return true;
    }
    try {
      String host = URI.create(origin).getHost();
      if (host == null) {
        return false;
      }
      return allowedOrigins.contains(host);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Handles JSON-RPC notifications and responses by invoking the handler (for notifications) and
   * returning 202 Accepted with no body.
   */
  private ResponseEntity<?> handleNotificationOrResponse(
      JsonNode requestBody, String sessionId, boolean isNotification) {
    if (isNotification) {
      String method = requestBody.get("method").asText();
      log.debug("Received notification: {}", method);

      if ("notifications/initialized".equals(method)) {
        if (sessionId != null) {
          sessionManager.getSession(sessionId).ifPresent(s -> mcpServer.clientInitialized());
        }
      }
    } else {
      log.debug("Received JSON-RPC response, acknowledging with 202");
    }

    return ResponseEntity.accepted().build();
  }

  /**
   * Validates the Accept header for POST requests. Must include both application/json and
   * text/event-stream, or *&#47;*.
   */
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

  /** Validates the Accept header for GET requests. Must include text/event-stream or *&#47;*. */
  private boolean isValidGetAcceptHeader(String acceptHeader) {
    if (acceptHeader == null) {
      return false;
    }
    List<MediaType> mediaTypes = MediaType.parseMediaTypes(acceptHeader);
    for (MediaType mt : mediaTypes) {
      if (mt.includes(MediaType.TEXT_EVENT_STREAM)) {
        return true;
      }
    }
    return false;
  }
}
