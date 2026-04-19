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

import static com.callibrity.mocapi.o11y.McpObservationConvention.HANDLER_KIND;
import static com.callibrity.mocapi.o11y.McpObservationConvention.HANDLER_NAME;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.intercept.MethodInvocation;

/**
 * Wraps a single MCP handler invocation in a Micrometer {@link Observation}. One instance per
 * handler: the observation name, kind, and handler name are closed over at construction so the hot
 * path does zero reflection. Registered observation handlers (meters, tracing, custom) see {@code
 * onStart} / {@code onStop} / {@code onError} events for every call.
 *
 * <p>Exceptions thrown by {@code proceed} propagate after the observation is stopped with {@code
 * ERROR} status.
 */
public final class McpObservationInterceptor implements MethodInterceptor<Object> {

  private final ObservationRegistry registry;
  private final String observationName;
  private final String handlerKind;
  private final String handlerName;

  public McpObservationInterceptor(
      ObservationRegistry registry,
      String observationName,
      String handlerKind,
      String handlerName) {
    this.registry = registry;
    this.observationName = observationName;
    this.handlerKind = handlerKind;
    this.handlerName = handlerName;
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    McpObservationContext context = new McpObservationContext(handlerKind, handlerName);
    Observation observation =
        Observation.createNotStarted(observationName, () -> context, registry)
            .lowCardinalityKeyValue(HANDLER_KIND, handlerKind)
            .lowCardinalityKeyValue(HANDLER_NAME, handlerName);
    return observation.observe(invocation::proceed);
  }
}
