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
import com.callibrity.ripcurl.core.JsonRpcResponse;
import org.jwcarman.odyssey.core.OdysseyPublisher;

/**
 * Transport for post-initialize requests. Wraps an {@link OdysseyPublisher} and publishes every
 * message to the stream. Automatically completes the stream when a {@link JsonRpcResponse} is sent,
 * signaling that the request/response cycle is finished.
 */
class OdysseyTransport implements McpTransport {

  private final OdysseyPublisher<JsonRpcMessage> publisher;
  private boolean completed;

  OdysseyTransport(OdysseyPublisher<JsonRpcMessage> publisher) {
    this.publisher = publisher;
  }

  @Override
  public void send(JsonRpcMessage message) {
    if (completed) {
      throw new IllegalStateException("Transport completed");
    }
    publisher.publish(message);
    if (message instanceof JsonRpcResponse) {
      publisher.complete();
      completed = true;
    }
  }

  @Override
  public void emit(McpEvent event) {
    // No-op — events are a SynchronousTransport concern (SessionInitialized headers).
  }

  /** Returns the underlying stream name, used by the controller to subscribe. */
  String streamName() {
    return publisher.name();
  }
}
