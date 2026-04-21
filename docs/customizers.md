# Customizers

Mocapi's extension mechanism is a per-handler **customizer SPI**.
One `*HandlerCustomizer` bean per handler kind is invoked at startup
for every discovered handler (tool / prompt / resource / resource
template), and can read the handler's descriptor / method / bean
and attach:

- a `MethodInterceptor` wrapping the invocation, contributed to one of
  five named **strata** (see [Strata](#strata) below),
- a `Guard` gating visibility + invocation (see
  [guards.md](guards.md)),
- or a `ParameterResolver` supplying a value for a specific parameter.

This is the only supported extension point for cross-cutting
behavior on MCP handlers. It replaces the earlier "register any
`MethodInterceptor` bean and it joins every pipeline" shape, which
was a footgun (any bean matching the structural type silently joined
every handler). Customizers are explicit, typed, and see the handler
they're attaching to.

## Four interfaces, one pattern

| Handler kind | Customizer | Config reader |
|---|---|---|
| `@McpTool` | `CallToolHandlerCustomizer` | `CallToolHandlerConfig` |
| `@McpPrompt` | `GetPromptHandlerCustomizer` | `GetPromptHandlerConfig` |
| `@McpResource` | `ReadResourceHandlerCustomizer` | `ReadResourceHandlerConfig` |
| `@McpResourceTemplate` | `ReadResourceTemplateHandlerCustomizer` | `ReadResourceTemplateHandlerConfig` |

All four customizers are `@FunctionalInterface`s with a single
method:

```java
void customize(<XxxHandlerConfig> config);
```

Each `*HandlerConfig` exposes three read-only accessors, one guard
mutator, one resolver mutator, and one interceptor mutator **per stratum**:

```java
// readers
Tool           descriptor();   // or Prompt / Resource / ResourceTemplate
java.lang.reflect.Method  method();
Object         bean();

// per-stratum interceptor mutators (see Strata below)
XxxConfig correlationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig observationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig auditInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig validationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig invocationInterceptor(MethodInterceptor<? super T> interceptor);

// gates + parameter resolution
XxxConfig guard(Guard guard);
XxxConfig resolver(ParameterResolver<? super T> resolver);
```

The type parameter `T` is the argument type for the handler kind:
`JsonNode` for tools, `Map<String, String>` for prompts and
resource templates, `Object` for resources.

Customizer authors pick the stratum that matches the intent of the
interceptor. The builder assembles the chain; ordering stops being a
concern at the call site.

## Quick example — attach a timing interceptor to every tool

```java
@Configuration
class TimingConfig {

    @Bean
    CallToolHandlerCustomizer timingCustomizer(MeterRegistry meters) {
        return config -> {
            String toolName = config.descriptor().name();
            Timer timer = Timer.builder("my.tool.timing")
                .tag("tool", toolName)
                .register(meters);
            config.observationInterceptor(invocation -> timer.record(() -> {
                try {
                    return invocation.proceed();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }));
        };
    }
}
```

The customizer runs **once per tool at startup** — the `Timer`
instance and the captured `toolName` are closed over at attachment
time. Per-invocation cost is just the `timer.record(...)` wrapping.

## Conditional attachment

Customizers aren't obligated to attach anything. Common pattern:
inspect the method for an annotation, attach only if present.

```java
@Bean
CallToolHandlerCustomizer auditOnlyWhenAnnotated() {
    return config -> {
        Audited ann = config.method().getAnnotation(Audited.class);
        if (ann == null) return;
        config.auditInterceptor(new AuditInterceptor(ann.level()));
    };
}
```

This is how `mocapi-spring-security-guards` attaches — it only
installs a `ScopeGuard` when the method carries `@RequiresScope`
(see [guards.md](guards.md)).

## Strata

Customizers don't negotiate ordering — they pick a **stratum** that
describes the intent of what they're contributing, and the handler
builder assembles the chain in a fixed outer-to-inner sequence.

| Stratum | Add method | What it's for |
|---|---|---|
| CORRELATION | `correlationInterceptor(...)` | MDC, request-id propagation. Outermost so every downstream log carries correlation. |
| OBSERVATION | `observationInterceptor(...)` | Traces, metrics. Wraps the rest so denials + validation failures are observed. |
| AUDIT | `auditInterceptor(...)` | Persistent record of every attempt. Inside observation; sees post-guard outcomes. |
| AUTHORIZATION | (no method — use `guard(...)`) | Guards. Wired by the builder into a single evaluation interceptor that short-circuits with `-32003 Forbidden` on denial. |
| VALIDATION | `validationInterceptor(...)` | Semantic validation (Jakarta Bean Validation, cross-field checks). For tools, the compiled input JSON schema check is wired by the builder as the first VALIDATION step — a wire-level schema miss short-circuits before semantic validation runs. |
| INVOCATION | `invocationInterceptor(...)` | Escape hatch that wraps the reflective call itself — retries, timeouts. |

Assembled chain (outer → inner):

```
CORRELATION → OBSERVATION → AUDIT → AUTHORIZATION (guards) →
VALIDATION (schema for tools, then user's validation) → INVOCATION
→ (reflective call)
```

Denials bubble up through audit, observation, and MDC so every
attempt — allowed or blocked — is correlated and observable.
Validation only runs for callers who made it past the guard gate.

Within a single stratum, multiple customizers contribute in the order
Spring hands them to the builder — which is customizer bean
`@Order` when running under a Spring application context. That's
usually irrelevant: you rarely have two customizers competing for
the same stratum on the same handler.

### Picking a stratum

- Setting up context that every later log line should carry →
  **CORRELATION**.
- Emitting traces or metrics → **OBSERVATION**.
- Recording who called what and what happened → **AUDIT**.
- Gating access → **`guard(...)`**, not an interceptor. See
  [guards.md](guards.md).
- Rejecting malformed or invalid inputs → **VALIDATION**.
- Wrapping the actual method call (retry, timeout, fallback) →
  **INVOCATION**.

If none fit, you're usually better off extending one of the existing
ones than stretching a stratum to cover a new concept. File an issue
if you hit a genuine gap.

## Writing to `method.getAnnotation`

The `config.method()` is the user's declared method (on the concrete
bean). Spring AOP-style proxies don't affect what you read from
annotations — customizers run against the target-class method.

## A note on thread-safety

Customizers run once per handler at application startup on a single
thread. The interceptors / guards / resolvers they attach run
per-invocation on whatever thread the handler is dispatched on
(typically a virtual thread spawned by
`StreamableHttpController.handleCall`, or the stdio loop thread for
the stdio transport).

**Your attached objects must be thread-safe.** The Methodical
interceptor chain is shared across every concurrent invocation of
the same handler, so a stateful interceptor instance sees concurrent
`intercept(...)` calls.

Common gotcha: libraries that look stateless but have internal
mutable state (ahem, json-sKema's `Validator`). When in doubt,
allocate fresh per-call rather than caching at construction.

## Parameter resolvers specifically

See [parameter-resolvers.md](parameter-resolvers.md) for the
dedicated `config.resolver(...)` story — how mocapi layers built-in
resolvers with user-added ones, and how to write a
`ParameterResolver` that extracts a handler parameter from
request-scoped state (e.g., `@CurrentTenant String tenant`).

## Built-in customizers shipped by mocapi modules

Reference for what's already at work before you add your own:

| Module | Attaches | Stratum |
|---|---|---|
| `mocapi-server` | `InputSchemaValidatingInterceptor` (tools), `GuardEvaluationInterceptor` (when guards present) | built-in: VALIDATION (schema), AUTHORIZATION (guards) |
| `mocapi-logging` | `McpMdcInterceptor` per handler kind | CORRELATION |
| `mocapi-o11y` | `McpHandlerObservationInterceptor` per handler kind (plus `McpObservationFilter` enriching the outer `jsonrpc.server` observation — see below) | OBSERVATION |
| `mocapi-audit` | `AuditLoggingInterceptor` per handler kind | AUDIT |
| `mocapi-jakarta-validation` | Methodical's `JakartaValidationInterceptor` per handler kind | VALIDATION |
| `mocapi-spring-security-guards` | `ScopeGuard` / `RoleGuard` when `@RequiresScope` / `@RequiresRole` present | AUTHORIZATION (via `guard(...)`) |
| `mocapi-oauth2` | Two `SecurityFilterChain` beans (metadata chain + MCP chain), an `McpTokenStrategy` (`JwtMcpTokenStrategy` or `OpaqueTokenMcpTokenStrategy` depending on which Spring Boot wired), and five `McpMetadataCustomizer` beans populating the RFC 9728 metadata document (`ResourceMetadataCustomizer`, `AuthorizationServersMetadataCustomizer`, `ScopesSupportedMetadataCustomizer`, `ResourceNameMetadataCustomizer`, `ClaimsMetadataCustomizer`). See [authorization.md](authorization.md). | n/a (HTTP / metadata layer) |

Every one of these is a few lines of `@Bean` declaration —
mocapi-autoconfigure. Crack any module's source to see the
pattern in isolation; it's the same shape everywhere.

## Customizers beyond the handler level

The `*HandlerCustomizer` SPIs above attach to individual MCP handlers. A
few other customizer SPIs live at coarser layers — JSON-RPC method
dispatch, and the HTTP security filter chains for the OAuth2 module.
They're listed here so all the extension points are discoverable from one
page; each has deeper coverage in its own module doc.

| SPI | Where it attaches | Use it to | Doc |
|---|---|---|---|
| `JsonRpcMethodHandlerCustomizer` | Every `@JsonRpcMethod` on the dispatcher (ripcurl) | Wrap the outer JSON-RPC dispatch with interceptors. Used by `mocapi-o11y` to emit the `jsonrpc.server` observation. | [observability.md](observability.md) |
| `McpFilterChainCustomizer` | The `SecurityFilterChain` serving `/mcp/**` | Require specific scopes on top of authentication, add CORS to the MCP endpoint, install a rate-limit filter. | [authorization.md](authorization.md) |
| `McpMetadataFilterChainCustomizer` | The `SecurityFilterChain` serving `/.well-known/oauth-protected-resource` | HTTP-layer tweaks only — CORS for browser clients, security headers, rate limiting. The auth policy of this chain is frozen at `permitAll` by RFC 9728. | [authorization.md](authorization.md) |
| `McpMetadataCustomizer` | The RFC 9728 protected-resource metadata document | Set or override any field on the metadata JSON — `resource`, `authorization_servers`, `scopes_supported`, or custom extension claims. | [authorization.md](authorization.md#customizing-the-metadata-document) |
| `McpTokenStrategy` | The `oauth2ResourceServer` DSL on both filter chains | Swap the bearer-token validation mode. `JwtMcpTokenStrategy` and `OpaqueTokenMcpTokenStrategy` are the built-ins; register a `@Primary McpTokenStrategy` bean to replace them. | [authorization.md](authorization.md#swapping-the-token-strategy) |

## Non-goals

- **Custom strata.** The six-stratum sequence is fixed. If you have
  a genuine cross-cutting concern that doesn't map to one of them,
  file an issue — we'd rather add a stratum than let the axis
  proliferate.
- **Dynamic add/remove at runtime.** Customizers run once at
  startup; the chain is frozen. For runtime gating use a `Guard`
  or an `MethodInterceptor` that reads a toggle.
- **Replacing mocapi's structural resolvers.** You can add
  resolvers (tries user's first) but can't remove mocapi's built-in
  ones (schema validation for tools, argument binding for prompts).
  See [parameter-resolvers.md](parameter-resolvers.md) for the
  resolution order.
