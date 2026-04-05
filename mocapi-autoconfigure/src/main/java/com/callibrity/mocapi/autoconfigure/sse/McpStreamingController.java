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

import com.callibrity.mocapi.prompts.McpPromptsCapability;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.tools.McpToolsCapability;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

  @Autowired(required = false)
  private McpToolsCapability toolsCapability;

  @Autowired(required = false)
  private McpPromptsCapability promptsCapability;

  private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";

  // -------------------------- OTHER METHODS --------------------------

  @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<?> handlePost(
      @RequestBody JsonNode requestBody,
      @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
      @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      @RequestHeader(value = "Origin", required = false) String origin) {

    // Parse JSON-RPC request
    JsonNode idNode = requestBody.get("id");
    String method = requestBody.path("method").asText();
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
            ObjectNode errorResponse =
                createErrorResponse(idNode, -32603, "Internal error: " + e.getMessage());
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
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {

    log.debug("GET request received, but server-initiated notifications not yet implemented");
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(Map.of("error", "Server-initiated notifications not yet supported"));
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
      case "tools/call" ->
          Optional.ofNullable(toolsCapability)
              .map(
                  cap ->
                      cap.callTool(
                          params.path("name").asText(), (ObjectNode) params.get("arguments")))
              .orElseThrow(() -> new IllegalArgumentException("Tools capability not available"));
      case "prompts/list" ->
          Optional.ofNullable(promptsCapability)
              .map(
                  cap ->
                      cap.listPrompts(params != null ? params.path("cursor").asText(null) : null))
              .orElseThrow(() -> new IllegalArgumentException("Prompts capability not available"));
      case "prompts/get" ->
          Optional.ofNullable(promptsCapability)
              .map(
                  cap ->
                      cap.getPrompt(
                          params.path("name").asText(),
                          objectMapper.convertValue(params.get("arguments"), Map.class)))
              .orElseThrow(() -> new IllegalArgumentException("Prompts capability not available"));
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
    return origin.contains("localhost") || origin.contains("127.0.0.1") || origin.contains("[::1]");
  }
}
