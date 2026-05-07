# ADR-0011 — Customizer SPI and stratified interceptor chain

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

Cross-cutting behavior on MCP handlers — MDC correlation, metrics,
distributed tracing, audit logging, authorization, semantic validation,
retries — needs an extension point. The earliest mocapi shape autowired any
`MethodInterceptor<? super T>` bean from the application context into every
handler pipeline of that kind. That worked until it didn't:

- **It was a footgun.** Any bean structurally matching
  `MethodInterceptor<? super JsonNode>` silently joined every tool's
  pipeline. A user adding an unrelated interceptor for some other library
  would discover their interceptor was wrapping every MCP tool call.
- **Ripcurl 2.7.0 collision.** Ripcurl's
  `DefaultAnnotationJsonRpcMethodProviderFactory` also scans for
  `MethodInterceptor<? super JsonNode>` beans at the JSON-RPC dispatch
  layer. A bean matching the bound got installed twice — once at JSON-RPC,
  once at the mocapi handler layer — with confusing behavior at the outer
  layer.
- **No per-handler context.** Observability needs to bake the handler
  kind and name into the interceptor at construction time so the hot
  path doesn't re-introspect the method on every call. A flat
  bean-level list can't see *which* handler it's about to wrap.
- **No conditional attachment.** Authorization wants to attach a guard
  only when an annotation is present on the method. A flat list applies
  unconditionally.
- **Ordering by interceptor was negotiated implicitly.** Two
  interceptors trying to share the chain had no way to declare "I go
  outside MDC" or "I go inside validation."

A typed, per-handler extension point with named ordering layers solves all
of these.

## Decision

Per-handler customizer beans are the single supported extension point for
cross-cutting handler behavior. The blind bean-level interceptor autowiring
is removed.

**Four customizer interfaces, one per handler kind.** Each is a
`@FunctionalInterface` taking a `*HandlerConfig`:

| Handler kind | Customizer | Config |
|---|---|---|
| `@McpTool` | `CallToolHandlerCustomizer` | `CallToolHandlerConfig` |
| `@McpPrompt` | `GetPromptHandlerCustomizer` | `GetPromptHandlerConfig` |
| `@McpResource` | `ReadResourceHandlerCustomizer` | `ReadResourceHandlerConfig` |
| `@McpResourceTemplate` | `ReadResourceTemplateHandlerCustomizer` | `ReadResourceTemplateHandlerConfig` |

Each `*HandlerConfig` exposes read-only access to the handler's
`Descriptor` record (see [ADR-0014](0014-mocapi-model-from-schema-ts.md)),
the target `Method`, and the target bean, plus mutators for attaching
behavior:

```java
// readers
Tool descriptor();
java.lang.reflect.Method method();
Object bean();

// behavior attachment
XxxConfig correlationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig observationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig auditInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig validationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig invocationInterceptor(MethodInterceptor<? super T> interceptor);
XxxConfig guard(Guard guard);
XxxConfig resolver(ParameterResolver<? super T> resolver);
```

**Six fixed strata, outer-to-inner:**

```
CORRELATION → OBSERVATION → AUDIT → AUTHORIZATION → VALIDATION → INVOCATION → (reflective call)
```

| Stratum | Purpose |
|---|---|
| CORRELATION | MDC, request-id propagation |
| OBSERVATION | Traces, metrics |
| AUDIT | Persistent record of every attempt |
| AUTHORIZATION | Guards (wired by the builder; see [ADR-0012](0012-guard-spi.md)) |
| VALIDATION | Wire-level schema check (built-in for tools), then user validation |
| INVOCATION | Retries, timeouts, fallback |

Customizer authors pick the stratum that matches intent. The handler builder
assembles the chain in the fixed sequence — ordering stops being a concern
at the call site. Within a single stratum, multiple customizers contribute
in Spring `@Order` sequence.

**Every handler exposes a nested `Descriptor` record** as the single source
of truth for what `tools/list`, `prompts/list`, `resources/list`, and
`resources/templates/list` return. The same record is what the customizer
sees via `config.descriptor()` and what the actuator endpoint
([ADR-0017](0017-observability-stack.md)) inventories.

## Consequences

**What this buys us.** Cross-cutting concerns (MDC, observability, audit,
authorization, validation) ship as small autoconfig beans contributing
exactly one customizer each, with no risk of accidental cross-wiring.
Customizers see the handler they're attaching to, so per-handler config
(metric tags, authorization annotations, guard attachment) is natural.
The strata sequence is documented and fixed — readers don't have to
reverse-engineer ordering from `@Order` annotations scattered across
modules. The handler builder becomes the only place that knows the chain
shape.

**Costs.** The strata sequence is closed: there is no "custom stratum"
escape hatch. New cross-cutting concepts that don't fit one of the six are
expected to extend an existing stratum or open an issue. Multi-handler-kind
behavior requires four customizer beans (one per kind), even when the
implementation is shared — a small amount of `@Bean` boilerplate per
feature module.

**Non-goals.** Dynamic add/remove of interceptors at runtime is not
supported; the chain is frozen after `@PostConstruct`. For runtime gating,
use a `Guard` ([ADR-0012](0012-guard-spi.md)) or an interceptor that reads a
toggle.

**Code anchors:** `mocapi-server/.../tools/CallToolHandlerCustomizer.java`, `CallToolHandlers.java` (chain assembly). See also
[customizers.md](../guides/customizers.md) for the user-facing guide.
