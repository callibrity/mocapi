# `mocapi-audit`: structured audit logging for every handler invocation

## What to build

Ship `mocapi-audit` (code module) + autoconfig in
`mocapi-autoconfigure`. On every MCP handler invocation, emit one
structured log event with fields suitable for compliance queries:
caller identity, session id, handler kind, handler name, outcome,
duration, and (opt-in) an arguments hash. Logger name is
`mocapi.audit` so ops can route audit events to a dedicated sink
— file, SIEM, Kafka, whatever — without mixing with app logs.

### Why

Regulated environments (SOC2 / HIPAA / PCI / internal audit
policies) require an answer to "who called what tool when with
what outcome" that's stable, queryable, and doesn't get lost in
free-form log noise. Structured logging (SLF4J 2.0 fluent API
with `addKeyValue(...)`) gives downstream tools — Splunk,
Datadog, ELK, CloudWatch — machine-queryable fields so compliance
queries become SQL-ish instead of regex-over-prose.

Mocapi already has the shape for this: a customizer SPI
(spec 180) that attaches a `MethodInterceptor` per handler, a
`McpSession.CURRENT` ScopedValue for session correlation, and
spec 182's context propagation so Spring Security's
`SecurityContextHolder` is populated on the handler VT. Audit
slots in as another customizer-attached interceptor.

### Module layout

```
mocapi-audit/                                    (code module)
  src/main/java/com/callibrity/mocapi/audit/
    AuditLoggingInterceptor.java                — the interceptor
    AuditCallerIdentityProvider.java            — pluggable caller extraction
    DefaultAuditCallerIdentityProvider.java     — reads SecurityContextHolder when present
    AuditFieldKeys.java                          — the structured-field name constants
    MocapiAuditProperties.java                   — config properties
```

```
mocapi-autoconfigure/.../audit/
  MocapiAuditAutoConfiguration.java              — declares customizer beans + provider default
```

`mocapi-audit` depends on `mocapi-server` (for customizer SPI,
session access, `Guard` / `HandlerKinds` sharing) and
`slf4j-api`. Spring Security is an **optional** dependency —
the `DefaultAuditCallerIdentityProvider` uses it reflectively
with a `@ConditionalOnClass` guard so users without Spring
Security still get audit (with `caller=anonymous`).

### The interceptor

```java
public final class AuditLoggingInterceptor implements MethodInterceptor<Object> {

    private static final Logger log = LoggerFactory.getLogger("mocapi.audit");

    private final String handlerKind;
    private final String handlerName;
    private final AuditCallerIdentityProvider callerProvider;
    private final boolean hashArguments;    // opt-in per MocapiAuditProperties

    // ctor stores all four

    @Override
    public Object intercept(MethodInvocation<?> invocation) throws Throwable {
        long startNanos = System.nanoTime();
        String caller = safeCaller();
        String sessionId = safeSessionId();
        String argsHash = hashArguments ? computeArgsHash(invocation) : null;

        String outcome = "success";
        Throwable failure = null;
        try {
            return invocation.proceed();
        } catch (Throwable t) {
            outcome = classifyOutcome(t);  // "forbidden", "invalid_params", "error"
            failure = t;
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            var event = log.atInfo()
                    .addKeyValue(AuditFieldKeys.CALLER, caller)
                    .addKeyValue(AuditFieldKeys.SESSION_ID, sessionId)
                    .addKeyValue(AuditFieldKeys.HANDLER_KIND, handlerKind)
                    .addKeyValue(AuditFieldKeys.HANDLER_NAME, handlerName)
                    .addKeyValue(AuditFieldKeys.OUTCOME, outcome)
                    .addKeyValue(AuditFieldKeys.DURATION_MS, durationMs);
            if (argsHash != null) {
                event = event.addKeyValue(AuditFieldKeys.ARGUMENTS_HASH, argsHash);
            }
            if (failure != null) {
                event = event.addKeyValue(AuditFieldKeys.ERROR_CLASS, failure.getClass().getSimpleName());
            }
            event.log("mcp.audit");
        }
    }
}
```

