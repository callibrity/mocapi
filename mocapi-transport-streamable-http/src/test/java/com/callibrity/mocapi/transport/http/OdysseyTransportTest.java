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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyPublisher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class OdysseyTransportTest {

  @Mock private OdysseyPublisher<JsonRpcMessage> publisher;

  private OdysseyTransport transport;

  @BeforeEach
  void setUp() {
    transport = new OdysseyTransport(publisher);
  }

  @Test
  void sendPublishesMessageToOdyssey() {
    var call = JsonRpcCall.of("tools/list", null, intNode(1));

    transport.send(call);

    verify(publisher).publish(call);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  void sendCompletesStreamOnResponse() {
    var result = new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1));

    transport.send(result);

    verify(publisher).publish(result);
    verify(publisher).complete();
  }

  @Test
  void sendDoesNotCompleteOnNonResponse() {
    var notification = new JsonRpcNotification("2.0", "notifications/initialized", null);

    transport.send(notification);

    verify(publisher).publish(notification);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  void streamNameDelegatesToPublisher() {
    when(publisher.name()).thenReturn("stream-abc");

    assertThat(transport.streamName()).isEqualTo("stream-abc");
  }

  @Test
  void sendAfterCompletionThrows() {
    var result = new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1));
    transport.send(result);

    var call = JsonRpcCall.of("tools/list", null, intNode(2));
    assertThatThrownBy(() -> transport.send(call))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Transport completed");
  }

  @Test
  void emitIsNoOp() {
    transport.emit(new McpEvent.SessionInitialized("session-1", "2025-11-25"));

    verifyNoMoreInteractions(publisher);
  }

  private static tools.jackson.databind.JsonNode intNode(int value) {
    return JsonNodeFactory.instance.numberNode(value);
  }
}
