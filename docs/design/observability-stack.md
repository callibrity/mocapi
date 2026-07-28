# Observability stack

Mocapi's observability surface is four feature modules plus one operational
endpoint. Each module is opt-in by adding the JAR; activation is by
classpath presence, not configuration toggles. None of them depends on the
others, but they compose into a coherent stratum chain when enabled
together.

See [ADR-0017](../adr/0017-observability-stack.md) for the architecture
decision and [ADR-0011](../adr/0011-customizer-spi-and-strata.md) for the
underlying customizer SPI and interceptor strata.

## The four modules and one endpoint

| Module | Stratum | Output | Activation |
|---|---|---|---|
| `mocapi-logging` | CORRELATION | SLF4J MDC keys | classpath presence |
| `mocapi-o11y` | OBSERVATION | Micrometer `Observation` per call → metrics + spans | classpath + `ObservationRegistry` bean |
| `mocapi-otel` | (bundle) | `mocapi-o11y` + Spring Boot 4 OTel SDK + tracing bridge | classpath presence |
| `mocapi-audit` | AUDIT | `mocapi.audit` SLF4J events with structured fields | classpath presence |
| `mocapi-actuator` | n/a (operational) | `/actuator/mcp` read-only inventory | classpath + actuator |

All four interceptor-bearing modules attach via the per-handler customizer
SPI: one `*HandlerCustomizer` bean per handler kind, registering an
interceptor (or a guard, or a resolver) into the per-handler invoker chain.
Kind and name are closed over at startup; the hot path does no reflection.

## `mocapi-logging` — correlation (MDC)

A `McpMdcInterceptor` wraps every handler call. On entry it stamps the
MDC keys below (each only when its source value is available); on exit
(in a `finally`) it removes exactly the keys it added, leaving any
pre-existing MDC state untouched. MCP 2026-07-28 has no sessions
([ADR-0020](../adr/0020-stateless-request-model.md)), so correlation
context is per-request: protocol version and client name come from the
request's `_meta` envelope via the bound `McpExchange`.

| Key | Value |
|---|---|
| `mcp.handler.kind` | `tool`, `prompt`, `resource`, `resource_template`. |
| `mcp.handler.name` | Tool / prompt name, or resource URI / URI template. |
| `mcp.handler.class` | Simple name of the (unwrapped) bean class hosting the handler. |
| `mcp.protocol.version` | Protocol version from the request's `_meta` envelope. |
| `mcp.client.name` | Client name from the envelope's `clientInfo`. |
| `mcp.request.id` | JSON-RPC request id for the current call (absent for notifications). |

MDC is `ThreadLocal`-backed. On the per-call virtual thread spawned by the
HTTP transport ([ADR-0006](../adr/0006-virtual-thread-per-call.md)), MDC is
captured via `io.micrometer:context-propagation` so it survives the
handoff from the request thread.

## `mocapi-o11y` — observation (Micrometer)

Two interceptors compose:

- `McpHandlerObservationInterceptor` — one `Observation` per handler
  call (name `mcp.handler.execution`, contextual name = the target
  name), wrapping the rest of the chain, with a low-cardinality
  `mcp.handler.kind` tag. GenAI / MCP-resource semantic-convention
  attributes are populated for cross-tool consistency. Whichever
  `ObservationHandler`s are on the classpath participate —
  `MeterObservationHandler` produces `Timer`/`Counter` meters,
  `TracingObservationHandler` produces spans. One observation, both
  telemetry shapes; no parallel APIs.
- `McpObservationFilter` — enriches the *outer* `jsonrpc.server`
  observation that wraps the dispatch with MCP-specific tags
  (`mcp.method.name`, `mcp.protocol.version`, `mcp.client.name` from
  the per-request `_meta` envelope) so traces show the full
  `http post /mcp` → `jsonrpc.server` → `mcp.handler.execution`
  waterfall.

