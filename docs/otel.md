# OpenTelemetry with Mocapi

Drop in `mocapi-otel` plus a backend-specific exporter and every tool / prompt / resource invocation produces a two-layer trace carrying OpenTelemetry MCP, GenAI, and JSON-RPC semantic-convention attributes.

## What you get

Span hierarchy per invocation:

```
http POST /mcp                   (Spring MVC — SERVER kind)
  └─ jsonrpc.server               (ripcurl-o11y; mocapi-o11y enriches with mcp.* tags)
     rpc.system.name=jsonrpc
     jsonrpc.protocol.version=2.0
     jsonrpc.request.id=42
     rpc.response.status_code=OK  (or JSON-RPC error code on failure)
     error.type=…                 (on failure)
     mcp.method.name=tools/call   (added by mocapi-o11y's McpObservationFilter)
     mcp.session.id=…
     mcp.protocol.version=2025-11-25
     └─ mcp.handler.execution      (mocapi-o11y per-handler span)
        mcp.handler.kind=tool
        gen_ai.operation.name=execute_tool
        gen_ai.tool.name=my-tool
```

For `tools/call`, `prompts/get`, and `resources/*` the inner `mcp.handler.execution` span fires with handler-specific GenAI / resource-URI attrs. For dispatch-only methods (`tools/list`, `initialize`, notifications) only the outer `jsonrpc.server` span appears.

Metrics: two histogram meters — `jsonrpc.server.duration` (ripcurl-o11y) and `mcp.handler.execution.duration` (mocapi-o11y) — produced automatically by Micrometer's default meter observation handler whenever a `MeterRegistry` is in the context.

Attributes align with:
- [OTel MCP semconv](https://opentelemetry.io/docs/specs/semconv/gen-ai/mcp/)
- [OTel JSON-RPC semconv](https://opentelemetry.io/docs/specs/semconv/rpc/json-rpc/)
- [OTel GenAI semconv](https://opentelemetry.io/docs/specs/semconv/gen-ai/)

## Baseline setup

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-otel</artifactId>
</dependency>
```

This transitively pulls:
- `mocapi-o11y` — MCP handler observations + the `McpObservationFilter` that enriches the outer JSON-RPC span with `mcp.*` tags
- `ripcurl-o11y` (via mocapi-o11y) — JSON-RPC observations with OTel JSON-RPC semconv attrs
- `spring-boot-starter-opentelemetry` — OTel SDK + Micrometer Observation → OTel tracing bridge + autoconfig
- `spring-boot-micrometer-observation` (via mocapi-o11y) — the `ObservationRegistry` bean Micrometer needs

`mocapi-otel` does **not** bundle an exporter — that's deployment-specific. Pick one of the recipes below.

## Recipes

### OTLP → Jaeger, Tempo, or an OTel Collector

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.tracing.sampling.probability=1.0
```

**Jaeger-specific note.** Jaeger's all-in-one accepts traces at `/v1/traces` but **not** metrics at `/v1/metrics`. Without disabling OTLP metrics, Spring's `OtlpMeterRegistry` will log 404s every minute. Set:

```properties
management.otlp.metrics.export.enabled=false
```

This is **not** a default in `mocapi-otel` — Grafana Cloud, Tempo-with-OTel-Collector, and similar backends accept OTLP metrics fine, and forcing the default off would silently drop metrics for them. Only set it when the backend you're targeting is traces-only.

### Azure Monitor / Application Insights

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-otel</artifactId>
</dependency>
<dependency>
    <groupId>com.azure.spring</groupId>
    <artifactId>spring-cloud-azure-starter-monitor</artifactId>
</dependency>
```

The Azure monitor starter reads `APPLICATIONINSIGHTS_CONNECTION_STRING` from the environment and plugs its exporter into Spring Boot's OpenTelemetry bean. Uses the OTel SDK path (not the App Insights Java agent), so it's compatible with GraalVM native-image.

```properties
# Optional: tune sampling
management.tracing.sampling.probability=1.0
```

No endpoint property needed — the Azure starter picks up the destination from `APPLICATIONINSIGHTS_CONNECTION_STRING`.

### Datadog via OTLP

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces   # Datadog Agent OTLP receiver
management.tracing.sampling.probability=0.1
```

Requires the Datadog Agent running locally with an OTLP receiver enabled. See Datadog's OTLP ingest documentation for the agent-side configuration.

## Optional: OTel-correlated logs

If you want every log line to flow to the same OTel backend as your spans — correlated by trace and span ID — add the Logback appender **explicitly** (not transitively via `mocapi-otel`):

```xml
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender-1.0</artifactId>
    <version>2.24.0-alpha</version>
</dependency>
```

Then configure a `logback-spring.xml` with the `OpenTelemetryAppender` (see [OTel docs](https://opentelemetry.io/docs/zero-code/java/agent/instrumentation/logback-appender/)).

**Why isn't this in `mocapi-otel`?**
- It's Logback-specific; Log4j2 users need a different bridge.
- Log shipping is often a separate pipeline from tracing (stdout → Fluent Bit, syslog, etc.) — baking a specific path into a feature module is presumptuous.
- The OTel Java Instrumentation appender is `-alpha` stability; pulling alpha artifacts into a stable feature module's transitive set isn't safe.

Opt in explicitly when you want it.

## Sampling

Spring Boot defaults to 10% sampling. For dev / demo:

```properties
management.tracing.sampling.probability=1.0
```

For production, 0.01–0.10 is typical. Our standing [performance baseline](perf/benchmarking.md) measures 100% sampling — production throughput is typically 5–10% higher at 0.05 sampling, not a regression.

## Known gaps

- **Span kind is `INTERNAL`, not `SERVER`.** Both the `jsonrpc.server` and `mcp.handler.execution` spans emit as `INTERNAL` kind. Upgrading to `SERVER` kind requires Micrometer `ReceiverContext` plumbing and hasn't been prioritized — the ambient HTTP span already carries `SERVER` kind via Spring MVC, so filtering by kind still finds the inbound request.
- **`tool_error` attribute not emitted.** When a tool returns `CallToolResult.isError=true` (a spec-defined structured-error response rather than a thrown exception), we currently don't set `error.type=tool_error` on the span. Detecting `isError` requires deserializing the tool's result; deferred to a follow-up.
- **`rpc.response.status_code` is on the outer JSON-RPC span only.** The inner `mcp.handler.execution` span uses the standard `error.type` attr on failure; `rpc.*` attrs belong at the transport layer above.
