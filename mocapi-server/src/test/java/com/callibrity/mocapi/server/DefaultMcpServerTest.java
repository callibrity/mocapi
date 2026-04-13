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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  private CapturingTransport transport;

  @BeforeEach
  void setUp() {
    protocol = new DefaultMcpServer(sessionService, dispatcher, correlationService);
    transport = new CapturingTransport();
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
    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isSameAs(dispatchResult);
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
  void nonInitializeCallWithoutSessionIdReturnsError() {
    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handleCall(noSessionContext(), call, transport);

    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isInstanceOf(JsonRpcError.class);
    JsonRpcError error = (JsonRpcError) transport.messages().getFirst();
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_REQUEST);
    assertThat(error.error().message()).isEqualTo("Missing session ID");

    verifyNoInteractions(dispatcher);
  }

  @Test
  void nonInitializeCallWithUnknownSessionReturnsError() {
    when(sessionService.find("unknown")).thenReturn(Optional.empty());

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handleCall(sessionContext("unknown"), call, transport);

    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isInstanceOf(JsonRpcError.class);
    JsonRpcError error = (JsonRpcError) transport.messages().getFirst();
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_REQUEST);
    assertThat(error.error().message()).isEqualTo("Unknown session");

    verifyNoInteractions(dispatcher);
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
    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isSameAs(dispatchResult);
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
    assertThat(transport.messages()).isEmpty();
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
  void notificationWithoutSessionIdIsDropped() {
    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);

    protocol.handleNotification(noSessionContext(), notification);

    verifyNoInteractions(dispatcher);
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
