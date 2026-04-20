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
package com.callibrity.mocapi.server.util;

import java.util.Optional;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

/**
 * Abstract base for {@link ParameterResolver} implementations that resolve a method parameter from
 * a {@link ScopedValue}. The argument type is {@code Object} because the resolver never inspects
 * the invocation argument — it reads exclusively from the bound {@link ScopedValue}. Subclass with
 * a concrete type to ensure runtime type discovery works:
 *
 * <pre>{@code
 * public class McpToolContextResolver extends ScopedValueResolver<McpToolContext> {
 *   public McpToolContextResolver() {
 *     super(McpToolContext.class, McpToolContext.CURRENT);
 *   }
 * }
 * }</pre>
 *
 * @param <T> the type of value held by the ScopedValue
 */
public abstract class ScopedValueResolver<T> implements ParameterResolver<Object> {

  private final Class<T> type;
  private final ScopedValue<T> scopedValue;

  protected ScopedValueResolver(Class<T> type, ScopedValue<T> scopedValue) {
    this.type = type;
    this.scopedValue = scopedValue;
  }

  @Override
  public Optional<Binding<Object>> bind(ParameterInfo info) {
    if (!type.isAssignableFrom(info.resolvedType())) {
      return Optional.empty();
    }
    return Optional.of(argument -> scopedValue.isBound() ? scopedValue.get() : null);
  }
}
