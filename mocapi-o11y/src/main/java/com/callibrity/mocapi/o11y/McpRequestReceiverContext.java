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
package com.callibrity.mocapi.o11y;

import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.server.exchange.TraceContext;
import io.micrometer.observation.transport.Kind;
import io.micrometer.observation.transport.ReceiverContext;

/**
 * Observation context whose carrier is the W3C trace context a client supplied in its request
 * {@code _meta} ({@code traceparent} / {@code tracestate} / {@code baggage} — spec {@code
 * basic/index#meta}, "OpenTelemetry trace context"). When a tracing-capable {@code
 * ObservationHandler} (e.g. Micrometer Tracing's {@code
 * PropagatingReceiverTracingObservationHandler}, auto-configured by the {@code mocapi-otel} bundle)
 * sees this context, its propagator extracts the remote parent from the carrier and the resulting
 * span joins the client's trace. Registries without a propagating handler treat it as a plain
 * context — metrics-only deployments are unaffected.
 *
 * <p>The getter answers exactly the three spec-defined unprefixed keys; trace-context propagators
 * ask for lowercase W3C field names, which is what {@link McpMetaKeys} pins.
 */
public final class McpRequestReceiverContext extends ReceiverContext<TraceContext> {

  public McpRequestReceiverContext(TraceContext traceContext) {
    super(McpRequestReceiverContext::read, Kind.SERVER);
    setCarrier(traceContext);
  }

  private static String read(TraceContext carrier, String key) {
    return switch (key) {
      case McpMetaKeys.TRACEPARENT -> carrier.traceparent();
      case McpMetaKeys.TRACESTATE -> carrier.tracestate();
      case McpMetaKeys.BAGGAGE -> carrier.baggage();
      default -> null;
    };
  }
}
