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
import static org.assertj.core.api.Assertions.assertThatCode;

import com.callibrity.ripcurl.core.JsonRpcResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PerRequestSseStreamTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private RecordingEmitter emitter;
  private PerRequestSseStream stream;

  @BeforeEach
  void set_up() {
    emitter = new RecordingEmitter();
    stream = new PerRequestSseStream(objectMapper, emitter);
  }

  private static JsonRpcResult okResult() {
    return new JsonRpcResult(
        JsonNodeFactory.instance.objectNode().put("k", "v"),
        JsonNodeFactory.instance.numberNode(1));
  }

  @Nested
  class Write {

    @Test
    void serializes_the_message_as_an_sse_data_event() {
      stream.write(okResult());

      assertThat(emitter.sentData).hasSize(1);
      assertThat(emitter.sentData.get(0))
          .contains("\"jsonrpc\":\"2.0\"")
          .contains("\"result\":{\"k\":\"v\"}")
          .contains("\"id\":1");
    }

    @Test
    void after_client_disconnect_writes_are_silently_dropped() {
      emitter.failNextSend = true;

      stream.write(okResult());
      assertThatCode(() -> stream.write(okResult())).doesNotThrowAnyException();

      // first write hit the broken pipe, second was dropped without touching the emitter
      assertThat(emitter.sendAttempts).isEqualTo(1);
    }

    @Test
    void after_close_writes_are_silently_dropped() {
      stream.close();

      assertThatCode(() -> stream.write(okResult())).doesNotThrowAnyException();
      assertThat(emitter.sentData).isEmpty();
    }
  }

  @Nested
  class Close {

    @Test
    void completes_the_emitter() {
      stream.close();

      assertThat(emitter.completed).isTrue();
    }

    @Test
    void is_idempotent() {
      stream.close();

      assertThatCode(() -> stream.close()).doesNotThrowAnyException();
      assertThat(emitter.completions).isEqualTo(1);
    }
  }

  @Test
  void create_emitter_returns_the_per_request_emitter() {
    assertThat(stream.createEmitter()).isSameAs(emitter);
  }

  /** Captures sends without needing the Spring MVC async machinery. */
  private static final class RecordingEmitter extends SseEmitter {
    private final List<String> sentData = new ArrayList<>();
    private int sendAttempts;
    private int completions;
    private boolean completed;
    private boolean failNextSend;

    private RecordingEmitter() {
      super(0L);
    }

    @Override
    public void send(SseEventBuilder builder) throws IOException {
      sendAttempts++;
      if (failNextSend) {
        failNextSend = false;
        throw new IOException("broken pipe");
      }
      sentData.add(
          builder.build().stream()
              .map(d -> String.valueOf(d.getData()))
              .reduce("", String::concat));
    }

    @Override
    public synchronized void complete() {
      completions++;
      completed = true;
    }
  }
}
