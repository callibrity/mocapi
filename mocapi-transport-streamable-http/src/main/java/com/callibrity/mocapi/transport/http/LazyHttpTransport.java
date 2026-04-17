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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Transport that chooses its HTTP response shape based on the first outbound message. Starts in a
 * {@link Pending} state; a {@link JsonRpcResponse} commits a JSON body and transitions to a {@link
 * NullWriter} (no further messages accepted), while a {@link JsonRpcRequest} opens an SSE
 * connection and transitions to a {@link SseWriter} bound to the underlying Odyssey stream.
 */
final class LazyHttpTransport implements McpTransport {

  private static final Logger log = LoggerFactory.getLogger(LazyHttpTransport.class);

  private final Supplier<SseConnection> sseOpener;
  private final List<Consumer<ResponseEntity<Object>>> decorators = new ArrayList<>();
  private Writer writer;

  LazyHttpTransport(
      CompletableFuture<ResponseEntity<Object>> future, Supplier<SseConnection> sseOpener) {
    this.sseOpener = sseOpener;
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

  /**
   * Pair of a stream to publish outbound messages to and the emitter bound to the HTTP response.
   */
  record SseConnection(OdysseyStream<JsonRpcMessage> stream, SseEmitter emitter) {}

  sealed interface Writer permits Pending, NullWriter, SseWriter {
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
      return new NullWriter();
    }

    private Writer upgradeToSse(JsonRpcRequest first) {
      SseConnection conn = sseOpener.get();
      log.info(
          "Upgrading HTTP response to SSE (stream={}, first message type={})",
          conn.stream().name(),
          first.getClass().getSimpleName());
      ResponseEntity<Object> entity =
          ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(conn.emitter());
      decorate(entity);
      future.complete(entity);
      conn.stream().publish(first);
      return new SseWriter(conn.stream());
    }
  }

  static final class NullWriter implements Writer {
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
