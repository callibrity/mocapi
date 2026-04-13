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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class DefaultMcpServerTest {

  private static final String PROTOCOL_VERSION = "2025-11-25";

  @Mock private McpSessionService sessionService;
  @Mock private JsonRpcDispatcher dispatcher;
  @Mock private McpResponseCorrelationService correlationService;

  private DefaultMcpServer protocol;
  private McpTransport transport;

  @BeforeEach
  void setUp() {
    protocol = new DefaultMcpServer(sessionService, dispatcher, correlationService);
    transport = mock(McpTransport.class);
  }

  @Test
  void initializeCallIsDispatchedWithTransportBound() {
    JsonRpcResult dispatchResult =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("protocolVersion", PROTOCOL_VERSION),
            JsonNodeFactory.instance.numberNode(1));
    when(dispatcher.dispatch(any(JsonRpcCall.class)))
        .thenAnswer(
            _ -> {
              assertThat(McpTransport.CURRENT.isBound()).isTrue();
              assertThat(McpTransport.CURRENT.get()).isSameAs(transport);
              assertThat(McpSession.CURRENT.isBound()).isFalse();
              return dispatchResult;
            });

    JsonRpcCall call = JsonRpcCall.of("initialize", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handleCall(noSessionContext(), call, transport);

    verify(dispatcher).dispatch(call);
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getValue()).isSameAs(dispatchResult);
  }

  @Test
  void initializeCallDoesNotRequireSessionId() {
    when(dispatcher.dispatch(any(JsonRpcCall.class))).thenReturn(null);

    JsonRpcCall call = JsonRpcCall.of("initialize", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handleCall(noSessionContext(), call, transport);

    verify(dispatcher).dispatch(call);
    verifyNoInteractions(sessionService);
  }

  @Test
  void requiresSessionReturnsTrueForNonInitializeCall() {
    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));
    assertThat(protocol.requiresSession(call)).isTrue();
  }

  @Test
  void requiresSessionReturnsFalseForInitializeCall() {
    JsonRpcCall call = JsonRpcCall.of("initialize", null, JsonNodeFactory.instance.numberNode(1));
    assertThat(protocol.requiresSession(call)).isFalse();
  }

  @Test
  void sessionExistsReturnsFalseForUnknownSession() {
    when(sessionService.find("unknown")).thenReturn(Optional.empty());
    assertThat(protocol.sessionExists("unknown")).isFalse();
  }

  @Test
  void sessionExistsReturnsTrueForKnownSession() {
    when(sessionService.find("known"))
        .thenReturn(
            Optional.of(
                new McpSession(
                    "known",
                    PROTOCOL_VERSION,
                    null,
                    null,
                    com.callibrity.mocapi.model.LoggingLevel.WARNING,
                    true)));
    assertThat(protocol.sessionExists("known")).isTrue();
  }

  @Test
  void callWithValidSessionDispatchesAndSendsResult() {
    McpSession session =
        new McpSession(
            "valid",
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(2));
    JsonRpcResult dispatchResult =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("tools", "[]"),
            JsonNodeFactory.instance.numberNode(2));
    when(dispatcher.dispatch(call)).thenReturn(dispatchResult);

    protocol.handleCall(sessionContext("valid"), call, transport);

    verify(dispatcher).dispatch(call);
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getValue()).isSameAs(dispatchResult);
  }

  @Test
  void callWithValidSessionBindsSessionToScopedValue() {
    McpSession session =
        new McpSession(
            "valid",
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(3));
    when(dispatcher.dispatch(call))
        .thenAnswer(
            _ -> {
              assertThat(McpSession.CURRENT.isBound()).isTrue();
              assertThat(McpSession.CURRENT.get()).isSameAs(session);
              return new JsonRpcResult(
                  JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(3));
            });

    protocol.handleCall(sessionContext("valid"), call, transport);

    verify(dispatcher).dispatch(call);
  }

  @Test
  void callDispatchReturningNullSendsNothing() {
    McpSession session =
        new McpSession(
            "valid",
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(4));
    when(dispatcher.dispatch(call)).thenReturn(null);

    protocol.handleCall(sessionContext("valid"), call, transport);

    verify(dispatcher).dispatch(call);
    verifyNoInteractions(transport);
  }

  @Test
  void notificationWithValidSessionDispatches() {
    McpSession session =
        new McpSession(
            "valid",
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));

    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);
    when(dispatcher.dispatch(notification)).thenReturn(null);

    protocol.handleNotification(sessionContext("valid"), notification);

    verify(dispatcher).dispatch(notification);
  }

  @Test
  void notificationWithValidSessionBindsSessionToScopedValue() {
    McpSession session =
        new McpSession(
            "valid",
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));

    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);
    when(dispatcher.dispatch(notification))
        .thenAnswer(
            _ -> {
              assertThat(McpSession.CURRENT.isBound()).isTrue();
              assertThat(McpSession.CURRENT.get()).isSameAs(session);
              return null;
            });

    protocol.handleNotification(sessionContext("valid"), notification);

    verify(dispatcher).dispatch(notification);
  }

  @Test
  void requiresSessionReturnsTrueForNotification() {
    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);

    assertThat(protocol.requiresSession(notification)).isTrue();
  }

  @Test
  void responseMessagesAreDeliveredToCorrelationService() {
    JsonRpcResponse response =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    protocol.handleResponse(noSessionContext(), response);

    verify(correlationService).deliver(response);

    verifyNoInteractions(dispatcher);
  }

  @Test
  void terminateDeletesSession() {
    protocol.terminate("session-to-delete");

    verify(sessionService).delete("session-to-delete");
    verifyNoInteractions(dispatcher);
    verifyNoInteractions(correlationService);
  }

  private static McpContext noSessionContext() {
    return new SimpleContext(null, null);
  }

  private static McpContext sessionContext(String sessionId) {
    return new SimpleContext(sessionId, PROTOCOL_VERSION);
  }

  private record SimpleContext(String sessionId, String protocolVersion) implements McpContext {}
}
