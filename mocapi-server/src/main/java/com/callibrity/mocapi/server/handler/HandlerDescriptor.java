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
package com.callibrity.mocapi.server.handler;

import java.util.List;

/**
 * Cross-cutting, kind-agnostic description of a built MCP handler's wiring: the kind of handler,
 * the class and method it dispatches to, and the ordered toString forms of the interceptors
 * wrapping the reflective call. Kind-specific metadata (tool input schema, prompt arguments,
 * resource URI, etc.) stays on the MCP model records ({@code Tool}, {@code Prompt}, {@code
 * Resource}, {@code ResourceTemplate}) — this record describes only the handler plumbing.
 */
public record HandlerDescriptor(
    HandlerKind kind, String declaringClassName, String methodName, List<String> interceptors) {

  public HandlerDescriptor {
    interceptors = List.copyOf(interceptors);
  }
}
