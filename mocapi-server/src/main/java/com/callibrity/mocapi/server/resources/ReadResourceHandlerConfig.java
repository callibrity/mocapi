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

  /**
   * Replaces the resource descriptor that will be advertised for this handler. Customizers call
   * this to fold in additional {@code _meta} or other descriptor changes (ADR-0039); the last
   * customizer to call it wins. {@code descriptor} must not be {@code null}.
   *
   * <p><strong>Identity contract:</strong> the replacement must preserve {@link Resource#uri()}.
   * Registration identity ({@code uri}) is snapshotted from the descriptor at build time by other
   * customizers in the same chain (o11y, audit, logging, validation, guards commonly close over
   * {@code descriptor().uri()}); replacing it with a different URI desynchronizes those closures
   * from the handler actually registered. This mutator exists to replace metadata ({@code name},
   * {@code description}, {@code mimeType}, {@code _meta}); replacing identity is unsupported and
   * done at the customizer's own risk.
   */
  void descriptor(Resource descriptor);

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
