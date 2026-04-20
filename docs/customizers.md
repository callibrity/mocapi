# Customizers

Mocapi's extension mechanism is a per-handler **customizer SPI**.
One `*HandlerCustomizer` bean per handler kind is invoked at startup
for every discovered handler (tool / prompt / resource / resource
template), and can read the handler's descriptor / method / bean
and attach:

- a `MethodInterceptor` wrapping the invocation,
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

Each `*HandlerConfig` exposes three read-only accessors and three
mutators:

```java
// readers
Tool           descriptor();   // or Prompt / Resource / ResourceTemplate
java.lang.reflect.Method  method();
Object         bean();

// mutators
XxxConfig interceptor(MethodInterceptor<? super T> interceptor);
XxxConfig guard(Guard guard);
XxxConfig resolver(ParameterResolver<? super T> resolver);
```

The type parameter `T` is the argument type for the handler kind:
`JsonNode` for tools, `Map<String, String>` for prompts and
resource templates, `Object` for resources.

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
            config.interceptor(invocation -> timer.record(() -> {
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
        config.interceptor(new AuditInterceptor(ann.level()));
    };
}
```

This is how `mocapi-spring-security-guards` attaches — it only
installs a `ScopeGuard` when the method carries `@RequiresScope`
(see [guards.md](guards.md)).

## Ordering

When multiple customizers attach interceptors to the same handler,
the order in the final chain matches customizer bean ordering
(Spring's `@Order` annotation). Lower `@Order` values run first.

Built-in customizers in mocapi use these `@Order` values so their
interceptors nest in a defensible way around user interceptors:

| `@Order` | Who | Interceptor |
|---|---|---|
| 100 | `mocapi-logging` | `McpMdcInterceptor` |
| 200 | `mocapi-audit` | `AuditLoggingInterceptor` |
| 300 | `mocapi-o11y` | `McpObservationInterceptor` |
| (default) | user code | (yours) |

The outermost interceptors (lowest `@Order`) see every invocation,
including ones later stopped by inner interceptors. That's why MDC
sits outermost — it wants the correlation keys visible during any
log line any subsequent interceptor might emit. Audit is next so it
observes outcome/duration for everything including calls blocked by
guards. Observability is inside that. User interceptors are inside
observability so their work gets traced.

Guards are **not** customizer-ordered — they're evaluated at a fixed
point in the pipeline owned by mocapi (between customizer
interceptors and kind-specific trailing logic like tool input-schema
validation). See [guards.md](guards.md) for details.

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

| Module | Attaches |
|---|---|
| `mocapi-server` | (baseline — the handlers themselves, plus `InputSchemaValidatingInterceptor` for tools and the `GuardEvaluationInterceptor` when guards are attached) |
| `mocapi-logging` | `McpMdcInterceptor` per handler kind |
| `mocapi-o11y` | `McpObservationInterceptor` per handler kind |
| `mocapi-audit` | `AuditLoggingInterceptor` per handler kind |
| `mocapi-jakarta-validation` | Methodical's `JakartaValidationInterceptor` per handler kind |
| `mocapi-spring-security-guards` | `ScopeGuard` / `RoleGuard` when `@RequiresScope` / `@RequiresRole` present |

Every one of these is a few lines of `@Bean` declaration —
mocapi-autoconfigure. Crack any module's source to see the
pattern in isolation; it's the same shape everywhere.

## Non-goals

- **Customizer ordering beyond Spring's `@Order`.** No per-module
  ordering hints, no chain-position-sensitive APIs. If Spring's
  `@Order` isn't enough, file an issue.
- **Dynamic add/remove at runtime.** Customizers run once at
  startup; the chain is frozen. For runtime gating use a `Guard`
  or an `MethodInterceptor` that reads a toggle.
- **Replacing mocapi's structural resolvers.** You can add
  resolvers (tries user's first) but can't remove mocapi's built-in
  ones (schema validation for tools, argument binding for prompts).
  See [parameter-resolvers.md](parameter-resolvers.md) for the
  resolution order.
