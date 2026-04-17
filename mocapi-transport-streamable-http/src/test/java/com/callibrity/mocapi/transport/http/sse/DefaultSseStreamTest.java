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
package com.callibrity.mocapi.transport.http.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
class DefaultSseStreamTest {

  @Mock OdysseyStream<JsonRpcMessage> odysseyStream;

  @SuppressWarnings("unchecked")
  @Mock
  SseEventMapper<JsonRpcMessage> mapper;

  @Test
  void writeDelegatesToOdysseyStreamPublish() {
    Function<OdysseyStream<JsonRpcMessage>, SseEmitter> unusedEmitterFn =
        s -> {
          throw new AssertionError("should not be called");
        };
    var stream = new DefaultSseStream(odysseyStream, unusedEmitterFn);
    var notification = new JsonRpcNotification("2.0", "notifications/progress", null);

    stream.write(notification);

    verify(odysseyStream).publish(notification);
  }

  @Test
  void writeAcceptsAnyJsonRpcMessageType() {
    Function<OdysseyStream<JsonRpcMessage>, SseEmitter> unusedEmitterFn =
        s -> {
          throw new AssertionError("should not be called");
        };
    var stream = new DefaultSseStream(odysseyStream, unusedEmitterFn);
    var result =
        new JsonRpcResult(
            JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.numberNode(1));

    stream.write(result);

    verify(odysseyStream).publish(result);
  }

  @Test
  void createEmitterInvokesEmitterFnWithUnderlyingStream() {
    SseEmitter emitter = new SseEmitter();
    @SuppressWarnings("unchecked")
    Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitterFn =
        (Function<OdysseyStream<JsonRpcMessage>, SseEmitter>)
            org.mockito.Mockito.mock(Function.class);
    when(emitterFn.apply(odysseyStream)).thenReturn(emitter);

    var stream = new DefaultSseStream(odysseyStream, emitterFn);

    assertThat(stream.createEmitter()).isSameAs(emitter);
    verify(emitterFn).apply(odysseyStream);
  }

  @Test
  void factoryCreateSubscribesWithMapper() {
    SseEmitter emitter = new SseEmitter();
    when(odysseyStream.subscribe(any())).thenReturn(emitter);

    SseStream stream = DefaultSseStream.create(odysseyStream, mapper);

    assertThat(stream.createEmitter()).isSameAs(emitter);
    verify(odysseyStream).subscribe(any());
  }

  @Test
  void factoryCreateWithLastEventIdResumesWithMapper() {
    SseEmitter emitter = new SseEmitter();
    when(odysseyStream.resume(eq("evt-42"), any())).thenReturn(emitter);

    SseStream stream = DefaultSseStream.create(odysseyStream, "evt-42", mapper);

    assertThat(stream.createEmitter()).isSameAs(emitter);
    verify(odysseyStream).resume(eq("evt-42"), any());
  }
}
