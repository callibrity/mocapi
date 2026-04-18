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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.jwcarman.odyssey.core.SubscriberConfig;
import org.jwcarman.odyssey.core.SubscriberCustomizer;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultSseStreamTest {

  @Mock OdysseyStream<JsonRpcMessage> odysseyStream;
  @Mock SseEventMapper<JsonRpcMessage> mapper;
  @Mock SubscriberConfig<JsonRpcMessage> subscriberConfig;

  @Test
  void write_delegates_to_odyssey_stream_publish() {
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
  void write_accepts_any_json_rpc_message_type() {
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
  void create_emitter_invokes_emitter_fn_with_underlying_stream() {
    SseEmitter emitter = new SseEmitter();
    Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitterFn = s -> emitter;

    var stream = new DefaultSseStream(odysseyStream, emitterFn);

    assertThat(stream.createEmitter()).isSameAs(emitter);
  }

  @Test
  void factory_create_subscribes_with_mapper() {
    SseEmitter emitter = new SseEmitter();
    when(odysseyStream.subscribe(any())).thenReturn(emitter);

    SseStream stream = DefaultSseStream.create(odysseyStream, mapper);

    assertThat(stream.createEmitter()).isSameAs(emitter);
    verify(odysseyStream).subscribe(any());
  }

  @Test
  void factory_create_with_last_event_id_resumes_with_mapper() {
    SseEmitter emitter = new SseEmitter();
    when(odysseyStream.resume(eq("evt-42"), any())).thenReturn(emitter);

    SseStream stream = DefaultSseStream.create(odysseyStream, "evt-42", mapper);

    assertThat(stream.createEmitter()).isSameAs(emitter);
    verify(odysseyStream).resume(eq("evt-42"), any());
  }

  @Test
  void factory_create_subscribe_passes_mapper_to_subscriber_config() {
    when(odysseyStream.subscribe(any()))
        .thenAnswer(
            inv -> {
              SubscriberCustomizer<JsonRpcMessage> customizer = inv.getArgument(0);
              customizer.accept(subscriberConfig);
              return new SseEmitter();
            });

    SseStream stream = DefaultSseStream.create(odysseyStream, mapper);
    stream.createEmitter();

    verify(subscriberConfig).mapper(mapper);
  }

  @Test
  void factory_create_resume_passes_mapper_to_subscriber_config() {
    when(odysseyStream.resume(eq("evt-42"), any()))
        .thenAnswer(
            inv -> {
              SubscriberCustomizer<JsonRpcMessage> customizer = inv.getArgument(1);
              customizer.accept(subscriberConfig);
              return new SseEmitter();
            });

    SseStream stream = DefaultSseStream.create(odysseyStream, "evt-42", mapper);
    stream.createEmitter();

    verify(subscriberConfig).mapper(mapper);
  }
}
