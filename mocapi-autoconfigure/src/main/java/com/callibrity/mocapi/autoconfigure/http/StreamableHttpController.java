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
package com.callibrity.mocapi.autoconfigure.http;

import com.callibrity.mocapi.autoconfigure.stream.DefaultMcpStreamContext;
import com.callibrity.mocapi.autoconfigure.stream.McpStreamContextParamResolver;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionStore;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
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
  private final McpSessionStore sessionStore;
  private final OdysseyStreamRegistry registry;
  private final ObjectMapper objectMapper;
  private final McpStreamContextParamResolver streamContextResolver;
  private final Duration sessionTimeout;
  private final MailboxFactory mailboxFactory;
  private final SchemaGenerator schemaGenerator;
  private final Duration elicitationTimeout;

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

    // Validate jsonrpc version
    if (!"2.0".equals(body.path("jsonrpc").asString(null))) {
      return ResponseEntity.badRequest()
          .body(errorResponse(body.get("id"), -32600, "jsonrpc must be \"2.0\""));
    }

    String method = body.path("method").asString(null);
    JsonNode id = body.get("id");

    // Notification: has method, no id
    if (method != null && id == null) {
      return handleNotification(method, sessionId);
    }

    // Response: no method, has result or error (elicitation answer)
    if (method == null && (body.has("result") || body.has("error"))) {
      return handleResponse(body, sessionId);
    }

    // Regular request — validate MCP headers
    if (method == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(errorResponse(id, -32600, "Missing method field"));
    }

    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(errorResponse(id, -32600, "Invalid MCP-Protocol-Version: " + version));
    }
    if (!validator.isValidOrigin(origin)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(errorResponse(id, -32600, "Invalid origin"));
    }

    boolean isInitialize = INITIALIZE.equals(method);
    McpSession session = null;
    if (!isInitialize) {
      if (sessionId == null) {
        return ResponseEntity.badRequest().body(errorResponse(id, -32600, SESSION_REQUIRED));
      }
      var sessionOpt = sessionStore.find(sessionId);
      if (sessionOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(MediaType.APPLICATION_JSON)
            .body(errorResponse(id, -32600, "Session not found or expired"));
      }
      session = sessionOpt.get();
      sessionStore.touch(sessionId, sessionTimeout);
    }

    return dispatch(isInitialize, method, body, session);
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
    if (sessionStore.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Session not found or expired"));
    }
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Invalid MCP-Protocol-Version: " + version));
    }

    sessionStore.touch(sessionId, sessionTimeout);
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
    if (sessionStore.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(Map.of("error", "Session not found or expired"));
    }
    sessionStore.delete(sessionId);
    registry.channel(sessionId).delete();
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<Object> dispatch(
      boolean isInitialize, String method, JsonNode body, McpSession session) {
    JsonNode params = body.get("params");
    JsonNode id = body.get("id");
    JsonRpcRequest request = new JsonRpcRequest("2.0", method, params, id);
    OdysseyStream stream = registry.ephemeral();
    String progressToken = extractProgressToken(params);
    DefaultMcpStreamContext ctx =
        new DefaultMcpStreamContext(
            stream,
            objectMapper,
            progressToken,
            mailboxFactory,
            schemaGenerator,
            session,
            elicitationTimeout);
    try {
      streamContextResolver.set(ctx);
      JsonRpcResponse response = dispatcher.dispatch(request);

      if (streamContextResolver.wasResolved()) {
        stream.publishJson(Map.of());
        SseEmitter emitter = stream.subscribe();
        stream.publishJson(response);
        stream.close();
        var builder = ResponseEntity.ok();
        if (isInitialize) {
          builder.header("MCP-Session-Id", createSessionFromParams(params));
        }
        return builder.body(emitter);
      }

      stream.close();
      var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
      if (isInitialize) {
        builder.header("MCP-Session-Id", createSessionFromParams(params));
      }
      return builder.body(response);
    } catch (JsonRpcException e) {
      stream.close();
      log.warn("JSON-RPC error processing {}: {}", method, e.getMessage());
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(errorResponse(id, e.getCode(), e.getMessage()));
    } finally {
      streamContextResolver.clear();
    }
  }

  private ResponseEntity<Object> handleNotification(String method, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("error", SESSION_REQUIRED));
    }
    if ("notifications/initialized".equals(method)) {
      sessionStore
          .find(sessionId)
          .ifPresent(_ -> dispatcher.dispatch(new JsonRpcRequest("2.0", method, null, null)));
    }
    return ResponseEntity.accepted().build();
  }

  private ResponseEntity<Object> handleResponse(JsonNode body, String sessionId) {
    if (sessionId == null) {
      return ResponseEntity.badRequest()
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("error", SESSION_REQUIRED));
    }
    JsonNode idNode = body.get("id");
    if (idNode != null && !idNode.isNull()) {
      String id = idNode.asString();
      JsonNode resultNode = body.get("result");
      JsonNode errorNode = body.get("error");
      Mailbox<JsonNode> mailbox = mailboxFactory.create("elicit:" + id, JsonNode.class);
      if (errorNode != null) {
        ObjectNode errorWrapper = objectMapper.createObjectNode();
        errorWrapper.set("error", errorNode);
        mailbox.deliver(errorWrapper);
      } else if (resultNode != null) {
        mailbox.deliver(resultNode);
      }
    }
    return ResponseEntity.accepted().build();
  }

  private static String extractProgressToken(JsonNode params) {
    if (params == null) return null;
    JsonNode meta = params.get("_meta");
    if (meta == null) return null;
    JsonNode token = meta.get("progressToken");
    return (token == null || token.isMissingNode()) ? null : token.asString();
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

  private ObjectNode errorResponse(JsonNode id, int code, String message) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", "2.0");
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
