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

import com.callibrity.mocapi.server.McpEvent;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Transport that chooses its HTTP response shape based on the first outbound message. Starts in a
 * {@link Pending} state; the first {@link JsonRpcResponse} commits a JSON body and the first {@link
 * JsonRpcRequest} upgrades to an SSE stream. Subsumes the behavior of the former {@code
 * SynchronousTransport} and {@code OdysseyTransport}.
 */
final class LazyHttpTransport implements McpTransport {

  private final Supplier<OdysseyStream<JsonRpcMessage>> streams;
  private final Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitters;
  private Writer writer;
  private McpEvent.SessionInitialized sessionInitialized;

  LazyHttpTransport(
      CompletableFuture<ResponseEntity<Object>> future,
      Supplier<OdysseyStream<JsonRpcMessage>> streams,
      Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitters) {
    this.streams = streams;
    this.emitters = emitters;
    this.writer = new Pending(future);
  }

  @Override
  public void send(JsonRpcMessage message) {
    writer = writer.send(message);
  }

  @Override
  public void emit(McpEvent event) {
    if (event instanceof McpEvent.SessionInitialized si) {
      sessionInitialized = si;
    }
  }

  sealed interface Writer permits Pending, JsonWriter, SseWriter {
    Writer send(JsonRpcMessage message);
  }

  final class Pending implements Writer {
    private final CompletableFuture<ResponseEntity<Object>> future;

    Pending(CompletableFuture<ResponseEntity<Object>> future) {
      this.future = future;
    }

    @Override
    public Writer send(JsonRpcMessage message) {
      return switch (message) {
        case JsonRpcResponse resp -> commitJson(resp);
        case JsonRpcRequest req -> upgradeToSse(req);
      };
    }

    private Writer commitJson(JsonRpcResponse resp) {
      var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
      if (sessionInitialized != null) {
        builder.header("MCP-Session-Id", sessionInitialized.sessionId());
      }
      future.complete(builder.body(resp));
      return new JsonWriter();
    }

    private Writer upgradeToSse(JsonRpcRequest first) {
      var stream = streams.get();
      var emitter = emitters.apply(stream);
      var builder = ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM);
      if (sessionInitialized != null) {
        builder.header("MCP-Session-Id", sessionInitialized.sessionId());
      }
      future.complete(builder.body(emitter));
      stream.publish(first);
      return new SseWriter(stream);
    }
  }

  static final class JsonWriter implements Writer {
    @Override
    public Writer send(JsonRpcMessage message) {
      throw new IllegalStateException(
          "JSON response already committed; cannot send " + message.getClass().getSimpleName());
    }
  }

  static final class SseWriter implements Writer {
    private final OdysseyStream<JsonRpcMessage> stream;
    private boolean completed;

    SseWriter(OdysseyStream<JsonRpcMessage> stream) {
      this.stream = stream;
    }

    @Override
    public Writer send(JsonRpcMessage message) {
      if (completed) {
        throw new IllegalStateException("Stream completed");
      }
      stream.publish(message);
      if (message instanceof JsonRpcResponse) {
        stream.complete();
        completed = true;
      }
      return this;
    }
  }
}