**Remote trace parent from `_meta`.** The spec defines unprefixed
`traceparent` / `tracestate` / `baggage` keys in the request `_meta`
(W3C Trace Context / Baggage). `MetaEnvelopeParser` surfaces them on
the per-request `McpExchange`; when a request carries a `traceparent`,
`McpHandlerObservationInterceptor` creates its observation with a
`McpRequestReceiverContext` (a Micrometer `ReceiverContext` whose
carrier is the trace context), so a propagating tracing handler joins
the handler span to the *client's* trace as a remote parent. The
client-supplied parent deliberately wins over local parentage: the
spec moved trace context into `_meta` precisely because transport
headers may not carry it. Without trace keys, the span nests locally
under `jsonrpc.server` as before; metrics-only registries are
unaffected.

Activation requires both `mocapi-o11y` and an `ObservationRegistry` bean.
Spring Boot Actuator auto-creates the registry; no concrete meter
registry is needed for `/actuator/metrics/mcp.handler.execution` to return data
(`SimpleMeterRegistry` is fine).

## `mocapi-otel` — sourceless dependency bundle

`mocapi-otel` ships no Java classes. It is a pom-only artifact whose
purpose is to express the right combination of dependencies for an
OpenTelemetry deployment in one place:

- `mocapi-o11y`
- Spring Boot 4 OTel autoconfig (`spring-boot-starter-opentelemetry`)
- Micrometer Tracing OTel bridge (`micrometer-tracing-bridge-otel`)

Adding `mocapi-otel` to a Spring Boot app produces working metrics +
tracing without further dependency hunting. Spring Boot 4 split the OTel
artifacts into three pieces and the SDK-providing one is the most often
forgotten — bundling avoids the silent-`NoopTracer` failure mode.

## `mocapi-audit` — structured audit log

One `AuditLoggingInterceptor` per handler kind. Each invocation emits a
single SLF4J event on the dedicated logger `mocapi.audit` using the SLF4J
2.0 fluent builder API:

```java
logger.atInfo()
    .addKeyValue("caller", caller)
    .addKeyValue("protocol_version", protocolVersion)
    .addKeyValue("client_name", clientName)
    .addKeyValue("handler_kind", kind)
    .addKeyValue("handler_name", name)
    .addKeyValue("outcome", outcome)
    .addKeyValue("duration_ms", durationMs)
    .log("mcp.audit");
```

`protocol_version` and `client_name` come from the per-request
`McpExchange` (`_meta` envelope); there is no session id in MCP
2026-07-28 ([ADR-0020](../adr/0020-stateless-request-model.md)).

`outcome` is one of:

- `success` — invocation completed without an infrastructure-level
  exception. A tool that returns `CallToolResult.isError=true` still
  counts as `success` (it's a model-visible tool error, not an audit
  failure).
- `forbidden` — `JsonRpcException` with code `-32010` (a guard denial, ADR-0023).
- `invalid_params` — `JsonRpcException` with code `-32602` (e.g.
  Jakarta Validation rejection).
- `error` — any other thrown exception. Stack traces are not emitted;
  only `error_class` (simple name).

`arguments_hash` is opt-in via `mocapi.audit.hash-arguments=true`. When
enabled, the interceptor emits `sha256:<hex>` over the key-sorted
canonical JSON of the arguments — enough to correlate "did these calls
pass identical inputs?" without persisting the inputs themselves.

Caller identity is provided by `AuditCallerIdentityProvider`, a
single-method SPI. The default reads
`SecurityContextHolder.getContext().getAuthentication().getName()` when
Spring Security is on the classpath, falling back to `"anonymous"`.

## `mocapi-actuator` — operational inventory

`/actuator/mcp` is a Spring Boot Actuator endpoint that returns a
read-only snapshot of what the running instance ships: server name /
version / protocol version, per-kind handler counts, and per-handler
descriptors with name, title, description, schema digests (SHA-256 of
the canonical JSON schema), and the toString sequence of every
interceptor wrapping the reflective call.

The endpoint is **deliberately separate from the MCP protocol**. MCP
clients discover handlers via `tools/list` (subject to per-caller guard
visibility); operators ask `/actuator/mcp` for the full list. The two
audiences have different needs:

- MCP clients want only what they're entitled to call. Guards filter.
- Operators want everything that is registered, regardless of who can
  call it, so they can verify "did my new tool register?" or compare
  schema digests across nodes for drift detection.

This is an *operational* concern, not a *protocol* concern, so it gets
its own endpoint shape that doesn't try to ride the MCP transport. The
schema digests in particular are an operator-only fingerprint; clients
already have the full schemas.

