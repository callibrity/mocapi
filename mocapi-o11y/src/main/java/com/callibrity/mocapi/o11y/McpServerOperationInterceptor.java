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

import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.exchange.TraceContext;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;

/**
 * Wraps a single MCP JSON-RPC method dispatch in the OpenTelemetry MCP semantic-convention server
 * operation observation (ADR-0030). Attached once per {@code @JsonRpcMethod} handler via ripcurl's
 * {@code JsonRpcMethodHandlerCustomizer}, so it brackets the full method — envelope handling,
 * validation, guards, the user handler, and result serialization — which is the interval the
 * conventions define for {@code mcp.server.operation.duration} ("received until the result or ack
 * is sent"). Do not attach this at mocapi's per-handler OBSERVATION stratum: that chain wraps only
 * the user's handler method and would under-report the metric by construction.
 *
 * <p>Observation name: {@code mcp.server.operation} — histogram metric {@code
 * mcp.server.operation.duration}. Contextual (span) name: {@code {mcp.method.name} {target}} where
 * target is the tool or prompt name ({@code tools/call echo}), or the bare method name when no
 * low-cardinality target exists. Per the conventions, {@code mcp.resource.uri} is deliberately
 * <em>not</em> used as a span-name target — it would produce high-cardinality span names.
 *
 * <p>This observation supersedes ripcurl's generic {@code jsonrpc.server} observation: {@link
 * McpServerOperationCustomizer} occupies ripcurl's {@code JsonRpcObservationCustomizer} seam, so
 * ripcurl's default backs off (ADR-0030). The MCP conventions describe <em>one</em> server span
 * carrying the JSON-RPC attributes ({@code jsonrpc.request.id}, {@code rpc.response.status_code},
 * …) alongside the MCP and GenAI ones — not two nested spans over the same interval. The JSON-RPC
 * attributes ripcurl emitted are therefore emitted here instead, with identical semantics ({@code
 * error.type} and {@code rpc.response.status_code} carry the JSON-RPC error code obtained through
 * the same {@link JsonRpcExceptionTranslatorRegistry} the dispatcher uses, so the code on the span
 * always matches the code on the wire).
 *
 * <p>Tool-level failure: a {@code tools/call} that completes normally but returns {@code
 * CallToolResult.isError=true} gets {@code error.type=tool_error}, as the conventions specifically
 * require. No throwable exists on that path, so the span status remains unset — a recorded
 * deviation (ADR-0030); the attribute is the queryable signal.
 *
 * <p><strong>Remote trace parent and span kind.</strong> When the bound {@link McpExchange} carries
 * the spec's W3C trace-context {@code _meta} keys, the observation is created with a {@link
 * McpRequestReceiverContext} ({@code Kind.SERVER}) so a propagating tracing handler joins this span
 * to the client's trace as a remote parent — the server span is the one that should carry the
 * cross-boundary link, per the conventions. Without trace keys the context is a plain one and the
 * span nests locally.
 *
 * <p>The MCP attributes are {@code development}-stability, sourced from {@code
 * open-telemetry/semantic-conventions-genai} {@code model/mcp/} — see ADR-0030 for the pinned
 * snapshot and the deliberate omissions ({@code client.address}/{@code client.port}, opt-in payload
 * attributes, session metrics).
 */
public final class McpServerOperationInterceptor implements MethodInterceptor<JsonNode> {

  /** Observation name — becomes the histogram metric {@code mcp.server.operation.duration}. */
  public static final String OBSERVATION_NAME = "mcp.server.operation";

  /** {@code network.transport} value for the Streamable HTTP transport. */
  public static final String TRANSPORT_TCP = "tcp";

  /** {@code network.transport} value for the stdio transport (semconv: stdio is {@code pipe}). */
  public static final String TRANSPORT_PIPE = "pipe";

  private final ObservationRegistry registry;
  private final JsonRpcExceptionTranslatorRegistry translators;
  private final String method;
  private final String networkTransport;

