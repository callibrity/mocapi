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
package com.callibrity.mocapi.server;

import java.util.function.BiFunction;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

/**
 * A method handler is either a simple JSON handler (no stream context needed) or a streaming
 * handler (requires an {@link McpStreamContext} for sending intermediate messages).
 */
public sealed interface McpMethodHandler {
  record Json(Function<JsonNode, Object> handler) implements McpMethodHandler {}

  record Streaming(BiFunction<JsonNode, McpStreamContext, Object> handler)
      implements McpMethodHandler {}
}
