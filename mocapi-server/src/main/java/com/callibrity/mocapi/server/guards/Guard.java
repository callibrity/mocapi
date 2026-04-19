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
package com.callibrity.mocapi.server.guards;

/**
 * Per-handler authorization / visibility check. Guards attach to individual MCP handlers via the
 * customizer SPI; at runtime, a guard that returns {@link GuardDecision.Deny} both hides the
 * handler from list operations and rejects invocation with a JSON-RPC forbidden error.
 *
 * <p>Implementations pull whatever runtime state they need (Spring Security's context, {@code
 * McpSession.CURRENT}, servlet request, etc.) from their own framework of choice — the mocapi core
 * does not pass anything implementation-specific at check time. A guard should typically close over
 * any handler metadata (annotation values, method, bean) at attachment time.
 */
@FunctionalInterface
public interface Guard {

  GuardDecision check();
}
