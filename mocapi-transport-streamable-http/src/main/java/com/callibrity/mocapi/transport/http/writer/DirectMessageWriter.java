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
package com.callibrity.mocapi.transport.http.writer;

import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RequiredArgsConstructor
@Slf4j
public final class DirectMessageWriter implements MessageWriter {

  // ------------------------------ FIELDS ------------------------------

  private final Supplier<SseStream> sseStreamProvider;
  private final Consumer<ResponseEntity<Object>> responseConsumer;

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface MessageWriter ---------------------

  @Override
  public MessageWriter write(JsonRpcMessage msg) {
    return switch (msg) {
      case JsonRpcResponse resp -> {
        log.trace("Direct → Closed (JSON response id={})", resp.id());
        responseConsumer.accept(
            ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp));
        yield ClosedMessageWriter.INSTANCE;
      }
      case JsonRpcRequest req -> {
        log.trace("Direct → SSE (first message type={})", req.getClass().getSimpleName());
        var stream = sseStreamProvider.get();
        responseConsumer.accept(
            ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream.createEmitter()));
        yield new SseMessageWriter(stream).write(req);
      }
    };
  }
}
