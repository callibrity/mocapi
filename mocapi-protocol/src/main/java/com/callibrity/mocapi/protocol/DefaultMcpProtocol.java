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
package com.callibrity.mocapi.protocol;

import com.callibrity.mocapi.model.InitializeRequestParams;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.protocol.session.McpSession;
import com.callibrity.mocapi.protocol.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import tools.jackson.databind.ObjectMapper;

/** Skeleton {@link McpProtocol} that handles session enforcement and the initialize handshake. */
public class DefaultMcpProtocol implements McpProtocol {

  private static final String INITIALIZE = "initialize";

  private final McpSessionService sessionService;
  private final InitializeResult initializeResult;
  private final ObjectMapper objectMapper;

  public DefaultMcpProtocol(
      McpSessionService sessionService,
      InitializeResult initializeResult,
      ObjectMapper objectMapper) {
    this.sessionService = sessionService;
    this.initializeResult = initializeResult;
    this.objectMapper = objectMapper;
  }

  @Override
  public void handle(McpContext context, JsonRpcMessage message, McpTransport transport) {
    if (message instanceof JsonRpcRequest request) {
      switch (request) {
        case JsonRpcCall call -> handleCall(context, call, transport);
        case JsonRpcNotification notification -> handleNotification(context, notification);
      }
    }
  }

  private void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
    if (INITIALIZE.equals(call.method())) {
      handleInitialize(call, transport);
      return;
    }
    if (context.sessionId() == null || context.sessionId().isBlank()) {
      transport.send(call.error(JsonRpcProtocol.INVALID_REQUEST, "Missing session ID"));
      return;
    }
    if (sessionService.find(context.sessionId()).isEmpty()) {
      transport.send(call.error(JsonRpcProtocol.INVALID_REQUEST, "Unknown session"));
    }
  }

  private void handleNotification(McpContext context, JsonRpcNotification notification) {
    if (INITIALIZE.equals(notification.method())) {
      return;
    }
    if (context.sessionId() == null || context.sessionId().isBlank()) {
      return;
    }
    sessionService.find(context.sessionId());
  }

  private void handleInitialize(JsonRpcCall call, McpTransport transport) {
    InitializeRequestParams params =
        objectMapper.treeToValue(call.params(), InitializeRequestParams.class);
    McpSession session =
        new McpSession(params.protocolVersion(), params.capabilities(), params.clientInfo());
    String sessionId = sessionService.create(session);
    transport.emit(new McpEvent.SessionInitialized(sessionId, params.protocolVersion()));
    transport.send(call.result(objectMapper.valueToTree(initializeResult)));
  }
}