`/actuator/mcp` is unrelated to the AUDIT stratum despite the surface
similarity. Audit is per-invocation; actuator is per-deployment
inventory. They do not share data or wiring.

## Stratum ordering

Interceptors compose outer-to-inner around the eventual reflective call:

```
CORRELATION   ── McpMdcInterceptor (mocapi-logging)
   OBSERVATION   ── McpHandlerObservationInterceptor (mocapi-o11y)
      AUDIT         ── AuditLoggingInterceptor (mocapi-audit)
         AUTHORIZATION ── Guard evaluation (mocapi-server)
            VALIDATION    ── input-schema + Jakarta (mocapi-server, mocapi-jakarta-validation)
               INVOCATION    ── user-attached interceptors → reflective method call
```

The order matters because each stratum has a job that depends on what's
inside it being observable:

- **CORRELATION outermost** so MDC keys are present on every log line
  emitted by everything inside, including audit events.
- **OBSERVATION outside AUDIT** so the Micrometer span / meter records
  the full wall-clock duration including any audit work, and so
  observation propagation crosses the audit boundary.
- **AUDIT outside AUTHORIZATION** so a denied call still produces an
  audit record with `outcome=forbidden`. Audit doesn't have to know
  whether the guard fired; it sees the post-guard outcome and classifies
  it.
- **AUTHORIZATION outside VALIDATION** so a request that would fail
  validation but isn't allowed in the first place returns `-32010
  Forbidden` (ADR-0023), not `-32602 Invalid params`. Information leak prevention.
- **VALIDATION outside INVOCATION** so a structurally invalid request
  never reaches user code.

Denials and exceptions bubble up through every outer stratum on their way
out, which is the entire point of the ordering. Every attempt — allowed
or blocked — is correlated (MDC keys present), observable (one
observation recorded with an `outcome` tag), and audited (one audit
record with `outcome=forbidden|invalid_params|error|success`). No
attempt slips past the o11y net.

## Cross-cutting: context propagation

The streamable HTTP transport spawns a fresh virtual thread per call.
`Thread.ofVirtual().start(...)` does not inherit `ThreadLocal`-backed
context — `SecurityContextHolder`, Micrometer Observation scope, and MDC
all live in `ThreadLocal`. `mocapi-streamable-http-transport` wires
`io.micrometer:context-propagation` into the spawn so every registered
`ThreadLocalAccessor` is captured on the request thread and restored on
the handler virtual thread. This is what makes:

- Spring Security's `Authentication` visible to guards running inside the
  virtual thread.
- Tracing parent-child relationships intact (`http post /mcp` is the
  parent of `jsonrpc.server` is the parent of `mcp.handler.execution` —
  unless the request's `_meta` carries a `traceparent`, in which case
  the handler span's parent is the client's remote span).
- MDC keys present on log lines emitted from user handler code.

Users don't configure this; it is a transitive behavior of having the
HTTP transport on the classpath.

## What's not in the stack

- **Per-invocation mutable metadata.** Methodical's `MethodInvocation`
  doesn't carry one. Interceptors that need shared per-call state use
  `ScopedValue`.
- **A unified observer SPI.** The customizer-driven interceptor model
  *is* the observer SPI. There is no parallel API.
- **Rate limiting.** Trivial to implement on the customizer SPI; mocapi
  doesn't ship one. Users or third-party starters do.
- **Full payloads in the audit log.** PII surface; opt-in
  `arguments_hash` is the closest thing.

## Related

- [Observability guide](../guides/observability.md) — recipe-level
  configuration for metrics, tracing, exporters.
- [Logging guide](../guides/logging.md) — MDC key reference, Logback
  pattern.
- [Audit guide](../guides/audit.md) — field vocabulary, SIEM routing.
- [Actuator guide](../guides/actuator.md) — `/actuator/mcp` shape and
  query examples.
- [ADR-0017](../adr/0017-observability-stack.md) — stack composition.
- [ADR-0011](../adr/0011-customizer-spi-and-strata.md) — customizer SPI
  and stratum model.
- [`authorization-model.md`](authorization-model.md) — where AUTHORIZATION
  fits, and what guards do.
