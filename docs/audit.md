# Audit logging

`mocapi-audit` emits one structured log event per MCP handler invocation on a
dedicated SLF4J logger — `mocapi.audit` — suitable for compliance queries,
SIEM pipelines, and "who called what, when, with what outcome" dashboards.

## Activation

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-audit</artifactId>
</dependency>
```

That's it. The autoconfig attaches one `AuditLoggingInterceptor` per
tool / prompt / resource / resource-template via the per-handler
customizer SPI; no mocapi-API calls required.

To silence audit temporarily without removing the module, set
`logging.level.mocapi.audit=OFF`.

## Field vocabulary

| Key | Always present? | Value |
|---|---|---|
| `caller` | yes | Authenticated principal name from the pluggable `AuditCallerIdentityProvider`, or `anonymous`. |
| `session_id` | yes (may be `null`) | The MCP session id when a session is bound to the invocation. `null` during the `initialize` handshake. |
| `handler_kind` | yes | One of `tool`, `prompt`, `resource`, `resource_template`. |
| `handler_name` | yes | Tool / prompt name, or resource URI / URI template. |
| `outcome` | yes | One of `success`, `forbidden`, `invalid_params`, `error`. |
| `duration_ms` | yes | Wall-clock invocation duration, in integer milliseconds. |
| `arguments_hash` | only when `mocapi.audit.hash-arguments=true` | SHA-256 over the key-sorted canonical JSON of the arguments, prefixed `sha256:`. |
| `error_class` | only on failed invocations | Simple name of the thrown exception class. |

All fields use `snake_case` so dashboards and SQL-over-log tools can reference
them without Java camelCase bleeding in. The constants live on
`com.callibrity.mocapi.audit.AuditFieldKeys`.

### Outcome classification

| Value | Trigger |
|---|---|
| `success` | The invocation completed without an infrastructure-level exception. A tool returning `CallToolResult.isError=true` still counts as `success` here — it's a model-visible tool error, not an audit-level failure. |
| `forbidden` | A `JsonRpcException` with code `-32003 Forbidden` (a guard denial — see `mocapi-spring-security-guards`). |
| `invalid_params` | A `JsonRpcException` with code `-32602 Invalid params` (e.g., Jakarta Validation rejected the inputs). |
| `error` | Any other thrown exception. Stack traces are not emitted — only `error_class`. |

## Caller identity

`AuditCallerIdentityProvider` is a single-method SPI:

```java
String currentCaller();
```

The default bean:

- **When Spring Security is on the classpath** — `DefaultAuditCallerIdentityProvider`
  reads `SecurityContextHolder.getContext().getAuthentication().getName()` and
  falls back to `"anonymous"` when nothing is authenticated.
- **When Spring Security is absent** — a trivial provider that always returns
  `"anonymous"`.

Override either default by declaring your own `AuditCallerIdentityProvider`
bean:

```java
@Bean
AuditCallerIdentityProvider tenantScopedCaller(TenantContext tenants) {
    return () -> tenants.current().map(t -> t.id() + ":" + t.userId()).orElse("anonymous");
}
```

The default is `@ConditionalOnMissingBean`, so your bean wins.

## Arguments hash

Off by default. Turn it on with

```properties
mocapi.audit.hash-arguments=true
```

When enabled, the interceptor serializes the arguments to key-sorted canonical
JSON and emits `sha256:<hex>` as `arguments_hash`. This is enough to correlate
"did these two calls pass identical inputs?" without persisting (or
transmitting) the input values themselves.

Even a hash can be a weak fingerprint of sensitive inputs (e.g., if the input
space is small and known, a hash is effectively a label), which is why this is
an opt-in. If hashing doesn't meet your threat model, leave it off.

## Routing to a file / SIEM

Audit events use the logger name `mocapi.audit`. Routing them somewhere
separate from the app log is a two-step Logback config:

```xml
<appender name="AUDIT" class="ch.qos.logback.core.FileAppender">
  <file>audit.log</file>
  <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>

<logger name="mocapi.audit" level="INFO" additivity="false">
  <appender-ref ref="AUDIT"/>
</logger>
```

`additivity="false"` keeps audit events out of the root appender so they don't
intermingle with ordinary log lines.

With [`logstash-logback-encoder`](https://github.com/logfellow/logstash-logback-encoder)
installed, the structured fields flow into the JSON document automatically:

```json
{
  "@timestamp": "2026-04-19T17:22:11.043Z",
  "level": "INFO",
  "logger_name": "mocapi.audit",
  "message": "mcp.audit",
  "caller": "alice",
  "session_id": "5d3cb2b3",
  "handler_kind": "tool",
  "handler_name": "get_weather",
  "outcome": "success",
  "duration_ms": 42
}
```

With a plain console encoder the same fields render as `key=value` tails on
each line:

```
2026-04-19T17:22:11.043Z INFO mocapi.audit - mcp.audit caller=alice session_id=5d3cb2b3 handler_kind=tool handler_name=get_weather outcome=success duration_ms=42
```

## Interceptor ordering

`mocapi-audit` contributes its interceptor to the **AUDIT** stratum of the
handler chain (see [customizers.md](customizers.md#strata) for the full
stratum model). The outer-to-inner sequence around it is:

1. **CORRELATION** (MDC) — stamps `mcp.session` / `mcp.handler.kind` / `mcp.handler.name`.
2. **OBSERVATION** (Micrometer observation) — wraps the rest.
3. **AUDIT** (this module) — records the attempt, times it end-to-end. Sees post-guard outcomes, so a denial surfaces as `outcome=forbidden`.
4. **AUTHORIZATION** — guards evaluated here; denial short-circuits with `-32003`.
5. **VALIDATION** — JSON input-schema for tools first (rejections surface as `outcome=invalid_params`), then user-contributed validation (e.g. Jakarta Bean Validation).
6. **INVOCATION** — any user-contributed invocation interceptors, then the reflective method call.

Because AUDIT sits outer of AUTHORIZATION, audit records every attempt —
allowed or forbidden — and never has to reason about whether the guard
fired. If you want a user interceptor that runs **outside** the audit scope
(e.g., one that logs before audit kicks in), contribute it to CORRELATION or
OBSERVATION instead.

## What is deliberately NOT emitted

- **Full argument payloads.** PII surface.
- **Full response payloads.** Same reason.
- **Stack traces.** Class name only — audit is for compliance queries, not
  debugging.

If you need the full payloads, write your own customizer-attached interceptor.
The audit module's scope is intentionally compliance-shaped.

## Query examples

**Splunk — failed tool calls by caller, last 24h:**

```
index=mocapi source=audit.log outcome!="success" handler_kind="tool"
| stats count by caller, handler_name, outcome
```

**ELK — slowest tools, top 10:**

```
logger_name:"mocapi.audit" AND handler_kind:"tool"
| GROUP BY handler_name
| AGGREGATE p95(duration_ms) AS p95
| SORT p95 DESC LIMIT 10
```

**CloudWatch Insights — guard denials per caller:**

```
fields caller, handler_name, outcome
| filter logger_name = "mocapi.audit" and outcome = "forbidden"
| stats count() as denials by caller, handler_name
| sort denials desc
```
