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
package com.callibrity.mocapi.server.tools;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.guards.Guard;
import java.lang.reflect.Method;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.databind.JsonNode;

/**
 * Per-handler configuration view passed to each {@link CallToolHandlerCustomizer} while a {@link
 * CallToolHandler} is being built. Customizers inspect the tool descriptor, target method, and
 * target bean, and contribute {@link MethodInterceptor}s to one of several named strata so the
 * handler builder can assemble them in a stable outer-to-inner order:
 *
 * <ol>
 *   <li>{@link #correlationInterceptor correlation} — MDC, request id propagation
 *   <li>{@link #observationInterceptor observation} — traces, metrics
 *   <li>{@link #auditInterceptor audit} — persistent record of each attempt
 *   <li>AUTHORIZATION — {@link #guard(Guard) guards}, wired by the builder into a single evaluation
 *       interceptor so denied calls short-circuit with {@code -32003}
 *   <li>VALIDATION — the tool's compiled input JSON schema (wired by the builder), followed by any
 *       customizer-contributed {@link #validationInterceptor validation} interceptors
 *   <li>{@link #invocationInterceptor invocation} — escape hatch that wraps the reflective call
 *       (retries, timeouts)
 * </ol>
 *
 * <p>Within one stratum, customizer contributions land in the order their customizer beans are
 * consulted; that matches Spring's {@code @Order} sort when running inside an application context.
 */
public interface CallToolHandlerConfig {

  Tool descriptor();

  Method method();

  Object bean();

  /** Adds an interceptor to the CORRELATION stratum (MDC, request-id propagation). */
  CallToolHandlerConfig correlationInterceptor(MethodInterceptor<? super JsonNode> interceptor);

  /** Adds an interceptor to the OBSERVATION stratum (spans, metrics). */
  CallToolHandlerConfig observationInterceptor(MethodInterceptor<? super JsonNode> interceptor);

  /** Adds an interceptor to the AUDIT stratum (structured outcome record per attempt). */
  CallToolHandlerConfig auditInterceptor(MethodInterceptor<? super JsonNode> interceptor);

  /**
   * Adds an interceptor to the VALIDATION stratum (semantic validation — Jakarta Bean Validation,
   * cross-field checks). Runs after the tool's JSON input-schema check, so a JSON-schema miss
   * short-circuits before semantic validation fires.
   */
  CallToolHandlerConfig validationInterceptor(MethodInterceptor<? super JsonNode> interceptor);

  /** Adds an interceptor to the INVOCATION stratum (innermost — retries, timeouts). */
  CallToolHandlerConfig invocationInterceptor(MethodInterceptor<? super JsonNode> interceptor);

  CallToolHandlerConfig guard(Guard guard);

  CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver);
}