  /**
   * @param registry the observation registry
   * @param translators the translator registry the dispatcher uses, so error codes on the
   *     observation match the wire
   * @param method the JSON-RPC method name, closed over at customizer time (no per-call reflection)
   * @param networkTransport the semconv {@code network.transport} value ({@link #TRANSPORT_TCP} or
   *     {@link #TRANSPORT_PIPE}), or {@code null} to omit the attribute
   */
  public McpServerOperationInterceptor(
      ObservationRegistry registry,
      JsonRpcExceptionTranslatorRegistry translators,
      String method,
      String networkTransport) {
    this.registry = registry;
    this.translators = translators;
    this.method = method;
    this.networkTransport = networkTransport;
  }

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> invocation) {
    Observation observation = buildObservation(currentParams());

    observation.start();
    JsonNode result = null;
    boolean completed = false;
    try (var _ = observation.openScope()) {
      result = (JsonNode) invocation.proceed();
      completed = true;
      return result;
    } catch (RuntimeException e) {
      JsonRpcErrorDetail detail = translators.translate(e);
      String code = Integer.toString(detail.code());
      observation.lowCardinalityKeyValue("rpc.response.status_code", code);
      observation.lowCardinalityKeyValue("error.type", code);
      observation.error(e);
      throw e;
    } finally {
      if (completed && isToolError(result)) {
        observation.lowCardinalityKeyValue("error.type", "tool_error");
      }
      observation.stop();
    }
  }

  /** Builds the not-yet-started observation with the full semconv attribute set. */
  private Observation buildObservation(JsonNode params) {
    String target = targetOf(params);
    Observation observation =
        Observation.createNotStarted(
                OBSERVATION_NAME, McpServerOperationInterceptor::serverContext, registry)
            .contextualName(target != null ? method + " " + target : method)
            .lowCardinalityKeyValue("mcp.method.name", method)
            .lowCardinalityKeyValue("rpc.system.name", "jsonrpc")
            .lowCardinalityKeyValue("jsonrpc.protocol.version", JsonRpcProtocol.VERSION);
    addNetworkAttributes(observation);
    addProtocolVersion(observation);
    addTargetAttributes(observation, target, params);
    addRequestId(observation);
    return observation;
  }

  private void addNetworkAttributes(Observation observation) {
    if (networkTransport != null) {
      observation.lowCardinalityKeyValue("network.transport", networkTransport);
      if (TRANSPORT_TCP.equals(networkTransport)) {
        observation.lowCardinalityKeyValue("network.protocol.name", "http");
      }
    }
  }

  private static void addProtocolVersion(Observation observation) {
    if (McpExchange.CURRENT.isBound() && McpExchange.CURRENT.get().protocolVersion() != null) {
      observation.lowCardinalityKeyValue(
          "mcp.protocol.version", McpExchange.CURRENT.get().protocolVersion());
    }
  }

  private void addTargetAttributes(Observation observation, String target, JsonNode params) {
    switch (method) {
      case McpMethods.TOOLS_CALL -> addToolAttributes(observation, target);
      case McpMethods.PROMPTS_GET -> addPromptName(observation, target);
      case McpMethods.RESOURCES_READ -> addResourceUri(observation, params);
      default -> {
        // No target-bearing attributes for list/discover/notification methods.
      }
    }
  }

  private static void addToolAttributes(Observation observation, String target) {
    observation.lowCardinalityKeyValue("gen_ai.operation.name", "execute_tool");
    if (target != null) {
      observation.lowCardinalityKeyValue("gen_ai.tool.name", target);
    }
  }

  private static void addPromptName(Observation observation, String target) {
    if (target != null) {
      observation.lowCardinalityKeyValue("gen_ai.prompt.name", target);
    }
  }

  private static void addResourceUri(Observation observation, JsonNode params) {
    String uri = stringField(params, "uri");
    if (uri != null) {
      observation.highCardinalityKeyValue("mcp.resource.uri", uri);
    }
  }

  private static void addRequestId(Observation observation) {
    if (JsonRpcDispatcher.CURRENT_REQUEST.isBound()
        && JsonRpcDispatcher.CURRENT_REQUEST.get() instanceof JsonRpcCall call) {
      observation.highCardinalityKeyValue("jsonrpc.request.id", call.id().asString());
    }
  }

  private boolean isToolError(JsonNode result) {
    return McpMethods.TOOLS_CALL.equals(method)
        && result != null
        && result.path("isError").asBoolean(false);
  }

  /** The tool or prompt name for the two target-bearing methods; {@code null} otherwise. */
  private String targetOf(JsonNode params) {
    return switch (method) {
      case McpMethods.TOOLS_CALL, McpMethods.PROMPTS_GET -> stringField(params, "name");
      default -> null;
    };
  }

  private static JsonNode currentParams() {
    return JsonRpcDispatcher.CURRENT_REQUEST.isBound()
        ? JsonRpcDispatcher.CURRENT_REQUEST.get().params()
        : null;
  }

  private static String stringField(JsonNode params, String field) {
    if (params == null || !params.isObject()) {
      return null;
    }
    JsonNode value = params.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  /**
   * A {@link McpRequestReceiverContext} ({@code Kind.SERVER}, remote parent from the {@code _meta}
   * W3C keys) when the bound exchange carries a trace context, otherwise a plain context.
   */
  private static Observation.Context serverContext() {
    if (McpExchange.CURRENT.isBound()) {
      TraceContext traceContext = McpExchange.CURRENT.get().traceContext();
      if (traceContext.isPresent()) {
        return new McpRequestReceiverContext(traceContext);
      }
    }
    return new Observation.Context();
  }

  @Override
  public String toString() {
    return "Records Micrometer '"
        + OBSERVATION_NAME
        + "' observations (OpenTelemetry MCP semantic conventions) for method '"
        + method
        + "'";
  }
}
