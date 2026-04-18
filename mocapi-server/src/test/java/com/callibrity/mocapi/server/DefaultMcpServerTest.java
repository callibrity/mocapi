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

import com.callibrity.mocapi.model.LoggingLevel;
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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMcpServerTest {

  private static final String PROTOCOL_VERSION = "2025-11-25";

  @Mock private McpSessionService sessionService;
  @Mock private JsonRpcDispatcher dispatcher;
  @Mock private McpResponseCorrelationService correlationService;

  private DefaultMcpServer server;
  private McpTransport transport;

  @BeforeEach
  void setUp() {
    server = new DefaultMcpServer(sessionService, dispatcher, correlationService);
    transport = mock(McpTransport.class);
  }

  @Test
  void initialize_call_is_dispatched_with_transport_bound() {
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

    server.handleCall(McpContext.empty(), call, transport);

    verify(dispatcher).dispatch(call);
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getValue()).isSameAs(dispatchResult);
  }

  @Test
  void initialize_call_does_not_require_session_id() {
    when(dispatcher.dispatch(any(JsonRpcCall.class))).thenReturn(null);

    JsonRpcCall call = JsonRpcCall.of("initialize", null, JsonNodeFactory.instance.numberNode(1));

    server.handleCall(McpContext.empty(), call, transport);

    verify(dispatcher).dispatch(call);
    verifyNoInteractions(sessionService);
  }

  @Test
  void create_context_returns_session_id_required_for_null_session_id() {
    assertThat(server.createContext(null, null))
        .isInstanceOf(McpContextResult.SessionIdRequired.class);
  }

  @Test
  void create_context_returns_session_id_required_for_empty_session_id() {
    assertThat(server.createContext("", null))
        .isInstanceOf(McpContextResult.SessionIdRequired.class);
  }

  @Test
  void create_context_returns_session_not_found_for_non_existent_session() {
    when(sessionService.find("unknown")).thenReturn(Optional.empty());
    assertThat(server.createContext("unknown", PROTOCOL_VERSION))
        .isInstanceOf(McpContextResult.SessionNotFound.class);
  }

  @Test
  void create_context_returns_valid_context_for_known_session() {
    McpSession session = session("known", true);
    when(sessionService.find("known")).thenReturn(Optional.of(session));
    var result = server.createContext("known", PROTOCOL_VERSION);
    assertThat(result).isInstanceOf(McpContextResult.ValidContext.class);
    var ctx = ((McpContextResult.ValidContext) result).context();
    assertThat(ctx.sessionId()).isEqualTo("known");
    assertThat(ctx.session()).isPresent().hasValue(session);
  }

  @Test
  void create_context_returns_protocol_version_mismatch_for_bad_version() {
    McpSession session = session("valid", true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));
    assertThat(server.createContext("valid", "9999-01-01"))
        .isInstanceOf(McpContextResult.ProtocolVersionMismatch.class);
  }

  @Test
  void create_context_returns_valid_context_for_null_protocol_version() {
    McpSession session = session("valid", true);
    when(sessionService.find("valid")).thenReturn(Optional.of(session));
    assertThat(server.createContext("valid", null))
        .isInstanceOf(McpContextResult.ValidContext.class);
  }

  @Test
  void call_with_valid_session_dispatches_and_sends_result() {
    McpSession session = session("valid", true);

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(2));
    JsonRpcResult dispatchResult =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("tools", "[]"),
            JsonNodeFactory.instance.numberNode(2));
    when(dispatcher.dispatch(call)).thenReturn(dispatchResult);

    server.handleCall(contextWithSession(session), call, transport);

    verify(dispatcher).dispatch(call);
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    assertThat(captor.getValue()).isSameAs(dispatchResult);
  }

  @Test
  void call_with_valid_session_binds_session_to_scoped_value() {
    McpSession session = session("valid", true);

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(3));
    when(dispatcher.dispatch(call))
        .thenAnswer(
            _ -> {
              assertThat(McpSession.CURRENT.isBound()).isTrue();
              assertThat(McpSession.CURRENT.get()).isSameAs(session);
              return new JsonRpcResult(
                  JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(3));
            });

    server.handleCall(contextWithSession(session), call, transport);

    verify(dispatcher).dispatch(call);
  }

  @Test
  void call_dispatch_returning_null_sends_nothing() {
    McpSession session = session("valid", true);

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(4));
    when(dispatcher.dispatch(call)).thenReturn(null);

    server.handleCall(contextWithSession(session), call, transport);

    verify(dispatcher).dispatch(call);
    verifyNoInteractions(transport);
  }

  @Test
  void notification_with_valid_session_dispatches() {
    McpSession session = session("valid", true);

    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);
    when(dispatcher.dispatch(notification)).thenReturn(null);

    server.handleNotification(contextWithSession(session), notification);

    verify(dispatcher).dispatch(notification);
  }

  @Test
  void notification_with_valid_session_binds_session_to_scoped_value() {
    McpSession session = session("valid", true);

    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);
    when(dispatcher.dispatch(notification))
        .thenAnswer(
            _ -> {
              assertThat(McpSession.CURRENT.isBound()).isTrue();
              assertThat(McpSession.CURRENT.get()).isSameAs(session);
              return null;
            });

    server.handleNotification(contextWithSession(session), notification);

    verify(dispatcher).dispatch(notification);
  }

  @Test
  void response_messages_are_delivered_to_correlation_service() {
    JsonRpcResponse response =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    server.handleResponse(McpContext.empty(), response);

    verify(correlationService).deliver(response);

    verifyNoInteractions(dispatcher);
  }

  @Test
  void terminate_deletes_session() {
    McpSession session =
        new McpSession(
            "session-to-delete",
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            true);
    McpContext context = new SessionMcpContext(session, PROTOCOL_VERSION);

    server.terminate(context);

    verify(sessionService).delete("session-to-delete");
    verifyNoInteractions(dispatcher);
    verifyNoInteractions(correlationService);
  }

  private static McpSession session(String sessionId, boolean initialized) {
    return new McpSession(
        sessionId, PROTOCOL_VERSION, null, null, LoggingLevel.WARNING, initialized);
  }

  private static McpContext contextWithSession(McpSession session) {
    return new SessionMcpContext(session, PROTOCOL_VERSION);
  }
}
