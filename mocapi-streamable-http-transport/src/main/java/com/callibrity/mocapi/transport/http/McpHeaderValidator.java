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
package com.callibrity.mocapi.transport.http;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/**
 * Validates the MCP 2026-07-28 routing headers against the request body, before dispatch. The
 * Streamable HTTP transport spec requires every POST to carry headers that mirror the body so
 * intermediaries can route without parsing JSON; the server must reject any disagreement with the
 * transport-prose {@code HeaderMismatch} error ({@code -32020}, HTTP 400). The constant lives here
 * — not in {@code mocapi-model} — because mocapi sources it from the transport spec; the value now
 * also appears in {@code schema.ts} as {@code HEADER_MISMATCH}.
 *
 * <p>Rules:
 *
 * <ul>
 *   <li>{@code MCP-Protocol-Version} is required on every request and notification, and must equal
 *       the body's {@code _meta} {@code io.modelcontextprotocol/protocolVersion} when that value is
 *       present.
 *   <li>{@code Mcp-Method} is required on every request and notification, and must equal the body's
 *       {@code method}.
 *   <li>{@code Mcp-Name} is required on {@code tools/call} / {@code prompts/get} (must equal {@code
 *       params.name}) and {@code resources/read} (must equal {@code params.uri}). It is not
 *       expected on any other method; a stray {@code Mcp-Name} elsewhere is ignored, per RFC 9110's
 *       treatment of unrecognized fields.
 * </ul>
 *
 * <p>When the body lacks the comparable value (missing {@code _meta} envelope, missing {@code
 * params.name}), header validation does not fail on its own: the body-side failure belongs to the
 * server's envelope/params validation ({@code -32602}), which runs after this check. A request that
 * fails both gets the transport's {@code -32020} first only when a header itself is missing or
 * contradicts a value the body does carry.
 *
 * <p>Header name lookups are case-insensitive ({@link HttpHeaders} semantics).
 */
public class McpHeaderValidator {

  private static final String MISSING_REQUIRED_HEADER = "missing required header ";

  /** The required protocol-version routing header. */
  public static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";

  /** The required method routing header. */
  public static final String MCP_METHOD_HEADER = "Mcp-Method";

  /** The name/uri routing header, required only on the three named methods. */
  public static final String MCP_NAME_HEADER = "Mcp-Name";

  /**
   * The spec's {@code HeaderMismatch} JSON-RPC error code (Streamable HTTP transport prose).
   * Deliberately a transport-side constant: it is not part of {@code schema.ts}, so it does not
   * belong in {@code mocapi-model}.
   */
  public static final int HEADER_MISMATCH_CODE = -32020;

  /** The spec's name for the {@code -32020} error. */
  public static final String HEADER_MISMATCH_NAME = "HeaderMismatch";

  private static final String META_FIELD = "_meta";

  /** Which params field {@code Mcp-Name} mirrors, per method. */
  private static final Map<String, String> NAMED_PARAM_FIELDS =
      Map.of(
          McpMethods.TOOLS_CALL, "name",
          McpMethods.PROMPTS_GET, "name",
          McpMethods.RESOURCES_READ, "uri");

  /**
   * Validates the routing headers against the request body.
   *
   * @param headers the HTTP request headers
   * @param request the parsed JSON-RPC request (call or notification)
   * @return a {@code HeaderMismatch}-prefixed failure message, or empty when the headers pass
   */
  public Optional<String> validate(HttpHeaders headers, JsonRpcRequest request) {
    String version = headers.getFirst(MCP_PROTOCOL_VERSION_HEADER);
    if (version == null) {
      return failure(MISSING_REQUIRED_HEADER + MCP_PROTOCOL_VERSION_HEADER);
    }
    String bodyVersion = metaProtocolVersion(request.params());
    if (bodyVersion != null && !bodyVersion.equals(version)) {
      return failure(
          MCP_PROTOCOL_VERSION_HEADER
              + " header '%s' does not match the body's %s '%s'"
                  .formatted(version, McpMetaKeys.PROTOCOL_VERSION, bodyVersion));
    }

    String method = headers.getFirst(MCP_METHOD_HEADER);
    if (method == null) {
      return failure(MISSING_REQUIRED_HEADER + MCP_METHOD_HEADER);
    }
    if (!request.method().equals(method)) {
      return failure(
          MCP_METHOD_HEADER
              + " header '%s' does not match the body's method '%s'"
                  .formatted(method, request.method()));
    }

    String namedField = NAMED_PARAM_FIELDS.get(request.method());
    if (namedField != null) {
      String name = headers.getFirst(MCP_NAME_HEADER);
      if (name == null) {
        return failure(
            MISSING_REQUIRED_HEADER + MCP_NAME_HEADER + " for method " + request.method());
      }
      String bodyName = stringField(request.params(), namedField);
      if (bodyName != null && !bodyName.equals(name)) {
        return failure(
            MCP_NAME_HEADER
                + " header '%s' does not match the body's params.%s '%s'"
                    .formatted(name, namedField, bodyName));
      }
    }
    return Optional.empty();
  }

  private static Optional<String> failure(String detail) {
    return Optional.of(HEADER_MISMATCH_NAME + ": " + detail);
  }

  private static String metaProtocolVersion(JsonNode params) {
    if (params == null || !params.isObject()) {
      return null;
    }
    JsonNode meta = params.get(META_FIELD);
    if (meta == null || !meta.isObject()) {
      return null;
    }
    JsonNode version = meta.get(McpMetaKeys.PROTOCOL_VERSION);
    return version != null && version.isString() ? version.asString() : null;
  }

  private static String stringField(JsonNode params, String field) {
    if (params == null || !params.isObject()) {
      return null;
    }
    JsonNode value = params.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }
}