One instance per handler, kind + name closed over at customizer
time. Hot path: two `System.nanoTime()` calls, six-to-eight
`addKeyValue` calls, one SLF4J event emission. No reflection,
no allocation beyond the event builder itself.

### Fields emitted

| Key | Source | Notes |
|---|---|---|
| `caller` | `AuditCallerIdentityProvider` | Default reads `SecurityContextHolder.getContext().getAuthentication().getName()`; falls back to `"anonymous"` |
| `session_id` | `McpSession.CURRENT.get().sessionId()` | `null` at initialize-time (no session bound yet) |
| `handler_kind` | closed over | `tool` / `prompt` / `resource` / `resource_template` |
| `handler_name` | closed over | Tool/prompt name or resource URI / URI template |
| `outcome` | try/catch classification | `success` / `forbidden` / `invalid_params` / `error` |
| `duration_ms` | nanoTime diff | Integer ms |
| `arguments_hash` | opt-in, SHA-256 of canonical JSON | Only when `mocapi.audit.hash-arguments=true` |
| `error_class` | exception simple name | Only when invocation failed |

Field names are `snake_case` to match typical structured-log
conventions and avoid Java-camelCase bleeding into log
dashboards. Constants live in `AuditFieldKeys` so users writing
queries / parsers can reference them without hardcoded strings.

`outcome` values normalized to a small vocabulary so dashboards
can aggregate: `success`, `forbidden` (guard denied → JSON-RPC
-32003), `invalid_params` (validation failure → -32602),
`error` (anything else). Tools returning
`CallToolResult.isError=true` still count as `success` at the
audit layer — protocol exchange succeeded; it's a model-visible
tool error, not an infrastructure failure. Matches the o11y
convention.

**What's deliberately NOT emitted:**

- **Full arguments.** PII / secrets risk. If users want them,
  they opt into `arguments_hash` for correlation without
  recording contents, or write their own interceptor.
- **Full response payloads.** Same reason.
- **Stack traces.** Class name only. Audit is for compliance
  queries, not debugging.

### Caller identity — pluggable

```java
public interface AuditCallerIdentityProvider {
    /**
     * Returns the current caller's identity, or null / "anonymous"
     * when the audit event is happening outside an authenticated
     * context (e.g., initialize-time).
     */
    String currentCaller();
}
```

Default bean wires up conditionally:

- Spring Security on classpath → `DefaultAuditCallerIdentityProvider`
  reads `SecurityContextHolder.getContext().getAuthentication().getName()`.
  Returns `"anonymous"` when no Authentication or not authenticated.
- Spring Security absent → a trivial provider returns `"anonymous"`.

