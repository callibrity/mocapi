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
package com.callibrity.mocapi.api.tools;

import java.util.List;

/**
 * Contributes a batch of {@link McpTool}s to the server's tool registry. Every Spring bean that
 * implements this interface is picked up at startup; the returned tools are merged into a single
 * catalog exposed through {@code tools/list}.
 *
 * <p>Most applications never implement this directly — the {@code @ToolService} /
 * {@code @ToolMethod} annotations generate a provider per annotated bean. Implement this SPI when
 * you need to register tools programmatically (dynamic catalogs, tools loaded from configuration,
 * etc.).
 */
@FunctionalInterface
public interface McpToolProvider {
  /** Returns the tools this provider contributes. Called once at startup. */
  List<McpTool> getMcpTools();
}
