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

import com.callibrity.mocapi.transport.http.HttpStatusMapping;
import com.callibrity.mocapi.transport.http.sse.SseStream;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Initial writer state: the response shape is not yet committed (ADR-0004 lazy JSON-vs-SSE). A
 * final {@link JsonRpcResponse} first commits a plain {@code application/json} reply with the HTTP
 * status mapped from the JSON-RPC error code ({@link HttpStatusMapping}); a request-scoped
 * notification first commits a per-request SSE stream ({@code X-Accel-Buffering: no}) and replays
 * onto it.
 */
@RequiredArgsConstructor
public final class DirectMessageWriter implements MessageWriter {

  /** Disables proxy response buffering so SSE events flush through nginx-style intermediaries. */
  public static final String X_ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

  private final Supplier<SseStream> sseStreamProvider;
  private final Consumer<ResponseEntity<Object>> responseConsumer;

  @Override
  public MessageWriter write(JsonRpcMessage msg) {
    return switch (msg) {
      case JsonRpcResponse resp -> {
        responseConsumer.accept(
            ResponseEntity.status(HttpStatusMapping.forResponse(resp))
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp));
        yield ClosedMessageWriter.INSTANCE;
      }
      case JsonRpcRequest req -> {
        var stream = sseStreamProvider.get();
        responseConsumer.accept(
            ResponseEntity.ok()
                .header(X_ACCEL_BUFFERING_HEADER, "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream.createEmitter()));
        yield new SseMessageWriter(stream).write(req);
      }
    };
  }
}
