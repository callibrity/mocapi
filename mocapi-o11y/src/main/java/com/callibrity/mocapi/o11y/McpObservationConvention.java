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
package com.callibrity.mocapi.o11y;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;

/**
 * Default {@link ObservationConvention} for MCP handler observations. Contributes the
 * low-cardinality tags {@code mcp.handler.kind} and {@code mcp.handler.name} to any {@link
 * McpObservationContext}. Matches the tags the built-in {@link McpObservationInterceptor} sets
 * inline, and is exported so users who prefer to apply {@code @Observed} to their own methods can
 * pass this convention to the {@link io.micrometer.observation.annotation.Observed} machinery (or
 * replace it with a custom convention that adds handler-specific tags).
 */
public class McpObservationConvention implements ObservationConvention<McpObservationContext> {

  /** Tag key for the handler kind (tool / prompt / resource / resource_template). */
  public static final String HANDLER_KIND = "mcp.handler.kind";

  /** Tag key for the handler name (tool / prompt name, resource URI, or URI template). */
  public static final String HANDLER_NAME = "mcp.handler.name";

  @Override
  public boolean supportsContext(Observation.Context context) {
    return context instanceof McpObservationContext;
  }

  @Override
  public KeyValues getLowCardinalityKeyValues(McpObservationContext context) {
    return KeyValues.of(HANDLER_KIND, context.handlerKind(), HANDLER_NAME, context.handlerName());
  }
}
