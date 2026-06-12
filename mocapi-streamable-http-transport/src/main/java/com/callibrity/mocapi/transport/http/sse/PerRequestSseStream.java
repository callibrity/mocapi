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

import com.callibrity.ripcurl.core.JsonRpcMessage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Plain Spring {@link SseEmitter}-backed {@link SseStream}, scoped to a single POST response
 * (ADR-0020: Odyssey's named/resumable stream management is gone with spec resumability).
 *
 * <p>Client disconnect is cancellation (ADR-0022): once the emitter completes, errors, or times
 * out, every subsequent {@link #write} is a silent no-op — the server MUST NOT send further
 * messages for the request, and the in-flight handler is not interrupted, so its late messages are
 * simply dropped without exception spam.
 *
 * <p>No emitter timeout is configured ({@code 0L}): the stream's lifetime is the in-flight call,
 * and the final response closes it.
 */
public final class PerRequestSseStream implements SseStream {

  private static final Logger log = LoggerFactory.getLogger(PerRequestSseStream.class);

  private final ObjectMapper objectMapper;
  private final SseEmitter emitter;
  private final AtomicBoolean closed = new AtomicBoolean();

  public PerRequestSseStream(ObjectMapper objectMapper) {
    this(objectMapper, new SseEmitter(0L));
  }

  PerRequestSseStream(ObjectMapper objectMapper, SseEmitter emitter) {
    this.objectMapper = objectMapper;
    this.emitter = emitter;
    emitter.onCompletion(() -> closed.set(true));
    emitter.onTimeout(() -> closed.set(true));
    emitter.onError(t -> closed.set(true));
  }

  @Override
  public SseEmitter createEmitter() {
    return emitter;
  }

  @Override
  public void write(JsonRpcMessage msg) {
    if (closed.get()) {
      log.debug("Response stream closed (client disconnect = cancellation); dropping message");
      return;
    }
    try {
      emitter.send(
          SseEmitter.event()
              .data(objectMapper.writeValueAsString(msg), MediaType.APPLICATION_JSON));
    } catch (IOException | IllegalStateException e) {
      closed.set(true);
      log.debug(
          "Response stream write failed (client disconnect = cancellation): {}", e.getMessage());
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        emitter.complete();
      } catch (IllegalStateException e) {
        log.debug("Response stream already terminated: {}", e.getMessage());
      }
    }
  }
}
