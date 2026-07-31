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

import com.callibrity.mocapi.model.Tool;
import java.lang.reflect.Method;

/**
 * Enriches a tool's {@link Tool} descriptor at build time (ADR-0034). Applied after the descriptor
 * is generated and before the handler is assembled; implementations return the same descriptor or a
 * copy carrying additional {@code _meta} (e.g. {@code mocapi-apps} writing {@code _meta.ui}). Core
 * does not interpret the enrichment.
 */
@FunctionalInterface
public interface ToolDescriptorCustomizer {
  Tool customize(Method method, Tool descriptor);
}
