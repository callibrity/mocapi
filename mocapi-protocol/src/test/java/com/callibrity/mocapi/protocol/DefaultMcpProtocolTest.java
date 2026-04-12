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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.InitializeResult;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.protocol.session.McpSession;
import com.callibrity.mocapi.protocol.session.McpSessionService;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class DefaultMcpProtocolTest {

  private static final String PROTOCOL_VERSION = "2025-11-25";

  @Mock private McpSessionService sessionService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final InitializeResult initializeResult =
      new InitializeResult(
          PROTOCOL_VERSION,
          new ServerCapabilities(null, null, null, null, null),
          new Implementation("mocapi", null, "1.0"),
          null);

  private DefaultMcpProtocol protocol;
  private CapturingTransport transport;

  @BeforeEach
  void setUp() {
    protocol = new DefaultMcpProtocol(sessionService, initializeResult, objectMapper);
    transport = new CapturingTransport();
  }

  @Test
  void initializeCreatesSessionEmitsEventAndSendsResult() {
    when(sessionService.create(any(McpSession.class))).thenReturn("new-session-id");

    JsonNode params =
        objectMapper.valueToTree(
            new java.util.LinkedHashMap<>() {
              {
                put("protocolVersion", PROTOCOL_VERSION);
                put("capabilities", new java.util.LinkedHashMap<>());
                put(
                    "clientInfo",
                    new java.util.LinkedHashMap<>() {
                      {
                        put("name", "test-client");
                        put("version", "1.0");
                      }
                    });
              }
            });
    JsonRpcCall call = JsonRpcCall.of("initialize", params, JsonNodeFactory.instance.numberNode(1));

    protocol.handle(noSessionContext(), call, transport);

    assertThat(transport.events()).hasSize(1);
    McpEvent.SessionInitialized event = (McpEvent.SessionInitialized) transport.events().getFirst();
    assertThat(event.sessionId()).isEqualTo("new-session-id");
    assertThat(event.protocolVersion()).isEqualTo(PROTOCOL_VERSION);

    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isInstanceOf(JsonRpcResult.class);
    JsonRpcResult result = (JsonRpcResult) transport.messages().getFirst();
    assertThat(result.result().path("protocolVersion").asString()).isEqualTo(PROTOCOL_VERSION);
  }

  @Test
  void nonInitializeCallWithoutSessionIdReturnsError() {
    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handle(noSessionContext(), call, transport);

    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isInstanceOf(JsonRpcError.class);
    JsonRpcError error = (JsonRpcError) transport.messages().getFirst();
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_REQUEST);
    assertThat(error.error().message()).isEqualTo("Missing session ID");
  }

  @Test
  void nonInitializeCallWithUnknownSessionReturnsError() {
    when(sessionService.find("unknown")).thenReturn(Optional.empty());

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handle(sessionContext("unknown"), call, transport);

    assertThat(transport.messages()).hasSize(1);
    assertThat(transport.messages().getFirst()).isInstanceOf(JsonRpcError.class);
    JsonRpcError error = (JsonRpcError) transport.messages().getFirst();
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_REQUEST);
    assertThat(error.error().message()).isEqualTo("Unknown session");
  }

  @Test
  void nonInitializeCallWithValidSessionSendsNothing() {
    McpSession session =
        new McpSession(
            PROTOCOL_VERSION,
            null,
            null,
            com.callibrity.mocapi.model.LoggingLevel.WARNING,
            "valid");
    when(sessionService.find("valid")).thenReturn(Optional.of(session));

    JsonRpcCall call = JsonRpcCall.of("tools/list", null, JsonNodeFactory.instance.numberNode(1));

    protocol.handle(sessionContext("valid"), call, transport);

    assertThat(transport.messages()).isEmpty();
    assertThat(transport.events()).isEmpty();
  }

  @Test
  void notificationWithoutSessionIdIsDropped() {
    JsonRpcNotification notification =
        new JsonRpcNotification("2.0", "notifications/initialized", null);

    protocol.handle(noSessionContext(), notification, transport);

    assertThat(transport.messages()).isEmpty();
    assertThat(transport.events()).isEmpty();
  }

  @Test
  void responseMessagesAreIgnored() {
    JsonRpcResult response =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    protocol.handle(noSessionContext(), response, transport);

    assertThat(transport.messages()).isEmpty();
    assertThat(transport.events()).isEmpty();
  }

  private static McpContext noSessionContext() {
    return new SimpleContext(null, null);
  }

  private static McpContext sessionContext(String sessionId) {
    return new SimpleContext(sessionId, PROTOCOL_VERSION);
  }

  private record SimpleContext(String sessionId, String protocolVersion) implements McpContext {}
}
