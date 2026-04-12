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
package com.callibrity.mocapi.protocol.tools;

import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import tools.jackson.databind.JsonNode;

/**
 * Resolves {@link McpToolContext} parameters in tool methods by reading from the {@link
 * McpToolContext#CURRENT} {@link ScopedValue}.
 */
public class McpToolContextScopedValueResolver implements ParameterResolver<JsonNode> {

  @Override
  public boolean supports(ParameterInfo info) {
    return McpToolContext.class.isAssignableFrom(info.resolvedType());
  }

  @Override
  public Object resolve(ParameterInfo info, JsonNode params) {
    return McpToolContext.CURRENT.isBound() ? McpToolContext.CURRENT.get() : null;
  }
}
