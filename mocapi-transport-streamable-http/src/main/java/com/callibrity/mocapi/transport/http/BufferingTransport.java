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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Transport that buffers messages in a {@link BlockingQueue}, enabling adaptive content-type
 * selection. The controller polls the queue: if the first message is a {@link
 * com.callibrity.ripcurl.core.JsonRpcResponse}, the call completed synchronously and the response
 * is returned as {@code application/json}. If the first message is a notification (progress,
 * logging) or the poll times out (elicitation, sampling), the controller switches to SSE and
 * forwards remaining messages through an Odyssey stream.
 */
class BufferingTransport implements McpTransport {

  private final BlockingQueue<JsonRpcMessage> queue = new LinkedBlockingQueue<>();

  @Override
  public void send(JsonRpcMessage message) {
    queue.add(message);
  }

  @Override
  public void emit(McpEvent event) {
    // Events are a SynchronousTransport concern (SessionInitialized headers).
  }

  /**
   * Polls for the next message, waiting up to the specified timeout. Returns {@code null} if the
   * timeout expires with no message available.
   */
  JsonRpcMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
    return queue.poll(timeout, unit);
  }
}
