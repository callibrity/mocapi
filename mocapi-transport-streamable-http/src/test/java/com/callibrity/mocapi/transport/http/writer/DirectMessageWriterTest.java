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
package com.callibrity.mocapi.transport.http.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class DirectMessageWriterTest {

  @Mock SseStream sseStream;

  private List<ResponseEntity<Object>> committed;
  private Consumer<ResponseEntity<Object>> consumer;

  @BeforeEach
  void setUp() {
    committed = new ArrayList<>();
    consumer = committed::add;
  }

  @Test
  void writeResultCommitsJsonAndTransitionsToClosed() {
    var writer = new DirectMessageWriter(unusedStreamSupplier(), consumer);
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("k", "v"),
            JsonNodeFactory.instance.numberNode(1));

    MessageWriter next = writer.write(result);

    assertThat(next).isSameAs(ClosedMessageWriter.INSTANCE);
    assertThat(committed).hasSize(1);
    ResponseEntity<Object> entity = committed.get(0);
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(entity.getBody()).isSameAs(result);
  }

  @Test
  void writeErrorCommitsJsonAndTransitionsToClosed() {
    var writer = new DirectMessageWriter(unusedStreamSupplier(), consumer);
    var error = new JsonRpcError(42, "boom", JsonNodeFactory.instance.numberNode(1));

    MessageWriter next = writer.write(error);

    assertThat(next).isSameAs(ClosedMessageWriter.INSTANCE);
    assertThat(committed).hasSize(1);
    assertThat(committed.get(0).getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(committed.get(0).getBody()).isSameAs(error);
  }

  @Test
  void writeResponseDoesNotPullStream() {
    Supplier<SseStream> exploding =
        () -> {
          throw new AssertionError("should not be called");
        };
    var writer = new DirectMessageWriter(exploding, consumer);
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    writer.write(result);

    assertThat(committed).hasSize(1);
  }

  @Test
  void writeNotificationCommitsSseAndTransitionsToSseWriter() {
    SseEmitter emitter = new SseEmitter();
    when(sseStream.createEmitter()).thenReturn(emitter);
    var writer = new DirectMessageWriter(() -> sseStream, consumer);
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    MessageWriter next = writer.write(notification);

    assertThat(next).isInstanceOf(SseMessageWriter.class);
    assertThat(committed).hasSize(1);
    ResponseEntity<Object> entity = committed.get(0);
    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
    assertThat(entity.getBody()).isSameAs(emitter);
    verify(sseStream).write(notification);
  }

  @Test
  void writeCallCommitsSseAndTransitionsToSseWriter() {
    SseEmitter emitter = new SseEmitter();
    when(sseStream.createEmitter()).thenReturn(emitter);
    var writer = new DirectMessageWriter(() -> sseStream, consumer);
    var call = JsonRpcCall.of("elicitation/create", null, JsonNodeFactory.instance.numberNode(7));

    MessageWriter next = writer.write(call);

    assertThat(next).isInstanceOf(SseMessageWriter.class);
    assertThat(committed.get(0).getBody()).isSameAs(emitter);
    verify(sseStream).write(call);
  }

  @Test
  void writeNotificationPullsStreamExactlyOnce() {
    SseEmitter emitter = new SseEmitter();
    when(sseStream.createEmitter()).thenReturn(emitter);
    int[] supplierInvocations = {0};
    Supplier<SseStream> countingSupplier =
        () -> {
          supplierInvocations[0]++;
          return sseStream;
        };
    var writer = new DirectMessageWriter(countingSupplier, consumer);

    writer.write(new JsonRpcNotification("2.0", "notifications/progress", null));

    assertThat(supplierInvocations[0]).isEqualTo(1);
  }

  @Test
  void sseUpgradeDoesNotWriteResponseToStream() {
    SseEmitter emitter = new SseEmitter();
    when(sseStream.createEmitter()).thenReturn(emitter);
    var writer = new DirectMessageWriter(() -> sseStream, consumer);
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    writer.write(notification);

    // Only the notification was written; no response was published before returning
    verify(sseStream).write(notification);
    verify(sseStream, never())
        .write(
            new JsonRpcResult(
                JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1)));
  }

  private Supplier<SseStream> unusedStreamSupplier() {
    return () -> {
      throw new AssertionError("should not be called");
    };
  }
}
