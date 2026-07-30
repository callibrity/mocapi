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

import com.callibrity.ripcurl.core.JsonRpcMessage;

/**
 * The seam a transport implements to deliver a server's JSON-RPC output — responses and
 * request-scoped notifications — back to the client. mocapi ships two implementations (Streamable
 * HTTP and stdio); authoring a custom transport is a rare, advanced extension.
 *
 * <p><b>This SPI intentionally speaks ripcurl's {@link JsonRpcMessage}.</b> ripcurl is mocapi's
 * JSON-RPC engine (message model, dispatcher, and {@code @JsonRpcMethod} routing), and a
 * transport's whole job is to move JSON-RPC messages — so the ripcurl message type is the natural,
 * deliberate currency here. This is the one place a ripcurl type surfaces in a mocapi SPI; the
 * user-facing handler API ({@code mocapi-api}) is ripcurl-free. See ADR-0020 for the stateless
 * {@link McpServer}&harr;{@code McpTransport} contract.
 */
public interface McpTransport {

  /**
   * The transport bound to the in-flight request, for handlers and interceptors that send
   * request-scoped notifications (e.g. progress) while the call is executing.
   */
  ScopedValue<McpTransport> CURRENT = ScopedValue.newInstance();

  /**
   * Delivers a JSON-RPC message — a response or a notification — to the client over this transport.
   *
   * @param message the ripcurl JSON-RPC message to send
   */
  void send(JsonRpcMessage message);
}
