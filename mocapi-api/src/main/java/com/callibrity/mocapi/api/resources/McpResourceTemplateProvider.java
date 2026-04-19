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
package com.callibrity.mocapi.api.resources;

import java.util.List;

/**
 * Contributes a batch of {@link McpResourceTemplate}s to the server's resource-template registry.
 * Every Spring bean that implements this interface is picked up at startup; the returned templates
 * are merged into the catalog exposed through {@code resources/templates/list}.
 *
 * <p>For fixed (non-parameterized) URIs, use {@link McpResourceProvider}. Most applications declare
 * resource templates with {@code @ResourceTemplateMethod} and never implement this SPI directly.
 */
@FunctionalInterface
public interface McpResourceTemplateProvider {
  /** Returns the resource templates this provider contributes. Called once at startup. */
  List<McpResourceTemplate> getMcpResourceTemplates();
}
