# ADR-0017 — Optional, independently-activatable observability stack

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

Observability needs are deployment-specific. A local-development server
wants log lines with correlation context but no metrics infrastructure.
A regulated production deployment needs structured audit logs to a
SIEM, full Micrometer metrics, distributed tracing, and an actuator
inventory endpoint. A serverless deployment may want only the actuator
endpoint for warm-start health checks.

Bundling all of that into one "observability" jar forces every
deployment to inherit every dependency — Micrometer, micrometer-tracing,
Spring Boot Actuator, audit-specific configuration — even when only one
is actually used. A monolithic jar also makes "what does mocapi do?"
hard to answer; the user has to read multiple unrelated features
mashed together.

The customizer SPI ([ADR-0011](0011-customizer-spi-and-strata.md))
provides exactly the seam needed: each observability concern attaches
one customizer per handler kind, in its appropriate stratum, without
the others knowing it exists. Splitting along that seam yields
independently-activatable starters that compose by classpath
membership.

## Decision

Mocapi ships five optional observability starters. Each has its own
code module and its own Spring Boot starter. They are independently
activatable — adding any one to the classpath enables it; removing it
disables it; no module depends on another.

| Starter | Stratum / Layer | Purpose |
|---|---|---|
| `mocapi-logging` | CORRELATION | SLF4J MDC: `mcp.session`, `mcp.handler.kind`, `mcp.handler.name`, `mcp.request` |
| `mocapi-o11y` | OBSERVATION | Micrometer `Observation` per handler call |
| `mocapi-otel` | OBSERVATION | Source-less bundle: `mocapi-o11y` + `spring-boot-starter-opentelemetry` (Observation → OpenTelemetry tracing) |
| `mocapi-audit` | AUDIT | Structured audit on a dedicated SLF4J logger |
| `mocapi-actuator` | (separate from MCP protocol) | `/actuator/mcp` read-only inventory endpoint |

**`mocapi-logging`.** Attaches a `McpMdcInterceptor` per handler kind
via the CORRELATION stratum. Sets MDC keys for the duration of every
handler invocation so every log line emitted during the call — including
lines from user code — carries correlation context automatically. Cleared
on exit so MDC doesn't leak across virtual threads.

**`mocapi-o11y`.** Attaches one `McpHandlerObservationInterceptor` per
handler kind via the OBSERVATION stratum. A single `Observation` per
handler call produces both metrics and tracing spans — whichever
Micrometer `ObservationHandler`s the user has on classpath
(`MeterObservationHandler`, `TracingObservationHandler`, anything else)
participate via the standard Micrometer registry mechanism. No
separate metrics interceptor and tracing interceptor; one observation
covers both. Low-cardinality keys (`mcp.handler.kind`,
`mcp.handler.name`) are baked in at customizer attachment time so the
hot path has zero reflection. The module also contributes a
`JsonRpcMethodHandlerCustomizer` that emits an outer `jsonrpc.server`
observation around the entire JSON-RPC dispatch.

**`mocapi-otel`.** A source-less dependency bundle (the same pattern as
`mocapi-jakarta-validation`): it declares `mocapi-o11y` plus Spring
Boot's `spring-boot-starter-opentelemetry` so that the Micrometer
`Observation`s `mocapi-o11y` already emits are bridged to OpenTelemetry
tracing. No observation code of its own, no backend exporter (that is
deployment-specific), and no default properties. With it on the
classpath, traces flow `http → jsonrpc.server → mcp.handler.execution`,
and a request whose `_meta` carries W3C trace-context keys joins the
client's remote-parent trace. Added after the original four starters;
[ADR-0022](0022-2026-07-28-features-not-implemented.md) cites it as the
spec-suggested replacement for the deprecated MCP Logging feature.

**`mocapi-audit`.** Attaches an `AuditLoggingInterceptor` per handler
kind via the AUDIT stratum. Emits one structured event per invocation
on the dedicated SLF4J logger `mocapi.audit` using the SLF4J 2.0 fluent
API (`logger.atInfo().addKeyValue(...).log()`). Fields cover caller
identity (extracted via a pluggable `AuditCallerIdentityProvider` —
default reads `SecurityContextHolder` reflectively when Spring Security
is on classpath), session id, handler kind and name, outcome,
duration, and an opt-in arguments hash. The dedicated logger name lets
ops route audit events to a separate sink (file, Kafka, SIEM) without
mixing with application logs.

**`mocapi-actuator`.** Contributes a Spring Boot Actuator endpoint at
`/actuator/mcp` that returns a read-only snapshot of registered tools,
prompts, resources, and resource templates. Purely introspective:
doesn't touch sessions, doesn't call handlers, doesn't mutate.
Deliberately a Spring Boot Actuator endpoint, not an MCP-protocol
operation — operations and platform teams expect actuator shape, and
the endpoint is reachable without an authenticated MCP session. The
inventory comes from the same handler list each handler autoconfig
already exposes for the customizer SPI, so there is no parallel
registry.

Each starter follows the same pattern: feature *code* in
`mocapi-<feature>`, feature *autoconfig* in `mocapi-autoconfigure`,
`@ConditionalOnClass` triggered by a class from the feature module so
classpath presence is what activates wiring.

## Consequences

**What this buys us.** Deployments pull in only what they want;
dependency footprints stay focused. Each observability concern is a
small, focused module that's easy to read top-to-bottom. Adding a
fifth observability concern (e.g., a request-rate-limit metric) is
another module, not an edit to a monolith. Customizer SPI does the
heavy lifting; the modules are tiny.

**Costs.** A user wanting "all the observability" lists four
starters in their POM. Mitigation: the documentation lists the
canonical bundle; future work could ship a meta-starter that
transitively pulls all four.

**Non-goals.** Mocapi does not ship a logback configuration, a
Prometheus scrape config, an OpenTelemetry collector setup, or a
Splunk parser. Those are deployment concerns; the modules just emit
the data.

**Code anchors:** `mocapi-logging/`, `mocapi-o11y/`, `mocapi-otel/` (source-less bundle — see `mocapi-otel/pom.xml`), `mocapi-audit/`, `mocapi-actuator/`. MDC key constants in `mocapi-logging/.../McpMdcKeys.java`.
