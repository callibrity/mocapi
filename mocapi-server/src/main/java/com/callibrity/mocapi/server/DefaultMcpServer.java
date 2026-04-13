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
  public ValidationResult validate(McpContext context, JsonRpcMessage message) {
    String method =
        switch (message) {
          case JsonRpcCall call -> call.method();
          case JsonRpcNotification notification -> notification.method();
          case JsonRpcResponse _ -> null;
        };

    if (INITIALIZE.equals(method)) {
      return new ValidationResult.Valid();
    }

    String sessionId = context.sessionId();
    if (sessionId == null || sessionId.isEmpty()) {
      return new ValidationResult.MissingSessionId();
    }

    var session = sessionService.find(sessionId);
    if (session.isEmpty()) {
      return new ValidationResult.UnknownSession(sessionId);
    }

    ValidationResult protocolCheck = validateProtocolVersion(context.protocolVersion());
    if (protocolCheck != null) {
      return protocolCheck;
    }

    if (method != null
        && !session.get().initialized()
        && !PING.equals(method)
        && !NOTIFICATIONS_INITIALIZED.equals(method)) {
      return new ValidationResult.SessionNotInitialized();
    }

    return new ValidationResult.Valid();
  }

  @Override
  public ValidationResult validate(McpContext context) {
    var session = sessionService.find(context.sessionId());
    if (session.isEmpty()) {
      return new ValidationResult.UnknownSession(context.sessionId());
    }

    ValidationResult protocolCheck = validateProtocolVersion(context.protocolVersion());
    if (protocolCheck != null) {
      return protocolCheck;
    }

    return new ValidationResult.Valid();
  }

  @Override
  public void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
    McpSession session = null;
    if (!INITIALIZE.equals(call.method())) {
      session =
          sessionService
              .find(context.sessionId())
              .orElseThrow(() -> new IllegalStateException("Session not found"));
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
    McpSession session =
        sessionService
            .find(context.sessionId())
            .orElseThrow(() -> new IllegalStateException("Session not found"));
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

  private ValidationResult validateProtocolVersion(String protocolVersion) {
    if (protocolVersion != null && !KNOWN_PROTOCOL_VERSIONS.contains(protocolVersion)) {
      return new ValidationResult.InvalidProtocolVersion(protocolVersion);
    }
    return null;
  }
}
