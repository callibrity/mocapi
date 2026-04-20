# Observability

Mocapi ships an opinionated observability stack built on the
per-handler customizer SPI. Four pieces, all opt-in by dropping in
the corresponding feature module:

| Concern | Module | What you get |
|---|---|---|
| Correlation logging (SLF4J MDC) | [`mocapi-logging`](logging.md) | `mcp.session` / `mcp.handler.kind` / `mcp.handler.name` MDC keys during every handler invocation |
| Metrics + tracing (Micrometer) | `mocapi-o11y` | One `Observation` per invocation → meters and spans, via whichever Micrometer handlers are on classpath |
| Structured audit log | [`mocapi-audit`](audit.md) | One SLF4J event per invocation on the `mocapi.audit` logger with caller / handler / outcome / duration fields |
| Handler inventory endpoint | [`mocapi-actuator`](actuator.md) | `/actuator/mcp` — server info, handler counts, per-handler metadata + schema digests |

All four attach through the same mechanism — one
`*HandlerCustomizer` bean per handler kind (see
[customizers.md](customizers.md)) — which means consistent wiring
shape, zero reflection on the hot path (kind + name are closed over
at startup), and no blind bean-list autowiring to surprise you.

---

## Metrics + tracing (`mocapi-o11y`)

One `McpObservationInterceptor` wraps every handler invocation in a
Micrometer `Observation`. The same code emits **both** metrics and
distributed-tracing spans: whichever `ObservationHandler`s the user
has on classpath participate automatically —
`MeterObservationHandler` converts events to `Timer` / `Counter`,
`TracingObservationHandler` converts events to spans. No separate
metrics or tracing interceptors.

### Observation shape

One observation name per handler kind (so dashboards get distinct
`Timer`s rather than one meter partitioned by tag):

- `mcp.tool`
- `mcp.prompt`
- `mcp.resource`
- `mcp.resource_template`

Low-cardinality tags:

- `mcp.handler.kind` — `tool` / `prompt` / `resource` / `resource_template`
- `mcp.handler.name` — tool / prompt name, or resource URI / URI template

The standard Micrometer `outcome` tag (`SUCCESS` / `ERROR`) is added
by `DefaultMeterObservationHandler` on exception automatically.

### Enabling metrics

Add `mocapi-o11y` to the classpath. The autoconfig activates when an
`ObservationRegistry` bean is present — Spring Boot auto-creates one
whenever `spring-boot-starter-actuator` or any Micrometer Observation
autoconfig is on the classpath. Typical recipe:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-o11y</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

That's enough to produce metrics at `/actuator/metrics`. No concrete
meter registry needed — Spring Boot's `SimpleMeterRegistry` is the
default and holds everything you need for
`/actuator/metrics/mcp.tool` to return timers.

Expose the endpoints in `application.properties`:

```properties
management.endpoints.web.exposure.include=health,info,metrics,mcp
```

Query the data:

```bash
# Aggregate across all tools
curl localhost:8080/actuator/metrics/mcp.tool

# One specific tool
curl 'localhost:8080/actuator/metrics/mcp.tool?tag=mcp.handler.name:get_weather'

# Currently-in-flight invocations
curl localhost:8080/actuator/metrics/mcp.tool.active
```

`mcp.tool.active` is a `LongTaskTimer` that tracks in-flight calls —
the right meter for alerting on stuck invocations.

### Enabling tracing

Add the Micrometer Tracing bridge + Spring Boot's OpenTelemetry
autoconfig + the OTel SDK:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-micrometer-tracing-opentelemetry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

Spring Boot 4 split these across three artifacts; the SDK-providing
`spring-boot-starter-opentelemetry` is the one people miss most
often. Without it, `NoopTracerAutoConfiguration` wins and trace IDs
come out empty in logs.

Trace IDs flow into Spring Boot's default log pattern automatically
once a real `Tracer` bean is present — no `logging.pattern.*`
override needed. Each log line picks up
`[<app>,<traceId>,<spanId>]`.

### Exporting spans

To ship spans somewhere (Jaeger / Tempo / Honeycomb / Grafana Cloud),
add an OTLP exporter and point it at your collector:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```properties
management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
management.tracing.sampling.probability=1.0
```

**Disable OTLP metrics export** unless your collector handles both:

```properties
# Jaeger all-in-one accepts traces at /v1/traces but 404s on
# /v1/metrics. Disable metrics OTLP export to avoid the 60s-interval
# warning log.
management.otlp.metrics.export.enabled=false
```

For dev-only span inspection, one-line Jaeger all-in-one:

```bash
docker run -d --rm --name jaeger \
    -p 16686:16686 \
    -p 4318:4318 \
    jaegertracing/all-in-one:latest
```

Then open `http://localhost:16686`, select your service, and click
any trace for the `http post /mcp` → `mcp.tool` waterfall.

### A production-sampling note

`management.tracing.sampling.probability=1.0` (sample everything) is
fine for dev. In production, typically **0.05-0.10** — the full
observation pipeline runs regardless of sampling, but the
`TracingObservationHandler` short-circuits the span-exporting work
for unsampled traces. Cuts OTel CPU by ~10-20× with minimal loss of
statistical fidelity.

### Interaction with `@Observed`

Users who prefer `@Observed` on individual tool methods (Spring
Boot's built-in AOP advice) can skip `mocapi-o11y` and wire their
own observations; the two paths don't fight but do nest if both are
enabled on the same method. Pick one.

---

## Context propagation across the HTTP virtual-thread boundary

`StreamableHttpController.handleCall` spawns a fresh virtual thread
per MCP call. By default, `Thread.ofVirtual().start(...)` does not
inherit `ThreadLocal`-backed context (including Spring Security's
`SecurityContextHolder`, Micrometer Observation scope, and SLF4J
MDC).

Mocapi wires `io.micrometer:context-propagation` into the spawn so
every `ThreadLocalAccessor` registered via SPI gets captured on the
request thread and restored on the handler VT. One change at one
call site; every library that ships an accessor participates
automatically. This is what makes tracing trees in Jaeger show
`http post /mcp` as the parent of the `mcp.tool` span instead of
two disconnected traces.

The `io.micrometer:context-propagation` jar is a direct dependency
of `mocapi-streamable-http-transport`; users don't configure
anything.

---

## Per-run performance methodology

See [`perf/benchmarking.md`](perf/benchmarking.md) for the standing
soak-test + JFR-profiling runbook, and
[`perf/history.md`](perf/history.md) for run-over-run numbers. Run
periodically to detect regressions or validate a performance change.

---

## The four pieces in depth

- [`logging.md`](logging.md) — MDC correlation keys, Logback pattern
  snippet, virtual-threads caveat.
- [`audit.md`](audit.md) — structured audit logger, pluggable caller
  identity, opt-in `arguments_hash`.
- [`actuator.md`](actuator.md) — `/actuator/mcp` endpoint shape,
  exposure config, security.
- [`customizers.md`](customizers.md) — the underlying extension
  mechanism every observability module plugs into.

---

## Non-goals

- **Per-invocation mutable metadata on `MethodInvocation`.**
  Methodical intentionally doesn't carry one; interceptors that need
  to share per-call state use `ScopedValue`.
- **A unified observer SPI.** The whole point of the
  customizer-driven interceptor model is that everything *is* the
  observer SPI. No parallel API.
- **Rate limiting in core.** A token-bucket `MethodInterceptor`
  would be trivial on top of the customizer SPI; mocapi doesn't
  ship one. Users or third-party starters do.
- **Baking scope / role / tenant models into mocapi.** Entitlement
  plugins (see [guards.md](guards.md)) own those semantics.
