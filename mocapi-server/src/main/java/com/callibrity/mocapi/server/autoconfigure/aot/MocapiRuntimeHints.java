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
package com.callibrity.mocapi.server.autoconfigure.aot;

import com.callibrity.mocapi.server.session.McpSession;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers binding hints for Mocapi-owned types that round-trip through a Codec boundary without
 * appearing in a {@code @JsonRpcMethod} signature. Ripcurl ships its own registrar for the {@code
 * JsonRpcMessage} hierarchy; this registrar covers the one type Mocapi is responsible for:
 *
 * <ul>
 *   <li>{@link McpSession} — written to the Substrate atom store by {@code AtomMcpSessionStore}.
 * </ul>
 */
public class MocapiRuntimeHints implements RuntimeHintsRegistrar {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(hints.reflection(), McpSession.class);
  }
}
