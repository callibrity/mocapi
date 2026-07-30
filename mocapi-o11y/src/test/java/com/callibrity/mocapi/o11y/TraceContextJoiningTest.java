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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.exchange.TraceContext;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * End-to-end check: a W3C {@code traceparent} carried in the request {@code _meta} (spec {@code
 * basic/index#meta}, "OpenTelemetry trace context") becomes the remote parent of the semconv {@code
 * mcp.server.operation} span (ADR-0030 — the server span carries the cross-boundary link). Uses the
 * real Micrometer Tracing → OpenTelemetry bridge — the same handler chain the {@code mocapi-otel}
 * bundle auto-configures — with an in-memory exporter so the assertion is on the actual exported
 * span's trace id and parent span id.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TraceContextJoiningTest {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String PARENT_SPAN_ID = "b7ad6b7169203331";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-" + PARENT_SPAN_ID + "-01";

  private final InMemorySpanExporter exporter = InMemorySpanExporter.create();
  private final SdkTracerProvider tracerProvider =
      SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
  private final OpenTelemetrySdk sdk =
      OpenTelemetrySdk.builder()
          .setTracerProvider(tracerProvider)
          .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
          .build();
  private final ObservationRegistry registry = ObservationRegistry.create();

  TraceContextJoiningTest() {
    var otelTracer = sdk.getTracer("mocapi-o11y-test");
    var tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {});
    var propagator = new OtelPropagator(sdk.getPropagators(), otelTracer);
    // Mirrors Spring Boot's tracing handler grouping: the propagating receiver handler claims
    // ReceiverContexts (remote parent join), the default handler claims everything else.
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler.FirstMatchingCompositeObservationHandler(
                new PropagatingReceiverTracingObservationHandler<>(tracer, propagator),
                new DefaultTracingObservationHandler(tracer)));
  }

  @AfterEach
  void shutDownSdk() {
    sdk.close();
  }

  @Test
  void server_operation_span_joins_the_meta_traceparent_as_remote_parent() {
    var interceptor = serverOperationInterceptor();
    var exchange =
        new McpExchange("2026-07-28", null, null, new TraceContext(TRACEPARENT, null, null));

    ScopedValue.where(McpExchange.CURRENT, exchange)
        .run(
            () ->
                interceptor.intercept(successfulInvocation(JsonNodeFactory.instance.objectNode())));

    List<SpanData> spans = exporter.getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    SpanData span = spans.getFirst();
    assertThat(span.getTraceId()).isEqualTo(TRACE_ID);
    assertThat(span.getParentSpanId()).isEqualTo(PARENT_SPAN_ID);
  }

  @Test
  void server_operation_span_starts_a_fresh_trace_when_no_trace_context_is_supplied() {
    var interceptor = serverOperationInterceptor();
    var exchange = new McpExchange("2026-07-28", null, null);

    ScopedValue.where(McpExchange.CURRENT, exchange)
        .run(
            () ->
                interceptor.intercept(successfulInvocation(JsonNodeFactory.instance.objectNode())));

    List<SpanData> spans = exporter.getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    SpanData span = spans.getFirst();
    assertThat(span.getTraceId()).isNotEqualTo(TRACE_ID);
    assertThat(span.getParentSpanContext().isValid()).isFalse();
  }

  private McpServerOperationInterceptor serverOperationInterceptor() {
    return new McpServerOperationInterceptor(
        registry, mock(JsonRpcExceptionTranslatorRegistry.class), "tools/call", "tcp");
  }

  private static MethodInvocation<JsonNode> successfulInvocation(JsonNode result) {
    return MethodInvocation.of(null, null, null, new Object[0], () -> result);
  }
}
