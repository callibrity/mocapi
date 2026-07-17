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
package com.callibrity.mocapi.server.exchange;

import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Parses and validates the per-request {@code _meta} envelope (the spec's {@code
 * RequestMetaObject}) into an {@link McpExchange}.
 *
 * <p>Failure modes, per spec {@code basic/index#meta} and ADR-0020:
 *
 * <ul>
 *   <li>missing/malformed envelope, or a missing/malformed required key — JSON-RPC {@code -32602}
 *       Invalid params ({@link JsonRpcException});
 *   <li>a well-formed envelope carrying an unsupported protocol version — {@link
 *       UnsupportedProtocolVersionException} ({@code -32022} on the wire, with the supported-list
 *       data that serves as the version bootstrap).
 * </ul>
 *
 * <p>The two cases are deliberately not conflated: the version check runs only after the envelope
 * has proven well-formed.
 */
public class MetaEnvelopeParser {

  private static final String META_FIELD = "_meta";

  // DRAFT_PROTOCOL_VERSION is an RC-window alias only — remove at the RC→final re-verification
  // (migration plan Task 9.3).
  private static final List<String> SUPPORTED_VERSIONS =
      List.of(McpServer.PROTOCOL_VERSION, McpServer.DRAFT_PROTOCOL_VERSION);

  private final ObjectMapper objectMapper;

  public MetaEnvelopeParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Parses the {@code _meta} envelope out of a request's {@code params} node.
   *
   * @param params the JSON-RPC {@code params} node (may be {@code null} or a non-object — both are
   *     invalid, since every request must carry the envelope)
   * @return the validated exchange
   * @throws JsonRpcException with {@code -32602} when the envelope is missing or malformed
   * @throws UnsupportedProtocolVersionException when the envelope is well-formed but the protocol
   *     version is not supported
   */
  public McpExchange parse(JsonNode params) {
    if (params == null || !params.isObject()) {
      throw invalidParams("Missing required _meta envelope: request has no params object");
    }
    JsonNode meta = params.get(META_FIELD);
    if (meta == null || !meta.isObject()) {
      throw invalidParams("Missing required _meta envelope on request params");
    }

    String protocolVersion = requiredString(meta, McpMetaKeys.PROTOCOL_VERSION);
    Implementation clientInfo = requiredObject(meta, McpMetaKeys.CLIENT_INFO, Implementation.class);
    if (clientInfo.name() == null || clientInfo.version() == null) {
      throw invalidParams(
          "Malformed _meta key " + McpMetaKeys.CLIENT_INFO + ": name and version are required");
    }
    ClientCapabilities clientCapabilities =
        requiredObject(meta, McpMetaKeys.CLIENT_CAPABILITIES, ClientCapabilities.class);

    if (!SUPPORTED_VERSIONS.contains(protocolVersion)) {
      throw new UnsupportedProtocolVersionException(SUPPORTED_VERSIONS, protocolVersion);
    }
    return new McpExchange(protocolVersion, clientInfo, clientCapabilities, traceContext(meta));
  }

  /**
   * Reads the optional, unprefixed W3C trace-context keys. They are observability hints, not
   * protocol requirements, so a non-string value is treated as absent rather than rejected — a
   * malformed telemetry hint must never fail the request.
   */
  private static TraceContext traceContext(JsonNode meta) {
    String traceparent = optionalString(meta, McpMetaKeys.TRACEPARENT);
    if (traceparent == null) {
      return TraceContext.NONE;
    }
    return new TraceContext(
        traceparent,
        optionalString(meta, McpMetaKeys.TRACESTATE),
        optionalString(meta, McpMetaKeys.BAGGAGE));
  }

  private static String optionalString(JsonNode meta, String key) {
    JsonNode node = meta.get(key);
    return node != null && node.isString() ? node.asString() : null;
  }

  private String requiredString(JsonNode meta, String key) {
    JsonNode node = meta.get(key);
    if (node == null) {
      throw invalidParams("Missing required _meta key: " + key);
    }
    if (!node.isString()) {
      throw invalidParams("Malformed _meta key " + key + ": expected a string");
    }
    return node.asString();
  }

  private <T> T requiredObject(JsonNode meta, String key, Class<T> type) {
    JsonNode node = meta.get(key);
    if (node == null) {
      throw invalidParams("Missing required _meta key: " + key);
    }
    if (!node.isObject()) {
      throw invalidParams("Malformed _meta key " + key + ": expected an object");
    }
    try {
      return objectMapper.treeToValue(node, type);
    } catch (JacksonException e) {
      throw new JsonRpcException(
          JsonRpcProtocol.INVALID_PARAMS, "Malformed _meta key " + key + ": " + e.getMessage(), e);
    }
  }

  private static JsonRpcException invalidParams(String message) {
    return new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, message);
  }
}
