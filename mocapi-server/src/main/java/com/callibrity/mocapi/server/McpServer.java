/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server;

import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcNotification;

/**
 * Stateless MCP server entry point (ADR-0019, ADR-0020). Every request is self-contained: the
 * client's protocol version, identity, and capabilities arrive in the request's {@code _meta}
 * envelope. There is no handshake, no session, and no server-initiated request channel.
 */
public interface McpServer {

  /**
   * The single MCP protocol version this server implements. There is no negotiation: a request
   * carrying any other {@code io.modelcontextprotocol/protocolVersion} is rejected with the spec's
   * {@code UnsupportedProtocolVersionError} (ADR-0019).
   */
  String PROTOCOL_VERSION = "2026-07-28";

  /**
   * Handles a single JSON-RPC call: parses and validates the {@code _meta} envelope, dispatches the
   * call with the resulting exchange in scope, and sends the response (or envelope error) through
   * the transport.
   *
   * @param call the JSON-RPC call
   * @param transport the transport to send responses and request-scoped notifications through
   */
  void handleCall(JsonRpcCall call, McpTransport transport);

  /**
   * Handles a client notification (e.g. {@code notifications/cancelled}). Notifications do not
   * carry the request {@code _meta} envelope and produce no response.
   *
   * @param notification the JSON-RPC notification
   */
  void handleNotification(JsonRpcNotification notification);
}