Users who want different identity semantics (e.g., "caller =
tenant-id + user-id composite") provide their own
`AuditCallerIdentityProvider` `@Bean`; mocapi's default is
`@ConditionalOnMissingBean`.

### Configuration

```properties
# Enable arguments-hash field (default: false — hash can help correlation,
# but even a hash of sensitive arguments can be a weak fingerprint, so opt-in).
mocapi.audit.hash-arguments=false

# Dedicated logger can be configured separately from the rest:
# logging.level.mocapi.audit=INFO  (already the default)
```

A `mocapi.audit.enabled` toggle isn't provided — the activation
rule is "is the module on the classpath?" If users want to
disable audit temporarily, they can set the `mocapi.audit`
logger level to `OFF`. Simpler than another boolean.

### Chain ordering

Audit is a customizer-attached interceptor. Position in the
chain matters:

1. **MDC** (spec 181) — sets correlation keys. Must run before
   audit so the audit log line carries MDC context (session id,
   handler kind/name) in the formatted output if the user's
   encoder pulls from MDC.
2. **Audit** (this spec) — records the attempt, times the
   whole invocation, catches exceptions for outcome
   classification.
3. **O11y** (spec 183) — observations. Runs inside audit so
   the observation's duration is slightly shorter than audit's
   duration (a negligible delta, but conceptually audit measures
   "how long did the caller wait" including o11y's start/stop
   overhead).
4. **User customizer interceptors** — their stuff.
5. **Guard evaluation** (spec 193) — denials throw inside the
   audit scope so audit catches them and records
   `outcome=forbidden`.
6. **Schema validation** (tools only) — validation failures
   caught as `outcome=invalid_params`.
7. **Method invocation.**

Achieved via Spring `@Order` on mocapi's customizer beans:

- MDC customizer: `@Order(100)`
- Audit customizer: `@Order(200)`
- O11y customizer: `@Order(300)`

User customizers default to `Ordered.LOWEST_PRECEDENCE` unless
they annotate otherwise; if they want to run outside audit, they
annotate with `@Order(< 200)`.

**Note:** this spec introduces explicit `@Order` values on the
existing MDC and o11y customizer beans — they don't have them
today. Audit needs them to pin its position; the others get
pinned alongside.

### Example output

With a plain console encoder:

```
2026-04-19T17:22:11.043Z INFO  mocapi.audit - mcp.audit caller=alice session_id=5d3cb2b3 handler_kind=tool handler_name=get_weather outcome=success duration_ms=42
```

With `logstash-logback-encoder` (JSON):

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

We don't bundle the encoder. `docs/audit.md` points users at
`logstash-logback-encoder` and the three-line appender config
needed to route `mocapi.audit` to its own file / stream.

### Activation

```java
@AutoConfiguration
@ConditionalOnClass({AuditLoggingInterceptor.class})
@EnableConfigurationProperties(MocapiAuditProperties.class)
public class MocapiAuditAutoConfiguration {

    @Bean @ConditionalOnMissingBean
    AuditCallerIdentityProvider auditCallerIdentityProvider(/* optional SecurityContextHolder delegate */) { ... }

    @Bean @Order(200) CallToolHandlerCustomizer auditToolCustomizer(...) { ... }
    @Bean @Order(200) GetPromptHandlerCustomizer auditPromptCustomizer(...) { ... }
    @Bean @Order(200) ReadResourceHandlerCustomizer auditResourceCustomizer(...) { ... }
    @Bean @Order(200) ReadResourceTemplateHandlerCustomizer auditResourceTemplateCustomizer(...) { ... }
}
```

Activation trigger: `AuditLoggingInterceptor.class` on classpath
→ `mocapi-audit` module is present. No bean condition, no
registry dependency. Drop in the module, audit happens. Turn it
off with `logging.level.mocapi.audit=OFF` if you need to silence
it temporarily.

## Acceptance criteria

- [ ] New module `mocapi-audit` with the seven classes listed
      above.
- [ ] `MocapiAuditAutoConfiguration` in `mocapi-autoconfigure`;
      `mocapi-audit` declared as `<optional>true</optional>`
      dependency of `mocapi-autoconfigure`.
- [ ] `AuditLoggingInterceptor` emits exactly one INFO log event
      per invocation on logger `mocapi.audit`, with the seven
      fields listed above (five always, two conditional).
- [ ] `outcome` values normalized to `success` / `forbidden` /
      `invalid_params` / `error`.
- [ ] `arguments_hash` field present only when
      `mocapi.audit.hash-arguments=true`; hash is SHA-256 over
      the canonicalized arguments (Jackson serialization with
      keys sorted) rendered as hex prefixed `sha256:`.
- [ ] `CallToolResult.isError=true` classifies as `success`
      (protocol-level success). Only infrastructure-level
      failures classify otherwise.
- [ ] `DefaultAuditCallerIdentityProvider` activates when
      Spring Security is on the classpath; reads
      `SecurityContextHolder.getContext().getAuthentication()`;
      returns `"anonymous"` when absent / not authenticated.
- [ ] When Spring Security is not on the classpath, a fallback
      provider returns `"anonymous"`.
- [ ] Users can override with a custom
      `AuditCallerIdentityProvider` `@Bean`;
      `@ConditionalOnMissingBean` on the default.
- [ ] Customizer beans carry `@Order(200)`; existing MDC
      customizer beans get `@Order(100)`; existing o11y
      customizer beans get `@Order(300)`. Ordering verified by
      integration test: in-flight interceptor chain sequence is
      MDC → audit → o11y → user → guard → schema → method.
- [ ] Integration test (per handler kind): invoke with a
      successful handler, confirm a single audit event with
      `outcome=success`, correct handler_kind/name, and
      non-negative duration_ms.
- [ ] Integration test: invoke a handler guarded by a denying
      guard, confirm audit event fires with `outcome=forbidden`.
- [ ] Integration test: invoke a tool where Jakarta Validation
      rejects input, confirm audit event fires with
      `outcome=invalid_params`.
- [ ] Integration test: invoke a handler that throws a
      `RuntimeException`, confirm audit event fires with
      `outcome=error` and `error_class` set.
- [ ] Integration test: `arguments_hash` is stable across calls
      with the same arguments and different across differing
      arguments, only when `mocapi.audit.hash-arguments=true`.
- [ ] Integration test: default provider returns `"anonymous"`
      when no `Authentication` is present on the handler VT,
      and returns the correct name when it is (exercised via
      the MCP streamable-HTTP transport with spec-182 context
      propagation).
- [ ] `docs/audit.md` — new doc covering: field vocabulary,
      pluggable caller provider, example Logback appender /
      JSON encoder config, query examples for Splunk /
      ELK-style fields.
- [ ] `README.md` "Modules" section gains
      `mocapi-audit` with a one-line description.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Added`: entry.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Bundling a JSON encoder.** Users configure
  `logstash-logback-encoder` or equivalent themselves.
- **Audit event persistence / ordering guarantees.** We emit
  via SLF4J; shipping them to Kafka / SIEM is the user's
  logging pipeline's job.
- **Emitting arguments / response bodies.** PII surface.
  `arguments_hash` is the only opt-in affordance; full bodies
  are never emitted.
- **Per-handler opt-out / opt-in annotations** (e.g.,
  `@SkipAudit`). If a user wants per-handler control, they
  can inspect the method annotation in their own
  customizer-attached interceptor. YAGNI for v1.
- **Audit levels beyond INFO.** Single level keeps operator
  configuration simple.
- **Pre-populating MDC from audit fields.** Audit and MDC are
  orthogonal; spec 181's MDC customizer already sets the
  handler kind/name/session keys on MDC — audit reads them from
  its own closed-over fields rather than from MDC, so the two
  don't have to depend on ordering for correctness.

## Implementation notes

- `LoggerFactory.getLogger("mocapi.audit")` (not
  `getClass().getName()`) — the logger name is user-visible
  contract. Document it.
- `System.nanoTime()` twice for duration; don't use clock time
  (could jump backward on NTP correction). Integer ms precision
  is enough for audit dashboards.
- SHA-256 hashing uses the shared `Hashes.sha256Of(...)`
  helper from spec 195
  (`com.callibrity.mocapi.server.util.Hashes`); do not
  re-implement `MessageDigest` / `HexFormat` locally.
  Canonicalize JSON via `ObjectMapper.writeValueAsBytes` with
  a key-sorted writer first, then pass the bytes to
  `Hashes.sha256Of(byte[])` so the same logical arguments
  always produce the same hash.
- `outcome` classification: guard denies surface as
  `JsonRpcException` with code `FORBIDDEN` (-32003, from spec
  187); Jakarta validation as `-32602`. Classify on the
  `JsonRpcException.code()` before falling back to "error."
- `AuditFieldKeys` constants: `CALLER = "caller"`,
  `SESSION_ID = "session_id"`, etc. Public so users can
  reference them when writing custom conventions / queries.
- The `log.atInfo().addKeyValue(...).log("mcp.audit")` pattern
  is SLF4J 2.0's fluent API. We're already on SLF4J 2.x; no
  version bump needed. The string `"mcp.audit"` is the message
  — a fixed marker so dashboards can filter/group by it
  instead of parsing the dynamic fields.
- Consider a future follow-up to spec 181 (MDC) that pulls
  `caller` into MDC so non-audit log lines emitted during the
  handler invocation also carry caller context. Out of scope
  for this spec.
