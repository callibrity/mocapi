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
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
public final class SseMessageWriter implements MessageWriter {

  private final SseStream sseStream;

  @Override
  public MessageWriter write(JsonRpcMessage msg) {
    return switch (msg) {
      case JsonRpcResponse resp -> {
        sseStream.write(resp);
        yield ClosedMessageWriter.INSTANCE;
      }
      case JsonRpcRequest req -> {
        sseStream.write(req);
        yield this;
      }
    };
  }

  public SseEmitter emitter() {
    return sseStream.createEmitter();
  }
}
