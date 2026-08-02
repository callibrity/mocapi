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
package com.callibrity.mocapi.server.dispatch;

import java.util.function.Supplier;

@FunctionalInterface
public interface McpDispatchInterceptor<H, P> {
  /**
   * Intercepts one dispatch of an MRTR-capable method. Return proceed.get() (optionally decorated)
   * to continue the chain; return a different response Object to own the call; throw to abort with
   * a JSON-RPC error. Interceptors are ordered by @Order/Ordered; lower values run outermost. Runs
   * BEFORE the handler chain — and therefore before guards and schema validation; an interceptor
   * that does not call proceed() owns those responsibilities itself (see the Extending-mocapi
   * guide).
   */
  Object intercept(H handler, P params, Supplier<Object> proceed);
}
