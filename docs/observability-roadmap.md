# Observability roadmap

Once mocapi's handler layer lands on Methodical 0.6 interceptors
(specs 170–174), every cross-cutting concern below collapses into
"ship a `MethodInterceptor` bean + a thin autoconfig + a Spring Boot
starter." Pop the starter onto the classpath, it auto-wires.

These aren't specced yet — they'll be specced **after** the handler
rewrite so their APIs can assume the interceptor model.

---

## MDC (correlation keys in SLF4J)

Set MDC attributes for the duration of every handler invocation so
every log line emitted during the call carries correlation context:

- `mcp.session` — current MCP session id
- `mcp.request` — JSON-RPC request id
- `mcp.handler.kind` — `tool` / `prompt` / `resource` / `resource-template`
- `mcp.handler.name` — handler name

A reusable MDC helper already exists in Methodical's stereotype
package (`ScopedValueInterceptor`-style pattern). Shipped as
`mocapi-logging-spring-boot-starter`.

## Metrics (Micrometer)

Emit standard meters per invocation, tagged by handler kind and name:

- `mcp.tool.invocations` (Counter), `mcp.tool.duration` (Timer),
  `mcp.tool.active` (Gauge) — plus `prompt` / `resource` variants.
- Outcome tag: `success` / `error`. Tool results with `isError=true`
  count as `success` (protocol-successful; model-visible error).
- Opt-in per-handler metric tags via plugin-contributed metadata (see
  interceptor spec).

Shipped as `mocapi-metrics-spring-boot-starter`; only activates when
a `MeterRegistry` bean is present.

## Tracing (OpenTelemetry)

One span per handler invocation, named `mcp.<kind> <handlerName>`,
kind `INTERNAL`, with attributes for handler kind / name, session id,
request id, and (for tools) an `mcp.tool.is_error` flag on
`isError=true` results. Exceptions record on the span and set status
to `ERROR`.

Shipped as `mocapi-tracing-spring-boot-starter`; depends only on
`opentelemetry-api`. Users bring their own SDK + exporter.

## Actuator endpoint

`/actuator/mcp` returns server info, handler counts, and per-handler
metadata (name + schema digest). Does not expose session state
(mocapi is multi-node; sessions live in the backing store, not on one
node). When the metrics starter is also present, the endpoint folds
in a metrics snapshot.

Shipped as `mocapi-actuator-spring-boot-starter`.

## Guards / entitlements (visibility)

Per-handler predicates that filter *list* operations. A caller who
isn't entitled to see a tool won't see it in `tools/list`; likewise
for prompts / resources / resource templates. Pure visibility — call-
time authorization is a separate concern and is not what this
feature does.

Plugins (Spring Security, OAuth2 scopes, RBAC, tenant checks,
whatever) attach a guard during handler discovery via a mocapi-owned
SPI. Mocapi itself knows nothing about the user's auth model.

Intentionally queued *after* the interceptor refactor lands — the
interceptor-based handler model makes this a clean additive API
rather than a coupled core concern.

Shipped as mocapi-internal machinery (no separate starter); users
write their own `Guard` implementations or pull in starters like
`mocapi-spring-security-entitlements` that read their existing
annotations.

## Rate limiting

`MethodInterceptor` that consults a rate-limit policy per tool. Token
bucket, leaky bucket, Redis-backed, whatever. Not in mocapi core —
users or third-party starters ship this.

## Audit logging

`MethodInterceptor` that records who called what, when, with what
outcome. Structured JSON lines to a dedicated logger. Not in core —
opt-in starter.

---

## Non-goals (for now)

- **Per-invocation mutable metadata** on `MethodInvocation`.
  Methodical intentionally doesn't carry one; interceptors that need
  to share per-call state use `ScopedValue`.
- **A unified observer SPI**. The whole point of the interceptor
  model is that everything *is* the observer SPI. No parallel API.
- **Baking scope / role / tenant models into mocapi.** Entitlement
  plugins own those semantics.
