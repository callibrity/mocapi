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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class SseMessageWriterTest {

  @Mock SseStream stream;

  private SseMessageWriter writer;

  @BeforeEach
  void setUp() {
    writer = new SseMessageWriter(stream);
  }

  @Test
  void writeResultPublishesAndTransitionsToClosed() {
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("k", "v"),
            JsonNodeFactory.instance.numberNode(1));

    MessageWriter next = writer.write(result);

    assertThat(next).isSameAs(ClosedMessageWriter.INSTANCE);
    verify(stream).write(result);
    verifyNoMoreInteractions(stream);
  }

  @Test
  void writeErrorPublishesAndTransitionsToClosed() {
    var error = new JsonRpcError(42, "boom", JsonNodeFactory.instance.numberNode(1));

    MessageWriter next = writer.write(error);

    assertThat(next).isSameAs(ClosedMessageWriter.INSTANCE);
    verify(stream).write(error);
  }

  @Test
  void writeNotificationPublishesAndStaysOpen() {
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    MessageWriter next = writer.write(notification);

    assertThat(next).isSameAs(writer);
    verify(stream).write(notification);
    verifyNoMoreInteractions(stream);
  }

  @Test
  void writeCallPublishesAndStaysOpen() {
    var call = JsonRpcCall.of("elicitation/create", null, JsonNodeFactory.instance.numberNode(7));

    MessageWriter next = writer.write(call);

    assertThat(next).isSameAs(writer);
    verify(stream).write(call);
    verifyNoMoreInteractions(stream);
  }

  @Test
  void multipleNotificationsStayOnSameWriter() {
    var first = new JsonRpcNotification("2.0", "notifications/progress", null);
    var second = new JsonRpcNotification("2.0", "notifications/message", null);

    MessageWriter afterFirst = writer.write(first);
    MessageWriter afterSecond = afterFirst.write(second);

    assertThat(afterFirst).isSameAs(writer);
    assertThat(afterSecond).isSameAs(writer);
    verify(stream).write(first);
    verify(stream).write(second);
  }

  @Test
  void emitterDelegatesToStream() {
    SseEmitter emitter = new SseEmitter();
    when(stream.createEmitter()).thenReturn(emitter);

    assertThat(writer.emitter()).isSameAs(emitter);
    verify(stream).createEmitter();
  }
}
