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
 * Transport that chooses its HTTP response shape based on the first outbound message. Starts in an
 * {@link OpenWriter} state; a {@link JsonRpcResponse} commits a JSON body and transitions to a
 * {@link TerminalWriter}; a {@link JsonRpcRequest} obtains an {@link SseWriter} from the supplier,
 * commits an SSE body, and transitions to it.
 */
final class LazyHttpTransport implements McpTransport {

  private static final Logger log = LoggerFactory.getLogger(LazyHttpTransport.class);

  private final CompletableFuture<ResponseEntity<Object>> response = new CompletableFuture<>();
  private final Supplier<SseWriter> sseSupplier;
  private final List<Consumer<ResponseEntity<Object>>> decorators = new ArrayList<>();
  private Writer writer = new OpenWriter();

  LazyHttpTransport(Supplier<SseWriter> sseSupplier) {
    this.sseSupplier = sseSupplier;
  }

  public CompletableFuture<ResponseEntity<Object>> response() {
    return response;
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

  sealed interface Writer permits OpenWriter, TerminalWriter, SseWriter {
    Writer send(JsonRpcMessage message);
  }

  final class OpenWriter implements Writer {
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
      response.complete(entity);
      return new TerminalWriter();
    }

    private Writer upgradeToSse(JsonRpcRequest first) {
      SseWriter sseWriter = sseSupplier.get();
      log.info(
          "Upgrading HTTP response to SSE (stream={}, first message type={})",
          sseWriter.id(),
          first.getClass().getSimpleName());
      ResponseEntity<Object> entity =
          ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(sseWriter.emitter());
      decorate(entity);
      response.complete(entity);
      return sseWriter.send(first);
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
    private final SseEmitter emitter;

    SseWriter(OdysseyStream<JsonRpcMessage> stream, SseEmitter emitter) {
      this.stream = stream;
      this.emitter = emitter;
    }

    SseEmitter emitter() {
      return emitter;
    }

    String id() {
      return stream.name();
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
