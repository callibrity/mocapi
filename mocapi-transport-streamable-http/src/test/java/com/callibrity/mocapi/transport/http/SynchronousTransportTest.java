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

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.node.JsonNodeFactory;

class SynchronousTransportTest {

  @Test
  void sendBuffersResponse() {
    var transport = new SynchronousTransport();
    var result = new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1));

    transport.send(result);

    var response = transport.toResponseEntity();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(result);
  }

  @Test
  void toResponseEntitySetsJsonContentType() {
    var transport = new SynchronousTransport();
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    var response = transport.toResponseEntity();
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void emitCapturesSessionInitializedHeader() {
    var transport = new SynchronousTransport();
    transport.emit(new McpEvent.SessionInitialized("session-42", "2025-11-25"));
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    var response = transport.toResponseEntity();
    assertThat(response.getHeaders().getFirst("MCP-Session-Id")).isEqualTo("session-42");
  }

  @Test
  void toResponseEntityWithoutSessionInitializedOmitsHeader() {
    var transport = new SynchronousTransport();
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    var response = transport.toResponseEntity();
    assertThat(response.getHeaders().getFirst("MCP-Session-Id")).isNull();
  }

  @Test
  void sendThrowsOnNonResponseMessage() {
    var transport = new SynchronousTransport();
    var call = JsonRpcCall.of("ping", null, intNode(1));

    assertThatThrownBy(() -> transport.send(call))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("only supports response messages");
  }

  @Test
  void sendThrowsOnSecondResponse() {
    var transport = new SynchronousTransport();
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    assertThatThrownBy(
            () ->
                transport.send(
                    new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(2))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already holds a response");
  }

  private static tools.jackson.databind.JsonNode intNode(int value) {
    return JsonNodeFactory.instance.numberNode(value);
  }
}
