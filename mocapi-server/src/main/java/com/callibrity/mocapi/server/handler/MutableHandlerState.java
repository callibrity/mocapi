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
package com.callibrity.mocapi.server.handler;

import com.callibrity.mocapi.server.guards.Guard;
import java.util.ArrayList;
import java.util.List;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterResolver;

/**
 * Shared mutable accumulator for the per-handler configuration surface each {@code *HandlerConfig}
 * implementation exposes. Holds one list per interceptor stratum plus guards and parameter
 * resolvers. Kept out of the individual {@code MutableConfig} classes so the per-handler builders
 * stay thin wrappers — just forwarding mutator calls here — rather than repeating the same seven
 * fields and getters.
 *
 * @param <T> the handler argument type ({@code JsonNode} for tools, {@code Map<String, String>} for
 *     prompts and resource templates, {@code Object} for resources)
 */
public final class MutableHandlerState<T> {

  public final List<MethodInterceptor<? super T>> correlation = new ArrayList<>();
  public final List<MethodInterceptor<? super T>> observation = new ArrayList<>();
  public final List<MethodInterceptor<? super T>> audit = new ArrayList<>();
  public final List<MethodInterceptor<? super T>> validation = new ArrayList<>();
  public final List<MethodInterceptor<? super T>> invocation = new ArrayList<>();
  public final List<Guard> guards = new ArrayList<>();
  public final List<ParameterResolver<? super T>> resolvers = new ArrayList<>();
}
