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

import com.callibrity.mocapi.server.handler.HandlerKind;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;

/**
 * Wraps the <em>user's</em> MCP handler invocation (tool / prompt / resource / resource-template)
 * in a Micrometer {@link Observation}, nesting inside the semconv {@link
 * McpServerOperationInterceptor mcp.server.operation} span. This is deliberately a
 * <strong>mocapi-specific</strong> observation, not a semantic-convention one (ADR-0030): it
 * isolates user-handler time from the framework work around it (envelope handling, validation,
 * guards, serialization), which the standard server-operation span cannot express. All
 * semantic-convention attributes live on the outer span.
 *
 * <p>One instance per handler, attached at the OBSERVATION stratum via {@code
 * CallToolHandlerCustomizer}, {@code GetPromptHandlerCustomizer}, {@code
 * ReadResourceHandlerCustomizer}, or {@code ReadResourceTemplateHandlerCustomizer}. The handler
 * kind + name are closed over at construction so the hot path does no reflection.
 *
 * <p>Observation name: {@code mcp.handler.execution} — histogram metric {@code
 * mcp.handler.execution.duration}. Contextual (span) name: the target name (tool name, prompt name,
 * resource URI, or resource-template URI template). Low-cardinality {@code mcp.handler.kind} tag
 * lets users filter by kind across the shared observation name; errored calls are distinguished by
 * Micrometer's automatic {@code error} meter tag and the exception recorded on the span.
 *
 * <p>The remote trace parent from {@code _meta} is honored by the outer server-operation span, not
 * here — this observation uses a plain context and parents locally, so the waterfall reads client
 * trace → {@code mcp.server.operation} → {@code mcp.handler.execution}.
 */
public final class McpHandlerObservationInterceptor implements MethodInterceptor<Object> {

  /** Observation name — becomes the histogram metric {@code mcp.handler.execution.duration}. */
  public static final String OBSERVATION_NAME = "mcp.handler.execution";

  private final ObservationRegistry registry;
  private final HandlerKind kind;
  private final String targetName;

  public McpHandlerObservationInterceptor(
      ObservationRegistry registry, HandlerKind kind, String targetName) {
    this.registry = registry;
    this.kind = kind;
    this.targetName = targetName;
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    Observation observation =
        Observation.createNotStarted(OBSERVATION_NAME, registry)
            .contextualName(targetName)
            .lowCardinalityKeyValue("mcp.handler.kind", kind.tag());

    observation.start();
    try (var _ = observation.openScope()) {
      return invocation.proceed();
    } catch (RuntimeException e) {
      observation.error(e);
      throw e;
    } finally {
      observation.stop();
    }
  }

  @Override
  public String toString() {
    return "Records Micrometer '"
        + OBSERVATION_NAME
        + "' observations (handler execution time) for "
        + kind.tag()
        + " '"
        + targetName
        + "'";
  }
}
