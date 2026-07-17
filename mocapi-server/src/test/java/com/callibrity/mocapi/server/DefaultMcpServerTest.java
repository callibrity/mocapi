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

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.model.UnsupportedProtocolVersionErrorData;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.exchange.MetaEnvelopeParser;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMcpServerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private JsonRpcDispatcher dispatcher;

  private DefaultMcpServer server;
  private McpTransport transport;

  @BeforeEach
  void setUp() {
    server = new DefaultMcpServer(dispatcher, new MetaEnvelopeParser(MAPPER), MAPPER);
    transport = mock(McpTransport.class);
  }

  private static ObjectNode paramsWithEnvelope(String protocolVersion) {
    ObjectNode params = JsonNodeFactory.instance.objectNode();
    ObjectNode meta = params.putObject("_meta");
    meta.put(McpMetaKeys.PROTOCOL_VERSION, protocolVersion);
    ObjectNode clientInfo = meta.putObject(McpMetaKeys.CLIENT_INFO);
    clientInfo.put("name", "test-client");
    clientInfo.put("version", "1.0");
    meta.putObject(McpMetaKeys.CLIENT_CAPABILITIES);
    return params;
  }

  private static JsonRpcCall validCall(String method) {
    return JsonRpcCall.of(
        method,
        paramsWithEnvelope(McpServer.PROTOCOL_VERSION),
        JsonNodeFactory.instance.numberNode(1));
  }

  @Nested
  class When_the_envelope_is_valid {

    @Test
    void dispatches_with_exchange_and_transport_bound() {
      JsonRpcResult dispatchResult =
          new JsonRpcResult(
              JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));
      when(dispatcher.dispatch(any(JsonRpcCall.class)))
          .thenAnswer(
              _ -> {
                assertThat(McpTransport.CURRENT.isBound()).isTrue();
                assertThat(McpTransport.CURRENT.get()).isSameAs(transport);
                assertThat(McpExchange.CURRENT.isBound()).isTrue();
                McpExchange exchange = McpExchange.CURRENT.get();
                assertThat(exchange.protocolVersion()).isEqualTo(McpServer.PROTOCOL_VERSION);
                assertThat(exchange.clientInfo().name()).isEqualTo("test-client");
                return dispatchResult;
              });

      JsonRpcCall call = validCall("tools/list");

      server.handleCall(call, transport);

      verify(dispatcher).dispatch(call);
      var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
      verify(transport).send(captor.capture());
      assertThat(captor.getValue()).isSameAs(dispatchResult);
    }

    @Test
    void null_dispatch_result_sends_nothing() {
      when(dispatcher.dispatch(any(JsonRpcCall.class))).thenReturn(null);

      server.handleCall(validCall("tools/list"), transport);

      verify(dispatcher).dispatch(any(JsonRpcCall.class));
      verifyNoInteractions(transport);
    }
  }

  @Nested
  class When_the_envelope_is_missing_or_malformed {

    @Test
    void call_without_params_gets_invalid_params_and_is_never_dispatched() {
      JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(7));

      server.handleCall(call, transport);

      var error = (JsonRpcError) captureSent();
      assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      verifyNoInteractions(dispatcher);
    }

    @Test
    void call_with_params_but_no_meta_gets_invalid_params() {
      ObjectNode params = JsonNodeFactory.instance.objectNode().put("name", "some-tool");
      JsonRpcCall call =
          JsonRpcCall.of("tools/call", params, JsonNodeFactory.instance.numberNode(8));

      server.handleCall(call, transport);

      var error = (JsonRpcError) captureSent();
      assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      verifyNoInteractions(dispatcher);
    }

    @Test
    void call_with_missing_required_meta_key_gets_invalid_params() {
      ObjectNode params = paramsWithEnvelope(McpServer.PROTOCOL_VERSION);
      ((ObjectNode) params.get("_meta")).remove(McpMetaKeys.CLIENT_CAPABILITIES);
      JsonRpcCall call =
          JsonRpcCall.of("tools/list", params, JsonNodeFactory.instance.numberNode(9));

      server.handleCall(call, transport);

      var error = (JsonRpcError) captureSent();
      assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
      verifyNoInteractions(dispatcher);
    }
  }

  @Nested
  class When_the_protocol_version_is_unsupported {

    @Test
    void responds_with_unsupported_protocol_version_error_listing_supported_versions() {
      JsonRpcCall call =
          JsonRpcCall.of(
              "tools/list",
              paramsWithEnvelope("2025-11-25"),
              JsonNodeFactory.instance.numberNode(10));

      server.handleCall(call, transport);

      var error = (JsonRpcError) captureSent();
      assertThat(error.error().code()).isEqualTo(UnsupportedProtocolVersionErrorData.CODE);
      assertThat(error.error().data().path("supported").get(0).asString())
          .isEqualTo(McpServer.PROTOCOL_VERSION);
      assertThat(error.error().data().path("requested").asString()).isEqualTo("2025-11-25");
      assertThat(error.id()).isEqualTo(call.id());
      verifyNoInteractions(dispatcher);
    }
  }

  @Nested
  class Notifications {

    @Test
    void notifications_are_dispatched_without_envelope_parsing() {
      JsonRpcNotification notification =
          new JsonRpcNotification("2.0", "notifications/cancelled", null);
      when(dispatcher.dispatch(notification)).thenReturn(null);

      server.handleNotification(notification);

      verify(dispatcher).dispatch(notification);
    }
  }

  private JsonRpcMessage captureSent() {
    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport).send(captor.capture());
    return captor.getValue();
  }
}
