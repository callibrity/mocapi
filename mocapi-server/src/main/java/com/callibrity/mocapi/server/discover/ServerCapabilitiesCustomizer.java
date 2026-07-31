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
package com.callibrity.mocapi.server.discover;

import com.callibrity.mocapi.model.ServerCapabilities;

/**
 * Contributes to the server's declared {@link ServerCapabilities} at startup (ADR-0031). Beans of
 * this type are collected and applied — in bean order — to a {@link ServerCapabilities.Builder}
 * before the {@code server/discover} response is assembled. The canonical use is an optional
 * extension module declaring its capability without replacing the whole {@code ServerCapabilities}
 * bean, e.g.:
 *
 * <pre>{@code
 * @Bean
 * ServerCapabilitiesCustomizer tasksCapability(ObjectMapper mapper) {
 *   return caps -> caps.extension("io.modelcontextprotocol/tasks", mapper.createObjectNode());
 * }
 * }</pre>
 *
 * <p>A deployment that supplies its own {@code ServerCapabilities} bean replaces the default
 * outright; in that case customizers are not applied (the override is authoritative).
 */
@FunctionalInterface
public interface ServerCapabilitiesCustomizer {

  /**
   * Applies this customization to the capabilities builder. Implementations typically call {@link
   * ServerCapabilities.Builder#extension(String, tools.jackson.databind.node.ObjectNode)}.
   *
   * @param capabilities the builder to mutate; never {@code null}
   */
  void customize(ServerCapabilities.Builder capabilities);
}
