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

import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcRequest;
import com.callibrity.ripcurl.o11y.JsonRpcObservationInterceptor;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * Enriches ripcurl's {@code jsonrpc.server} observations with OpenTelemetry MCP semantic-convention
 * attributes so the trace carries mocapi context alongside the JSON-RPC envelope metadata ripcurl
 * already stamps. Passes every other observation through unchanged.
 *
 * <p>The filter runs at {@link Observation#stop()} time, which ripcurl invokes from inside the
 * {@link JsonRpcDispatcher#CURRENT_REQUEST} scope — so both that {@link ScopedValue} and {@link
 * McpSession#CURRENT} are still bound when this filter reads them.
 *
 * <p>Attributes added on JSON-RPC observations per
 * https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/:
 *
 * <ul>
 *   <li>{@code mcp.method.name} — the JSON-RPC method (from the bound request envelope)
 *   <li>{@code mcp.session.id}, {@code mcp.protocol.version} — when {@link McpSession#CURRENT} is
 *       bound
 * </ul>
 *
 * <p>Using an {@link ObservationFilter} rather than a per-method interceptor avoids ordering
 * concerns with ripcurl's own observation customizer, has zero per-call hot-path cost beyond one
 * name-equality check per observation, and keeps mocapi decoupled from ripcurl's interceptor-chain
 * wiring entirely.
 */
public final class McpObservationFilter implements ObservationFilter {

  @Override
  public Observation.Context map(Observation.Context context) {
    if (!JsonRpcObservationInterceptor.OBSERVATION_NAME.equals(context.getName())) {
      return context;
    }

    if (JsonRpcDispatcher.CURRENT_REQUEST.isBound()) {
      JsonRpcRequest request = JsonRpcDispatcher.CURRENT_REQUEST.get();
      context.addLowCardinalityKeyValue(KeyValue.of("mcp.method.name", request.method()));
    }

    if (McpSession.CURRENT.isBound()) {
      McpSession session = McpSession.CURRENT.get();
      context.addLowCardinalityKeyValue(KeyValue.of("mcp.session.id", session.sessionId()));
      if (session.protocolVersion() != null) {
        context.addLowCardinalityKeyValue(
            KeyValue.of("mcp.protocol.version", session.protocolVersion()));
      }
    }

    return context;
  }
}
