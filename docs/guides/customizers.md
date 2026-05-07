# Customizers

> **Where else to look.** SPI internals (interceptor builder, descriptor
> pattern, the six strata, thread-safety contract) live in
> [`../design/extension-spi.md`](../design/extension-spi.md). The
> design rationale is in [ADR-0011](../adr/0011-customizer-spi-and-strata.md).
> This guide is for users who want to attach behavior to handlers.

Mocapi's extension mechanism is a per-handler **customizer SPI**. One
`*HandlerCustomizer` bean per handler kind is invoked at startup for
every discovered handler (tool / prompt / resource / resource template),
and can read the handler's descriptor / method / bean and attach:

- a `MethodInterceptor` wrapping the invocation, contributed to one of
  the named **strata** (CORRELATION, OBSERVATION, AUDIT, VALIDATION,
  INVOCATION),
- a `Guard` gating visibility + invocation (see [guards.md](guards.md)),
- or a `ParameterResolver` supplying a value for a specific parameter
  (see [parameter-resolvers.md](parameter-resolvers.md)).

This is the only supported extension point for cross-cutting behavior
on MCP handlers.

## Four interfaces, one pattern

| Handler kind | Customizer | Config reader |
|---|---|---|
| `@McpTool` | `CallToolHandlerCustomizer` | `CallToolHandlerConfig` |
| `@McpPrompt` | `GetPromptHandlerCustomizer` | `GetPromptHandlerConfig` |
| `@McpResource` | `ReadResourceHandlerCustomizer` | `ReadResourceHandlerConfig` |
| `@McpResourceTemplate` | `ReadResourceTemplateHandlerCustomizer` | `ReadResourceTemplateHandlerConfig` |

All four are `@FunctionalInterface`s. Each `*HandlerConfig` exposes
read-only accessors (`descriptor()`, `method()`, `bean()`), interceptor
mutators per stratum (`correlationInterceptor`, `observationInterceptor`,
`auditInterceptor`, `validationInterceptor`, `invocationInterceptor`),
and `guard(Guard)` / `resolver(ParameterResolver)`.

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

Customizers aren't obligated to attach anything. Common pattern: inspect
the method for an annotation, attach only if present.

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

## Picking a stratum

Customizer authors pick the stratum that matches the *intent* of what
they're contributing; the builder assembles the chain in a fixed
outer-to-inner sequence (see
[`extension-spi.md`](../design/extension-spi.md#the-six-strata) for
the full sequence and semantics).

- Setting up context that every later log line should carry → **CORRELATION**.
- Emitting traces or metrics → **OBSERVATION**.
- Recording who called what and what happened → **AUDIT**.
- Gating access → **`guard(...)`**, not an interceptor.
- Rejecting malformed or invalid inputs → **VALIDATION**.
- Wrapping the actual method call (retry, timeout, fallback) → **INVOCATION**.

If none fit, you're usually better off extending one of the existing
ones than stretching a stratum to cover a new concept. File an issue
if you hit a genuine gap.

## Thread-safety

Customizers run once per handler at startup on a single thread. The
interceptors / guards / resolvers they attach run per-invocation on
whatever thread the handler is dispatched on (typically a virtual
thread). The interceptor chain is shared across every concurrent
invocation of the same handler, so **attached objects must be
thread-safe**.

Common gotcha: libraries that look stateless but have internal
mutable state (ahem, json-sKema's `Validator`). When in doubt,
allocate fresh per-call rather than caching at construction.

## Built-in customizers shipped by mocapi modules

Reference for what's already at work before you add your own:

| Module | Attaches | Stratum |
|---|---|---|
| `mocapi-server` | `InputSchemaValidatingInterceptor` (tools), `GuardEvaluationInterceptor` (when guards present) | built-in: VALIDATION (schema), AUTHORIZATION (guards) |
| `mocapi-logging` | `McpMdcInterceptor` per handler kind | CORRELATION |
| `mocapi-o11y` | `McpHandlerObservationInterceptor` per handler kind (plus `McpObservationFilter` enriching the outer `jsonrpc.server` observation) | OBSERVATION |
| `mocapi-audit` | `AuditLoggingInterceptor` per handler kind | AUDIT |
| `mocapi-jakarta-validation` | Methodical's `JakartaValidationInterceptor` per handler kind | VALIDATION |
| `mocapi-spring-security-guards` | `ScopeGuard` / `RoleGuard` when `@RequiresScope` / `@RequiresRole` present | AUTHORIZATION (via `guard(...)`) |
| `mocapi-oauth2` | Two `SecurityFilterChain` beans, an `McpTokenStrategy`, and five `McpMetadataCustomizer` beans (see [authorization.md](authorization.md)). | n/a (HTTP / metadata layer) |

## Customizers beyond the handler level

A few other customizer SPIs live at coarser layers. They use the same
"see-and-attach at startup" pattern but operate on different units:

| SPI | Where it attaches | Module | Doc |
|---|---|---|---|
| `JsonRpcMethodHandlerCustomizer` | Every `@JsonRpcMethod` on the dispatcher | `mocapi-o11y` | [observability.md](observability.md) |
| `McpFilterChainCustomizer` | The `SecurityFilterChain` serving `/mcp/**` | `mocapi-oauth2` | [authorization.md](authorization.md) |
| `McpMetadataFilterChainCustomizer` | The `SecurityFilterChain` serving `/.well-known/oauth-protected-resource` | `mocapi-oauth2` | [authorization.md](authorization.md) |
| `McpMetadataCustomizer` | The RFC 9728 protected-resource metadata document | `mocapi-oauth2` | [authorization.md](authorization.md#customizing-the-metadata-document) |
| `McpTokenStrategy` | The `oauth2ResourceServer` DSL on both filter chains | `mocapi-oauth2` | [authorization.md](authorization.md#swapping-the-token-strategy) |

## Non-goals

- **Custom strata.** The six-stratum sequence is fixed. File an issue
  if you have a genuine cross-cutting concern that doesn't map to one
  of them.
- **Dynamic add/remove at runtime.** Customizers run once at startup;
  the chain is frozen. For runtime gating use a `Guard` or an
  interceptor that reads a toggle.
- **Replacing mocapi's structural resolvers.** You can add resolvers
  (tries user's first) but can't remove mocapi's built-in ones (schema
  validation for tools, argument binding for prompts). See
  [parameter-resolvers.md](parameter-resolvers.md) for the resolution
  order.
