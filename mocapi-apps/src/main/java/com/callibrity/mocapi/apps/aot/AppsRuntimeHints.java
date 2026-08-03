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
package com.callibrity.mocapi.apps.aot;

import com.callibrity.mocapi.apps.AppsResourceUiMetaCustomizer;
import com.callibrity.mocapi.apps.AppsToolUiMetaCustomizer;
import com.callibrity.mocapi.apps.McpUiResourceCsp;
import com.callibrity.mocapi.apps.McpUiToolMeta;
import com.callibrity.mocapi.apps.UiResourceMeta;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Jackson binding hints for the {@code io.modelcontextprotocol/ui} extension's {@code
 * _meta.ui} wire records so they survive a GraalVM native-image build.
 *
 * <p>Unlike {@code mocapi-tasks}, {@code mocapi-apps} has no dedicated {@code .model} subpackage to
 * scan — its {@code com.callibrity.mocapi.apps} package mixes wire records with annotations,
 * customizers, and services that never cross the Jackson codec boundary. Rather than widen a
 * package-wide scan to cover a handful of types (and risk pulling in irrelevant classes), this
 * registrar explicitly names the records {@link AppsToolUiMetaCustomizer} and {@link
 * AppsResourceUiMetaCustomizer} hand to {@code ObjectMapper#valueToTree}: {@link McpUiToolMeta}
 * (the tool descriptor's {@code _meta.ui}), {@link UiResourceMeta} (the resource descriptor's
 * {@code _meta.ui}), and its nested {@link McpUiResourceCsp}. This mirrors {@code
 * MocapiRuntimeHints}' explicit non-model registrations ({@code McpExchange}, {@code
 * RequestStatePayload}) for wire types that live outside a scannable model package.
 *
 * <p>mocapi-server's {@code MocapiRuntimeHints} only scans {@code com.callibrity.mocapi.model} —
 * core has no reason to know about extension-owned packages. Each extension that introduces its own
 * wire types is responsible for registering its own hints; this class is {@code mocapi-apps}' half
 * of that contract.
 */
public class AppsRuntimeHints implements RuntimeHintsRegistrar {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(hints.reflection(), McpUiToolMeta.class);
    BINDING.registerReflectionHints(hints.reflection(), UiResourceMeta.class);
    BINDING.registerReflectionHints(hints.reflection(), McpUiResourceCsp.class);
  }
}
