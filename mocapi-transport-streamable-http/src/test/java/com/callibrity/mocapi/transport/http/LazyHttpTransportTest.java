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

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.transport.http.LazyHttpTransport.SseConnection;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class LazyHttpTransportTest {

  @Mock OdysseyStream<JsonRpcMessage> stream;

  @Test
  void sendingResponseFromPendingCompletesFutureAsJson() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport = new LazyHttpTransport(future, unusedSseOpener());

    var result = new JsonRpcResult(JsonNodeFactory.instance.objectNode().put("k", "v"), intNode(1));
    transport.send(result);

    ResponseEntity<Object> entity = future.getNow(null);
    assertThat(entity).isNotNull();
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(entity.getBody()).isSameAs(result);
  }

  @Test
  void jsonResponseIncludesSessionInitializedHeader() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport = new LazyHttpTransport(future, unusedSseOpener());
    transport.emit(new McpEvent.SessionInitialized("session-42", "2025-11-25"));
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    var entity = future.getNow(null);
    assertThat(entity.getHeaders().getFirst("MCP-Session-Id")).isEqualTo("session-42");
  }

  @Test
  void sendAfterJsonCommitThrows() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport = new LazyHttpTransport(future, unusedSseOpener());
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    assertThatThrownBy(
            () -> transport.send(new JsonRpcNotification("2.0", "notifications/progress", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JSON response already committed");
  }

  @Test
  void sendingNotificationFromPendingUpgradesToSse() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var emitter = new SseEmitter();
    var transport = new LazyHttpTransport(future, () -> new SseConnection(stream, emitter));

    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);
    transport.send(notification);

    var entity = future.getNow(null);
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
    assertThat(entity.getBody()).isSameAs(emitter);
    verify(stream).publish(notification);
  }

  @Test
  void sendingServerInitiatedCallFromPendingUpgradesToSse() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var emitter = new SseEmitter();
    var transport = new LazyHttpTransport(future, () -> new SseConnection(stream, emitter));

    var call = JsonRpcCall.of("elicitation/create", null, intNode(7));
    transport.send(call);

    var entity = future.getNow(null);
    assertThat(entity.getBody()).isSameAs(emitter);
    verify(stream).publish(call);
  }

  @Test
  void sseSubsequentResponseCompletesStream() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport =
        new LazyHttpTransport(future, () -> new SseConnection(stream, new SseEmitter()));

    transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
    var response = new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1));
    transport.send(response);

    verify(stream).publish(response);
    verify(stream).complete();
  }

  @Test
  void sseAfterStreamCompleteThrows() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport =
        new LazyHttpTransport(future, () -> new SseConnection(stream, new SseEmitter()));

    transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
    transport.send(new JsonRpcResult(JsonNodeFactory.instance.objectNode(), intNode(1)));

    assertThatThrownBy(
            () -> transport.send(new JsonRpcNotification("2.0", "notifications/late", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Stream completed");
  }

  @Test
  void sseUpgradeIncludesSessionInitializedHeader() {
    var future = new CompletableFuture<ResponseEntity<Object>>();
    var transport =
        new LazyHttpTransport(future, () -> new SseConnection(stream, new SseEmitter()));
    transport.emit(new McpEvent.SessionInitialized("session-99", "2025-11-25"));

    transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));

    assertThat(future.getNow(null).getHeaders().getFirst("MCP-Session-Id")).isEqualTo("session-99");
  }

  private static Supplier<SseConnection> unusedSseOpener() {
    return () -> {
      throw new AssertionError("should not be called");
    };
  }

  private static tools.jackson.databind.JsonNode intNode(int value) {
    return JsonNodeFactory.instance.numberNode(value);
  }
}
