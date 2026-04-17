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
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class StreamableHttpTransportTest {

  @Mock SseStream sseStream;

  @Test
  void exposesSessionIdHeaderConstant() {
    assertThat(StreamableHttpTransport.SESSION_ID_HEADER).isEqualTo("MCP-Session-Id");
  }

  @Test
  void responseFutureIsInitiallyIncomplete() {
    var transport = new StreamableHttpTransport(unusedSupplier());

    assertThat(transport.response()).isNotCompleted();
  }

  @Test
  void sendResponseCompletesFutureWithJsonEntity() {
    var transport = new StreamableHttpTransport(unusedSupplier());
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("k", "v"),
            JsonNodeFactory.instance.numberNode(1));

    transport.send(result);

    var entity = transport.response().getNow(null);
    assertThat(entity).isNotNull();
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(entity.getBody()).isSameAs(result);
  }

  @Test
  void sendErrorCompletesFutureWithJsonEntity() {
    var transport = new StreamableHttpTransport(unusedSupplier());
    var error = new JsonRpcError(-32000, "err", JsonNodeFactory.instance.numberNode(1));

    transport.send(error);

    var entity = transport.response().getNow(null);
    assertThat(entity.getBody()).isSameAs(error);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void sendNotificationUpgradesToSse() {
    SseEmitter emitter = new SseEmitter();
    when(sseStream.createEmitter()).thenReturn(emitter);
    var transport = new StreamableHttpTransport(() -> sseStream);
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    transport.send(notification);

    var entity = transport.response().getNow(null);
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
    assertThat(entity.getBody()).isSameAs(emitter);
    verify(sseStream).write(notification);
  }

  @Test
  void subsequentSendsAfterSseUpgradeGoToStream() {
    when(sseStream.createEmitter()).thenReturn(new SseEmitter());
    var transport = new StreamableHttpTransport(() -> sseStream);

    transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
    var response =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));
    transport.send(response);

    verify(sseStream).write(response);
  }

  @Test
  void sendAfterJsonCommitThrows() {
    var transport = new StreamableHttpTransport(unusedSupplier());
    transport.send(
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1)));

    assertThatThrownBy(
            () -> transport.send(new JsonRpcNotification("2.0", "notifications/progress", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed response");
  }

  @Test
  void sendAfterSseTerminalResponseThrows() {
    when(sseStream.createEmitter()).thenReturn(new SseEmitter());
    var transport = new StreamableHttpTransport(() -> sseStream);
    transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));
    transport.send(
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1)));

    assertThatThrownBy(
            () -> transport.send(new JsonRpcNotification("2.0", "notifications/late", null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("closed response");
  }

  @Test
  void emitSessionInitializedAddsSessionHeaderOnJsonCommit() {
    var transport = new StreamableHttpTransport(unusedSupplier());

    transport.emit(new McpEvent.SessionInitialized("sess-42", "2025-11-25"));
    transport.send(
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1)));

    var entity = transport.response().getNow(null);
    assertThat(entity.getHeaders().getFirst(StreamableHttpTransport.SESSION_ID_HEADER))
        .isEqualTo("sess-42");
  }

  @Test
  void emitSessionInitializedAddsSessionHeaderOnSseCommit() {
    when(sseStream.createEmitter()).thenReturn(new SseEmitter());
    var transport = new StreamableHttpTransport(() -> sseStream);

    transport.emit(new McpEvent.SessionInitialized("sess-99", "2025-11-25"));
    transport.send(new JsonRpcNotification("2.0", "notifications/progress", null));

    var entity = transport.response().getNow(null);
    assertThat(entity.getHeaders().getFirst(StreamableHttpTransport.SESSION_ID_HEADER))
        .isEqualTo("sess-99");
  }

  @Test
  void withoutEmitSessionHeaderIsAbsent() {
    var transport = new StreamableHttpTransport(unusedSupplier());

    transport.send(
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1)));

    assertThat(
            transport
                .response()
                .getNow(null)
                .getHeaders()
                .getFirst(StreamableHttpTransport.SESSION_ID_HEADER))
        .isNull();
  }

  private static Supplier<SseStream> unusedSupplier() {
    return () -> {
      throw new AssertionError("should not be called");
    };
  }
}
