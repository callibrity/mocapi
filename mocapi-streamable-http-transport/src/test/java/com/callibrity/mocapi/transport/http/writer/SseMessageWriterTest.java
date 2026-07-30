/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SseMessageWriterTest {

  @Mock SseStream stream;

  @InjectMocks SseMessageWriter writer;

  @Test
  void write_result_publishes_closes_the_stream_and_transitions_to_closed() {
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("k", "v"),
            JsonNodeFactory.instance.numberNode(1));

    MessageWriter next = writer.write(result);

    assertThat(next).isSameAs(ClosedMessageWriter.INSTANCE);
    verify(stream).write(result);
    verify(stream).close();
    verifyNoMoreInteractions(stream);
  }

  @Test
  void write_error_publishes_closes_the_stream_and_transitions_to_closed() {
    var error = new JsonRpcError(42, "boom", JsonNodeFactory.instance.numberNode(1));

    MessageWriter next = writer.write(error);

    assertThat(next).isSameAs(ClosedMessageWriter.INSTANCE);
    verify(stream).write(error);
    verify(stream).close();
  }

  @Test
  void closes_the_stream_even_if_writing_the_terminal_response_throws() {
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode().put("k", "v"),
            JsonNodeFactory.instance.numberNode(1));
    doThrow(new RuntimeException("write boom")).when(stream).write(any());

    // The terminal response MUST always close the stream; a failure writing it must not leak the
    // committed emitter.
    assertThatThrownBy(() -> writer.write(result)).isInstanceOf(RuntimeException.class);
    verify(stream).close();
  }

  @Test
  void write_notification_publishes_and_stays_open() {
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    MessageWriter next = writer.write(notification);

    assertThat(next).isSameAs(writer);
    verify(stream).write(notification);
    verifyNoMoreInteractions(stream);
  }

  @Test
  void multiple_notifications_stay_on_same_writer() {
    var first = new JsonRpcNotification("2.0", "notifications/progress", null);
    var second = new JsonRpcNotification("2.0", "notifications/progress", null);

    MessageWriter afterFirst = writer.write(first);
    MessageWriter afterSecond = afterFirst.write(second);

    assertThat(afterFirst).isSameAs(writer);
    assertThat(afterSecond).isSameAs(writer);
  }
}
