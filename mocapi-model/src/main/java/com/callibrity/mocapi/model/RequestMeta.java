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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.node.ValueNode;

/**
 * The spec's {@code RequestMetaObject}: the per-request {@code _meta} envelope. The three {@code
 * io.modelcontextprotocol/*} fields are required on every client request (schema.json); mocapi
 * models them as nullable components and enforces presence at dispatch time, where a missing field
 * maps to JSON-RPC {@code -32602}. The deprecated {@code io.modelcontextprotocol/logLevel} key is
 * deliberately not modeled (logging not implemented, ADR-0022).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestMeta(
    ValueNode progressToken,
    @JsonProperty(McpMetaKeys.PROTOCOL_VERSION) String protocolVersion,
    @JsonProperty(McpMetaKeys.CLIENT_INFO) Implementation clientInfo,
    @JsonProperty(McpMetaKeys.CLIENT_CAPABILITIES) ClientCapabilities clientCapabilities) {}
