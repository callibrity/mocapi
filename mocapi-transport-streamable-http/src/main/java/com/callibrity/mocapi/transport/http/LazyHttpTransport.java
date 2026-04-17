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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jwcarman.odyssey.core.Odyssey;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SubscriberConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Transport that chooses its HTTP response shape based on the first outbound message. Starts in a
 * {@link Pending} state; a {@link JsonRpcResponse} commits a JSON body and transitions to a {@link
 * NullWriter} (no further messages accepted), while a {@link JsonRpcRequest} opens an SSE stream
 * and transitions to an {@link SseWriter} that publishes subsequent messages through the stream.
 */
final class LazyHttpTransport implements McpTransport {

  private static final Logger log = LoggerFactory.getLogger(LazyHttpTransport.class);

  private final Odyssey odyssey;
  private final BiConsumer<SubscriberConfig<JsonRpcMessage>, OdysseyStream<JsonRpcMessage>>
      subscribeConfigurer;
  private final List<Consumer<ResponseEntity<Object>>> decorators = new ArrayList<>();
  private Writer writer;

  LazyHttpTransport(
      CompletableFuture<ResponseEntity<Object>> future,
      Odyssey odyssey,
      BiConsumer<SubscriberConfig<JsonRpcMessage>, OdysseyStream<JsonRpcMessage>>
          subscribeConfigurer) {
    this.odyssey = odyssey;
    this.subscribeConfigurer = subscribeConfigurer;
    this.writer = new Pending(future);
  }

  @Override
  public void send(JsonRpcMessage message) {
    writer = writer.send(message);
  }

  @Override
  public void emit(McpEvent event) {
    if (event instanceof McpEvent.SessionInitialized si) {
      decorators.add(entity -> entity.getHeaders().add("MCP-Session-Id", si.sessionId()));
    }
  }

  private void decorate(ResponseEntity<Object> entity) {
    decorators.forEach(d -> d.accept(entity));
  }

  sealed interface Writer permits Pending, TerminalWriter, SseWriter {
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
      log.debug("Committing JSON response for request id={}", resp.id());
      ResponseEntity<Object> entity =
          ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
      decorate(entity);
      future.complete(entity);
      return new TerminalWriter();
    }

    private Writer upgradeToSse(JsonRpcRequest first) {
      var stream = odyssey.stream(UUID.randomUUID().toString(), JsonRpcMessage.class);
      log.info(
          "Upgrading HTTP response to SSE (stream={}, first message type={})",
          stream.name(),
          first.getClass().getSimpleName());
      var emitter = stream.subscribe(cfg -> subscribeConfigurer.accept(cfg, stream));
      ResponseEntity<Object> entity =
          ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
      decorate(entity);
      future.complete(entity);
      stream.publish(first);
      return new SseWriter(stream);
    }
  }

  static final class TerminalWriter implements Writer {
    @Override
    public Writer send(JsonRpcMessage message) {
      throw new IllegalStateException(
          "Transport is terminal; cannot send " + message.getClass().getSimpleName());
    }
  }

  static final class SseWriter implements Writer {
    private final OdysseyStream<JsonRpcMessage> stream;

    SseWriter(OdysseyStream<JsonRpcMessage> stream) {
      this.stream = stream;
    }

    @Override
    public Writer send(JsonRpcMessage message) {
      stream.publish(message);
      if (message instanceof JsonRpcResponse) {
        stream.complete();
        return new TerminalWriter();
      }
      return this;
    }
  }
}
