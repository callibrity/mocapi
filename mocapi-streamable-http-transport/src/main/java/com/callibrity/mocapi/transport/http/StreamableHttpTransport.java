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
package com.callibrity.mocapi.transport.http;

import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.mocapi.transport.http.writer.DirectMessageWriter;
import com.callibrity.mocapi.transport.http.writer.MessageWriter;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * Adapts the {@link MessageWriter} state machine to the {@link McpTransport} SPI for a single POST
 * request. Holds a future that resolves to the HTTP response once the first outbound message
 * commits the response shape (plain JSON for an immediate response, SSE when request-scoped
 * notifications come first — ADR-0004). Strictly per-request: MCP 2026-07-28 has no sessions and no
 * cross-request delivery channel (ADR-0020).
 */
final class StreamableHttpTransport implements McpTransport {

  private final Logger log = LoggerFactory.getLogger(StreamableHttpTransport.class);

  private final CompletableFuture<ResponseEntity<Object>> response = new CompletableFuture<>();
  private MessageWriter writer;
  private SseStream committedStream;

  StreamableHttpTransport(Supplier<SseStream> sseStreamProvider) {
    // Capture the SSE stream the moment it is created so abort() can release the emitter if a
    // dispatch failure happens after the response has already committed as SSE.
    Supplier<SseStream> capturing =
        () -> {
          SseStream stream = sseStreamProvider.get();
          this.committedStream = stream;
          return stream;
        };
    this.writer = new DirectMessageWriter(capturing, this::commit);
  }

  public CompletableFuture<ResponseEntity<Object>> response() {
    return response;
  }

  @Override
  public void send(JsonRpcMessage message) {
    writer = writer.write(message);
  }

  /**
   * Aborts the request after a dispatch failure. Before the response shape commits this fails the
   * pending future (a plain error reply); once an SSE stream has committed the future is already
   * resolved, so instead the committed stream is closed to release the emitter — otherwise a
   * post-commit throw would leak the connection (no SSE timeout is configured).
   */
  void abort(Throwable t) {
    // completeExceptionally returns false when the future is already settled — i.e. the response
    // shape has committed. In that case the only way to end the request is to close the committed
    // SSE stream (a JSON reply has nothing left to release).
    if (!response.completeExceptionally(t) && committedStream != null) {
      committedStream.close();
    }
  }

  private void commit(ResponseEntity<Object> entity) {
    log.debug("Committing {} response", entity.getHeaders().getContentType());
    response.complete(entity);
  }
}
