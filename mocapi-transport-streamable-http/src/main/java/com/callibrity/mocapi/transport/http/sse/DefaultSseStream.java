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
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.SseEventMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
public class DefaultSseStream implements SseStream {

  // ------------------------------ FIELDS ------------------------------

  private final OdysseyStream<JsonRpcMessage> stream;
  private final Function<OdysseyStream<JsonRpcMessage>, SseEmitter> emitterFn;

  // ------------------------ INTERFACE METHODS ------------------------

  // --------------------- Interface SseStream ---------------------

  public static SseStream create(
      OdysseyStream<JsonRpcMessage> stream, SseEventMapper<JsonRpcMessage> mapper) {
    return new DefaultSseStream(stream, s -> s.subscribe(cfg -> cfg.mapper(mapper)));
  }

  public static SseStream create(
      OdysseyStream<JsonRpcMessage> stream,
      String lastEventId,
      SseEventMapper<JsonRpcMessage> mapper) {
    return new DefaultSseStream(stream, s -> s.resume(lastEventId, cfg -> cfg.mapper(mapper)));
  }

  @Override
  public SseEmitter createEmitter() {
    return emitterFn.apply(stream);
  }

  @Override
  public void write(JsonRpcMessage msg) {
    stream.publish(msg);
  }
}
