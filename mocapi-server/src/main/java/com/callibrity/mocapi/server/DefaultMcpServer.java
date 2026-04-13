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
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;

/** Default {@link McpServer} that handles session lifecycle and JSON-RPC dispatch. */
public class DefaultMcpServer implements McpServer {

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
  public boolean requiresSession(JsonRpcMessage message) {
    String method =
        switch (message) {
          case JsonRpcCall call -> call.method();
          case JsonRpcNotification notification -> notification.method();
          case JsonRpcResponse _ -> null;
        };
    return !INITIALIZE.equals(method);
  }

  @Override
  public boolean sessionExists(String sessionId) {
    return sessionService.find(sessionId).isPresent();
  }

  @Override
  public void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
    McpSession session = null;
    if (!INITIALIZE.equals(call.method())) {
      session = requireSession(context.sessionId());
      if (!session.initialized() && !PING.equals(call.method())) {
        transport.send(call.error(JsonRpcProtocol.INVALID_REQUEST, "Session not initialized"));
        return;
      }
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
    if (INITIALIZE.equals(notification.method())) {
      return;
    }
    McpSession session = requireSession(context.sessionId());
    if (!session.initialized() && !NOTIFICATIONS_INITIALIZED.equals(notification.method())) {
      return;
    }
    ScopedValue.where(McpSession.CURRENT, session).run(() -> dispatcher.dispatch(notification));
  }

  @Override
  public void handleResponse(McpContext context, JsonRpcResponse response) {
    correlationService.deliver(response);
  }

  @Override
  public void terminate(String sessionId) {
    sessionService.delete(sessionId);
  }

  private McpSession requireSession(String sessionId) {
    return sessionService
        .find(sessionId)
        .orElseThrow(() -> new IllegalStateException("Session not found: " + sessionId));
  }
}
