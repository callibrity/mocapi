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

import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

/**
 * Generic {@link ParameterResolver} that resolves a method parameter from a {@link ScopedValue}.
 * Eliminates the need for a custom resolver class per injectable type.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * new ScopedValueResolver<>(McpToolContext.class, McpToolContext.CURRENT)
 * new ScopedValueResolver<>(McpTransport.class, McpTransport.CURRENT)
 * new ScopedValueResolver<>(McpSession.class, McpSession.CURRENT)
 * }</pre>
 *
 * @param <T> the type of value held by the ScopedValue
 * @param <A> the argument type passed to the resolver by the invoker
 */
public class ScopedValueResolver<T, A> implements ParameterResolver<A> {

  private final Class<T> type;
  private final ScopedValue<T> scopedValue;

  public ScopedValueResolver(Class<T> type, ScopedValue<T> scopedValue) {
    this.type = type;
    this.scopedValue = scopedValue;
  }

  @Override
  public boolean supports(ParameterInfo info) {
    return type.isAssignableFrom(info.resolvedType());
  }

  @Override
  public Object resolve(ParameterInfo info, A params) {
    return scopedValue.isBound() ? scopedValue.get() : null;
  }
}
