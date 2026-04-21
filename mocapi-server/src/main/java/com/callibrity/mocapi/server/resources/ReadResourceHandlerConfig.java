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
package com.callibrity.mocapi.server.resources;

import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.server.guards.Guard;
import java.lang.reflect.Method;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterResolver;

/**
 * Per-handler configuration view passed to each {@link ReadResourceHandlerCustomizer} while a
 * {@link ReadResourceHandler} is being built. Customizers may inspect the resource descriptor,
 * target method, and target bean, append {@link MethodInterceptor}s to the handler's invocation
 * chain, attach {@link Guard}s that gate visibility and invocation, and register additional {@link
 * ParameterResolver}s that supply values for bespoke parameter types.
 */
public interface ReadResourceHandlerConfig {

  Resource descriptor();

  Method method();

  Object bean();

  /** Adds an interceptor to the CORRELATION stratum. */
  ReadResourceHandlerConfig correlationInterceptor(MethodInterceptor<? super Object> interceptor);

  /** Adds an interceptor to the OBSERVATION stratum. */
  ReadResourceHandlerConfig observationInterceptor(MethodInterceptor<? super Object> interceptor);

  /** Adds an interceptor to the AUDIT stratum. */
  ReadResourceHandlerConfig auditInterceptor(MethodInterceptor<? super Object> interceptor);

  /** Adds an interceptor to the VALIDATION stratum. */
  ReadResourceHandlerConfig validationInterceptor(MethodInterceptor<? super Object> interceptor);

  /** Adds an interceptor to the INVOCATION stratum. */
  ReadResourceHandlerConfig invocationInterceptor(MethodInterceptor<? super Object> interceptor);

  ReadResourceHandlerConfig guard(Guard guard);

  ReadResourceHandlerConfig resolver(ParameterResolver<? super Object> resolver);
}
