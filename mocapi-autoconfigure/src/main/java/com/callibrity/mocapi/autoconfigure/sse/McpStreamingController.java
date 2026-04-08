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
import com.callibrity.mocapi.server.McpRequestValidator;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpSession;
import com.callibrity.mocapi.server.McpSessionStore;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
public class McpStreamingController {

  private static final String INITIALIZE = "initialize";
  private static final String ERR = "error";
  private static final String NO_SESSION = "Session not found or expired";
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

  public McpStreamingController(
      JsonRpcDispatcher dispatcher,
      McpRequestValidator validator,
      McpSessionStore sessionStore,
      OdysseyStreamRegistry registry,
      ObjectMapper objectMapper,
      McpStreamContextParamResolver streamContextResolver,
      Duration sessionTimeout,
      MailboxFactory mailboxFactory,
      SchemaGenerator schemaGenerator,
      Duration elicitationTimeout) {
    this.dispatcher = dispatcher;
    this.validator = validator;
    this.sessionStore = sessionStore;
    this.registry = registry;
    this.objectMapper = objectMapper;
    this.streamContextResolver = streamContextResolver;
    this.sessionTimeout = sessionTimeout;
    this.mailboxFactory = mailboxFactory;
    this.schemaGenerator = schemaGenerator;
    this.elicitationTimeout = elicitationTimeout;
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

    var envelope = validator.validateJsonRpcEnvelope(body);
    if (!envelope.valid()) {
      return badRequest(
          errorResponse(body.get("id"), envelope.errorCode(), envelope.errorMessage()));
    }

    if (McpRequestValidator.isNotificationOrResponse(body)) {
      return handleNotificationOrResponse(body, sessionId);
    }

    JsonNode idNode = body.get("id");
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return badRequest(errorResponse(idNode, -32600, "Invalid MCP-Protocol-Version: " + version));
    }
    if (!validator.isValidOrigin(origin)) {
      return forbidden(errorResponse(idNode, -32600, "Invalid origin"));
    }

    String method = body.get("method").asString();
    boolean isInitialize = INITIALIZE.equals(method);

    if (!isInitialize && sessionId == null) {
      return badRequest(errorResponse(idNode, -32600, SESSION_REQUIRED));
    }

    McpSession session = null;
    if (!isInitialize) {
      var sessionOpt = sessionStore.find(sessionId);
      if (sessionOpt.isEmpty()) {
        return notFound(errorResponse(idNode, -32600, NO_SESSION));
      }
      session = sessionOpt.get();
      sessionStore.touch(sessionId, sessionTimeout);
    }

    return dispatch(isInitialize, method, body.get("params"), idNode, session);
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
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(ERR, "Invalid origin"));
    }
    if (sessionId == null) {
      return ResponseEntity.badRequest().body(Map.of(ERR, SESSION_REQUIRED));
    }
    if (sessionStore.find(sessionId).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(ERR, NO_SESSION));
    }
    String version = protocolVersion != null ? protocolVersion : McpServer.PROTOCOL_VERSION;
    if (!validator.isValidProtocolVersion(version)) {
      return ResponseEntity.badRequest()
          .body(Map.of(ERR, "Invalid MCP-Protocol-Version: " + version));
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
      boolean isInitialize, String method, JsonNode params, JsonNode id, McpSession session) {
    JsonRpcRequest request = new JsonRpcRequest("2.0", method, params, id);
    OdysseyStream stream = registry.ephemeral();
    String progressToken = extractProgressToken(params);
    DefaultMcpStreamContext streamContext =
        new DefaultMcpStreamContext(
            stream,
            objectMapper,
            progressToken,
            mailboxFactory,
            schemaGenerator,
            session,
            elicitationTimeout);
    try {
      streamContextResolver.set(streamContext);
      JsonRpcResponse response = dispatcher.dispatch(request);

      if (streamContextResolver.wasResolved()) {
        stream.publishJson(Map.of());
        SseEmitter emitter = stream.subscribe();
        stream.publishJson(successResponse(response));
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
      return builder.body(successResponse(response));
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

  private static String extractProgressToken(JsonNode params) {
    if (params == null) {
      return null;
    }
    JsonNode meta = params.get("_meta");
    if (meta == null) {
      return null;
    }
    JsonNode token = meta.get("progressToken");
    if (token == null || token.isMissingNode()) {
      return null;
    }
    return token.asString();
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
      // This is a notification
      String method = methodNode.asString();
      if ("notifications/initialized".equals(method)) {
        sessionStore
            .find(sessionId)
            .ifPresent(
                _ -> {
                  JsonRpcRequest request = new JsonRpcRequest("2.0", method, null, null);
                  dispatcher.dispatch(request);
                });
      }
    } else {
      // This is a JSON-RPC response — route to the corresponding mailbox
      routeResponseToMailbox(body);
    }
    return ResponseEntity.accepted().build();
  }

  private void routeResponseToMailbox(JsonNode body) {
    JsonNode idNode = body.get("id");
    if (idNode == null || idNode.isNull()) {
      return;
    }
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

  // --- Response formatting ---

  private ObjectNode successResponse(JsonRpcResponse response) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("jsonrpc", "2.0");
    if (response.id() != null) {
      node.set("id", response.id());
    }
    if (response.result() != null) {
      node.set("result", response.result());
    }
    return node;
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

  // --- Accept header helpers ---

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
