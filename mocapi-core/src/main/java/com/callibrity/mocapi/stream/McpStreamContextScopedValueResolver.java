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
package com.callibrity.mocapi.stream;

import com.callibrity.ripcurl.core.invoke.JsonRpcParamResolver;
import java.lang.reflect.Parameter;
import tools.jackson.databind.JsonNode;

/**
 * Resolves {@link McpStreamContext} parameters in {@code @Tool} methods by reading from the {@link
 * McpStreamContext#CURRENT} {@link ScopedValue}. Used by {@link
 * com.callibrity.mocapi.tools.annotation.AnnotationMcpTool} when constructing its method invoker.
 */
public class McpStreamContextScopedValueResolver implements JsonRpcParamResolver {

  @Override
  public Object resolve(Parameter parameter, int index, JsonNode params) {
    if (!McpStreamContext.class.isAssignableFrom(parameter.getType())) {
      return null;
    }
    return McpStreamContext.CURRENT.isBound() ? McpStreamContext.CURRENT.get() : null;
  }
}
