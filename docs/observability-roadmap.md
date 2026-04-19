# Observability roadmap

Now that mocapi's handler layer runs on Methodical 0.6 interceptors
(spec 175, on top of the 170–174 cleanup), every cross-cutting
concern below collapses into "ship a `MethodInterceptor` bean + a
thin autoconfig + a Spring Boot starter." Pop the starter onto the
classpath, it auto-wires into every handler of the matching kind.

---

## MDC (correlation keys in SLF4J) — ✅ shipped (spec 178)

Sets MDC attributes for the duration of every handler invocation so
every log line emitted during the call — including lines from user
handler code — carries correlation context:

- `mcp.session` — current MCP session id (only set when a session is bound)
- `mcp.request` — JSON-RPC request id (reserved; wired by a follow-up spec)
- `mcp.handler.kind` — `tool` / `prompt` / `resource` / `resource_template`
- `mcp.handler.name` — handler name (tool/prompt name or resource URI / URI template)

Shipped as `mocapi-logging` + `mocapi-logging-spring-boot-starter`. See
[`docs/logging.md`](logging.md) for the Logback pattern snippet and the
virtual-threads caveat. Wiring: the autoconfig exposes one
`*HandlerCustomizer` bean per handler kind (spec 180 SPI); each
customizer reads the handler's descriptor name / uri / uriTemplate and
attaches a per-handler `McpMdcInterceptor` with the kind/name baked in.
The hot path does no reflection. The annotation-introspection helper
(`com.callibrity.mocapi.api.handlers.HandlerKinds`) in `mocapi-api` is
no longer consulted by `mocapi-logging` but remains available for other
code that needs to classify a `Method` outside of a Config-carrying
context, and is the shared point of reference for the metrics and
tracing starters queued next.

## Metrics + Tracing (Micrometer Observation API) — ✅ shipped (spec 183)

One interceptor wraps every handler invocation in a Micrometer
`Observation`. The same code emits **both** metrics and distributed-
tracing spans: whichever `ObservationHandler`s the user has on their
classpath participate automatically — `MeterObservationHandler`
converts events to `Timer` / `Counter`, `TracingObservationHandler`
converts events to spans. No separate metrics or tracing interceptors.

Observation names (one per handler kind, so meter dashboards get
distinct `Timer`s rather than a single meter partitioned by tag):

- `mcp.tool`
- `mcp.prompt`
- `mcp.resource`
- `mcp.resource_template`

Low-cardinality tags: `mcp.handler.kind` (`tool` / `prompt` /
`resource` / `resource_template`) and `mcp.handler.name` (tool /
prompt name, or resource URI / URI template). The standard Micrometer
`outcome` tag (`SUCCESS` / `ERROR`) is added automatically by
`DefaultMeterObservationHandler` on exception.

Shipped as `mocapi-o11y` + `mocapi-o11y-spring-boot-starter`. Only
activates when an `ObservationRegistry` bean is present — Spring Boot
3+ auto-creates one whenever `spring-boot-starter-actuator` or any
Micrometer Observation autoconfiguration is on the classpath, so the
starter lights up automatically when paired with a metrics or tracing
stack.

Users who prefer `@Observed` on individual tool methods (Spring
Boot's built-in AOP advice) can skip this starter and wire their own
observations; the two paths don't fight but do nest if both are
enabled on the same method.

Deferred follow-ups: session / request attributes on the Observation
(pending the MDC request-id piece of spec 178); an `mcp.tool.is_error`
attribute on `CallToolResult.isError=true` results if the standard
`outcome` tag turns out to be insufficient.

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
