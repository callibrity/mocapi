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
package com.callibrity.mocapi.session;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Abstraction over an SSE stream bound to an MCP session. Hides Odyssey's stream and subscriber
 * APIs behind simple publish and subscribe operations with encrypted event IDs.
 */
public interface McpSessionStream {

  void publishJson(Object payload);

  void close();

  SseEmitter subscribe();

  SseEmitter resumeAfter(String lastEventId);
}
