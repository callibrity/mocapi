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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Transport for the initialize handshake (no session ID). Buffers a single {@link JsonRpcResponse}
 * and captures the {@link McpEvent.SessionInitialized} event so the controller can set the {@code
 * MCP-Session-Id} response header. Throws on non-response messages.
 */
class SynchronousTransport implements McpTransport {

  private JsonRpcResponse response;
  private McpEvent.SessionInitialized sessionInitialized;

  @Override
  public void send(JsonRpcMessage message) {
    if (!(message instanceof JsonRpcResponse resp)) {
      throw new IllegalArgumentException(
          "SynchronousTransport only supports response messages, got: "
              + message.getClass().getSimpleName());
    }
    if (this.response != null) {
      throw new IllegalStateException("SynchronousTransport already holds a response");
    }
    this.response = resp;
  }

  @Override
  public void emit(McpEvent event) {
    if (event instanceof McpEvent.SessionInitialized si) {
      this.sessionInitialized = si;
    }
  }

  /**
   * Builds a {@link ResponseEntity} from the buffered response. If a {@link
   * McpEvent.SessionInitialized} was captured, the {@code MCP-Session-Id} header is set.
   */
  ResponseEntity<Object> toResponseEntity() {
    var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
    if (sessionInitialized != null) {
      builder.header("MCP-Session-Id", sessionInitialized.sessionId());
    }
    return builder.body(response);
  }
}
