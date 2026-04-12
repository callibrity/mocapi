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

import com.callibrity.mocapi.protocol.McpEvent;
import com.callibrity.mocapi.protocol.McpTransport;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Transport that buffers all messages during synchronous protocol dispatch. After dispatch
 * completes, the controller inspects the buffer to decide between a JSON response (single message)
 * and an SSE stream (multiple messages).
 */
class BufferingTransport implements McpTransport {

  private final List<JsonRpcMessage> messages = new ArrayList<>();

  @Override
  public void send(JsonRpcMessage message) {
    messages.add(message);
  }

  @Override
  public void emit(McpEvent event) {
    // No-op — events are a SynchronousTransport concern (SessionInitialized headers).
  }

  /**
   * Returns true if the buffer contains exactly one message and it is a {@link JsonRpcResponse}.
   */
  boolean isSimpleResponse() {
    return messages.size() == 1 && messages.getFirst() instanceof JsonRpcResponse;
  }

  /** Returns the single response. Only valid when {@link #isSimpleResponse()} is true. */
  JsonRpcResponse singleResponse() {
    return (JsonRpcResponse) messages.getFirst();
  }

  /** Returns all buffered messages in order. */
  List<JsonRpcMessage> messages() {
    return List.copyOf(messages);
  }
}
