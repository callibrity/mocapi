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
import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.mocapi.transport.http.writer.DirectMessageWriter;
import com.callibrity.mocapi.transport.http.writer.MessageWriter;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;

/**
 * Adapts the {@link MessageWriter} state machine to the {@link McpTransport} SPI. Holds a future
 * that resolves to the HTTP response once the first outbound message commits the response shape.
 */
final class StreamableHttpTransport implements McpTransport {

  public static final String SESSION_ID_HEADER = "MCP-Session-Id";

  private final Logger log = LoggerFactory.getLogger(StreamableHttpTransport.class);

  private final CompletableFuture<ResponseEntity<Object>> response = new CompletableFuture<>();
  private final List<Consumer<ResponseEntity<Object>>> decorators = new ArrayList<>();
  private MessageWriter writer;

  StreamableHttpTransport(Supplier<SseStream> sseStreamProvider) {
    this.writer = new DirectMessageWriter(sseStreamProvider, this::commit);
  }

  public CompletableFuture<ResponseEntity<Object>> response() {
    return response;
  }

  @Override
  public void send(JsonRpcMessage message) {
    writer = writer.write(message);
  }

  @Override
  public void emit(McpEvent event) {
    if (event instanceof McpEvent.SessionInitialized si) {
      decorators.add(entity -> entity.getHeaders().add(SESSION_ID_HEADER, si.sessionId()));
    }
  }

  private void commit(ResponseEntity<Object> entity) {
    log.debug("Committing {} response", entity.getHeaders().getContentType());
    decorators.forEach(d -> d.accept(entity));
    response.complete(entity);
  }
}
