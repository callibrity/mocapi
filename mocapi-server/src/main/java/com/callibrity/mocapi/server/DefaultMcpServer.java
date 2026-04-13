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
package com.callibrity.mocapi.server;

import static com.callibrity.mocapi.model.McpMethods.INITIALIZE;
import static com.callibrity.mocapi.model.McpMethods.NOTIFICATIONS_INITIALIZED;
import static com.callibrity.mocapi.model.McpMethods.PING;

import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.util.Set;

/** Default {@link McpServer} that handles session lifecycle and JSON-RPC dispatch. */
public class DefaultMcpServer implements McpServer {

  private static final Set<String> KNOWN_PROTOCOL_VERSIONS =
      Set.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05", "2024-10-07");

  private final McpSessionService sessionService;
  private final JsonRpcDispatcher dispatcher;
  private final McpResponseCorrelationService correlationService;

  public DefaultMcpServer(
      McpSessionService sessionService,
      JsonRpcDispatcher dispatcher,
      McpResponseCorrelationService correlationService) {
    this.sessionService = sessionService;
    this.dispatcher = dispatcher;
    this.correlationService = correlationService;
  }

  @Override
  public McpContextResult createContext(String sessionId, String protocolVersion) {
    if (sessionId == null || sessionId.isEmpty()) {
      return new McpContextResult.SessionIdRequired(
          -32000, "Bad Request: MCP-Session-Id header is required");
    }

    var session = sessionService.find(sessionId);
    if (session.isEmpty()) {
      return new McpContextResult.SessionNotFound(-32001, "Session not found");
    }

    if (protocolVersion != null && !KNOWN_PROTOCOL_VERSIONS.contains(protocolVersion)) {
      return new McpContextResult.ProtocolVersionMismatch(
          -32000, "Bad Request: Unsupported protocol version: " + protocolVersion);
    }

    return new McpContextResult.ValidContext(new SessionMcpContext(session.get(), protocolVersion));
  }

  @Override
  public void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
    McpSession session = context.session().orElse(null);

    if (session != null
        && !session.initialized()
        && !PING.equals(call.method())
        && !INITIALIZE.equals(call.method())) {
      transport.send(
          new JsonRpcError(JsonRpcProtocol.INVALID_REQUEST, "Session not initialized", call.id()));
      return;
    }

    JsonRpcResponse response;
    if (session != null) {
      response =
          ScopedValue.where(McpSession.CURRENT, session)
              .where(McpTransport.CURRENT, transport)
              .call(() -> dispatcher.dispatch(call));
    } else {
      response =
          ScopedValue.where(McpTransport.CURRENT, transport).call(() -> dispatcher.dispatch(call));
    }
    if (response != null) {
      transport.send(response);
    }
  }

  @Override
  public void handleNotification(McpContext context, JsonRpcNotification notification) {
    McpSession session =
        context
            .session()
            .orElseThrow(() -> new IllegalStateException("Session not found in context"));

    if (!session.initialized()
        && !NOTIFICATIONS_INITIALIZED.equals(notification.method())
        && !PING.equals(notification.method())) {
      return;
    }

    ScopedValue.where(McpSession.CURRENT, session).run(() -> dispatcher.dispatch(notification));
  }

  @Override
  public void handleResponse(McpContext context, JsonRpcResponse response) {
    correlationService.deliver(response);
  }

  @Override
  public void terminate(McpContext context) {
    context.session().ifPresent(session -> sessionService.delete(session.sessionId()));
  }
}
