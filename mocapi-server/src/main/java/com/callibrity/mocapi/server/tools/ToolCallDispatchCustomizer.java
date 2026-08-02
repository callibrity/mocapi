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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.CallToolRequestParams;
import java.util.Optional;

/**
 * Intercepts a {@code tools/call} request after handler lookup and before the default MRTR
 * invocation path, letting an extension (e.g. {@code mocapi-tasks}) reroute eligible calls to an
 * alternate execution model instead of the direct handler invocation. Registered customizers are
 * consulted in bean order; the first one to return a non-empty {@link Optional} short-circuits the
 * request and its value becomes the {@code tools/call} response as-is. Core stays
 * extension-agnostic: it neither inspects nor interprets the claimed result.
 */
@FunctionalInterface
public interface ToolCallDispatchCustomizer {
  /**
   * Returns the full tools/call response for this request, or {@link Optional#empty()} to fall
   * through. Consulted in bean order after handler lookup, before the default MRTR path.
   */
  Optional<Object> dispatch(CallToolHandler handler, CallToolRequestParams params);
}
