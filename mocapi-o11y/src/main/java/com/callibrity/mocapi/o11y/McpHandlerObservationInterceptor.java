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
import com.callibrity.ripcurl.o11y.JsonRpcObservationInterceptor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;

/**
 * Wraps an MCP handler invocation (tool / prompt / resource / resource-template) in a Micrometer
 * {@link Observation}, nesting inside the outer {@link JsonRpcObservationInterceptor} span. Attrs
 * describe the handler target itself (tool name, prompt name, resource URI) per the OpenTelemetry
 * MCP / GenAI semantic conventions; the outer span owns JSON-RPC envelope attrs.
 *
 * <p>One instance per handler, attached via {@code CallToolHandlerCustomizer}, {@code
 * GetPromptHandlerCustomizer}, {@code ReadResourceHandlerCustomizer}, or {@code
 * ReadResourceTemplateHandlerCustomizer}. The handler kind + name are closed over at construction
 * so the hot path does no reflection.
 *
 * <p>Observation name: {@code mcp.handler.execution} — histogram metric {@code
 * mcp.handler.execution.duration}. Contextual (span) name: the target name (tool name, prompt name,
 * resource URI, or resource-template URI template). Low-cardinality {@code mcp.handler.kind} tag
 * lets users filter by kind across the shared observation name.
 *
 * <p>Tag mapping by kind (per https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/):
 *
 * <ul>
 *   <li>{@code tool} — {@code gen_ai.operation.name=execute_tool}, {@code gen_ai.tool.name=<name>}
 *   <li>{@code prompt} — {@code gen_ai.prompt.name=<name>}
 *   <li>{@code resource} / {@code resource_template} — {@code mcp.resource.uri=<uri>} (high
 *       cardinality)
 * </ul>
 *
 * <p>{@code error.type} with the exception's simple class name is added on failure.
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

    switch (kind) {
      case TOOL -> {
        observation.lowCardinalityKeyValue("gen_ai.operation.name", "execute_tool");
        observation.lowCardinalityKeyValue("gen_ai.tool.name", targetName);
      }
      case PROMPT -> observation.lowCardinalityKeyValue("gen_ai.prompt.name", targetName);
      case RESOURCE, RESOURCE_TEMPLATE ->
          observation.highCardinalityKeyValue("mcp.resource.uri", targetName);
    }

    observation.start();
    try (var _ = observation.openScope()) {
      return invocation.proceed();
    } catch (RuntimeException e) {
      observation.lowCardinalityKeyValue("error.type", e.getClass().getSimpleName());
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
        + "' observations (OpenTelemetry MCP semconv) for "
        + kind.tag()
        + " '"
        + targetName
        + "'";
  }
}
