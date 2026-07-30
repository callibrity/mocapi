/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * A per-request SSE response stream (MCP 2026-07-28): zero or more request-scoped notifications,
 * then the final JSON-RPC response, then the stream closes. There are no named streams, no stream
 * resumption, and no event IDs — the spec removed {@code Last-Event-ID} resumability (ADR-0020).
 */
public interface SseStream {

  /** The emitter to hand to Spring MVC as the response body. */
  SseEmitter createEmitter();

  /** Writes one JSON-RPC message as an SSE event. A no-op once the stream is closed. */
  void write(JsonRpcMessage msg);

  /** Terminates the stream. The final response should be followed by a close. */
  void close();
}
