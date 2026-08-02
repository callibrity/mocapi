# Extension SPI

How mocapi's extension model is structured internally — the
customizer interfaces, the six interceptor strata, the parameter
resolver model, and the Guard SPI's hook into the same machinery.

For decisions, see:

- [ADR-0011](../adr/0011-customizer-spi-and-strata.md) — customizer SPI, strata, descriptor pattern
- [ADR-0012](../adr/0012-guard-spi.md) — Guard SPI, visibility ≡ invocation

For usage-side documentation, see the
[Customizers guide](../guides/customizers.md), the
[Custom Parameter Resolvers guide](../guides/parameter-resolvers.md),
and the [Guards guide](../guides/guards.md).

## One customizer per handler kind

Mocapi exposes four `*HandlerCustomizer` SPIs, one per handler kind.
This is the *only* extension point for cross-cutting behavior on MCP
handlers.

| Handler kind | Customizer | Config reader |
|---|---|---|
| `@McpTool` | `CallToolHandlerCustomizer` | `CallToolHandlerConfig` |
| `@McpPrompt` | `GetPromptHandlerCustomizer` | `GetPromptHandlerConfig` |
| `@McpResource` | `ReadResourceHandlerCustomizer` | `ReadResourceHandlerConfig` |
| `@McpResourceTemplate` | `ReadResourceTemplateHandlerCustomizer` | `ReadResourceTemplateHandlerConfig` |

All four customizers are `@FunctionalInterface`s with a single
method `void customize(<XxxHandlerConfig> config)`. Each
`*HandlerConfig` exposes:

- read-only accessors: `descriptor()`, `method()`, `bean()`
- one interceptor mutator **per stratum**
- `guard(Guard)` for authorization
- `resolver(ParameterResolver)` for parameter resolution

This shape replaces an earlier design where any `MethodInterceptor`
bean structurally matched the handler signature would silently join
every pipeline. Customizers are explicit, typed, and see the handler
they're attaching to — preventing the "blind autowiring" footgun.

## The descriptor pattern

Every handler kind exposes a nested `Descriptor` record (e.g.,
`Tool`, `Prompt`, `Resource`, `ResourceTemplate`) carrying its
`name`, `title`, `description`, and any schemas. The descriptor is
the single source of truth for listing operations (`tools/list`,
`prompts/list`, etc.) and is what `config.descriptor()` returns to
customizers. Listing services stream the registered handlers,
filter by guard evaluation (denials hidden), map to descriptors, and
paginate.

## The six strata

Customizers don't negotiate ordering. They contribute interceptors to
a named **stratum**, and the handler builder assembles the chain in a
fixed outer-to-inner sequence:

```
CORRELATION → OBSERVATION → AUDIT → AUTHORIZATION (guards) →
VALIDATION (schema for tools, then user's validation) → INVOCATION
→ (reflective call)
```

| Stratum | Add method | Intent |
|---|---|---|
| CORRELATION | `correlationInterceptor(...)` | MDC, request-id propagation. Outermost so every downstream log carries correlation. |
| OBSERVATION | `observationInterceptor(...)` | Traces, metrics. Wraps the rest so denials + validation failures are observed. |
| AUDIT | `auditInterceptor(...)` | Persistent record of every attempt. Inside observation; sees post-guard outcomes. |
| AUTHORIZATION | (no method — use `guard(...)`) | Guards. Wired by the builder into a single evaluation interceptor that short-circuits with `-32010 Forbidden` on denial. |
| VALIDATION | `validationInterceptor(...)` | Semantic validation (Jakarta Bean Validation, cross-field checks). For tools, the compiled input JSON schema check is wired by the builder as the first VALIDATION step — a wire-level schema miss short-circuits before semantic validation runs. |
| INVOCATION | `invocationInterceptor(...)` | Escape hatch that wraps the reflective call itself — retries, timeouts. |

Denials bubble up through audit, observation, and MDC so every
attempt — allowed or blocked — is correlated and observable.
Validation only runs for callers who made it past the guard gate.

The six-stratum sequence is fixed. Adding a stratum is a deliberate
SPI change, not a per-customizer extension point.

## Guard SPI

Guards are the AUTHORIZATION stratum, but they are not interceptors —
they're a separate interface so the same evaluation can be applied at
*list* time as well as *call* time:

```java
@FunctionalInterface
public interface Guard {
    GuardDecision check();
}

public sealed interface GuardDecision {
    record Allow() implements GuardDecision {}
    record Deny(String reason) implements GuardDecision {}
}
```

A `Guards.evaluate(List<Guard>)` helper walks the list with AND
semantics and short-circuits on the first `Deny`. Empty list →
`Allow`.

**At call time**, the builder wires guards into a single
`GuardEvaluationInterceptor` in the AUTHORIZATION stratum. A denial
throws `JsonRpcException` with code `-32010` (ADR-0023) and message
`"Forbidden: <reason>"`. Tools do *not* return
`CallToolResult.isError=true` for guard denials — that would invite
an LLM to "self-correct" on an auth failure.

**At list time**, the same guard list is evaluated by the listing
service, which streams handlers and filters out denied ones. The
deny reason is *not* surfaced at list time (information leak).

A denied call never reaches its interceptors at all — the
authorization stratum runs before validation and before the reflective
call.

## Parameter resolvers

The `config.resolver(ParameterResolver)` mutator adds a user resolver
to the front of the resolver list for that handler. Mocapi's
built-in resolvers (schema-driven for tools, argument-binding for
prompts/resource templates, ScopedValue resolvers for `McpExchange` /
`McpTransport` / `McpToolContext`) can't be removed. Users add new
ones (e.g., `@CurrentTenant String tenant`).

The resolver list is consulted for every parameter slot until one
resolver claims it. User resolvers run before built-ins, so a user
resolver can override a default binding for a specific annotation.

## Customizers beyond the handler level

Several customizer SPIs live at coarser layers — `tools/call` dispatch
interception, routing-header validation, JSON-RPC method dispatch, and
(in `mocapi-oauth2`) the HTTP security filter chains. They use the same
"see-and-attach at startup" pattern but operate on different units:

| SPI | Where it attaches | Module |
|---|---|---|
| `ToolCallDispatchCustomizer` | `McpToolsService.callTool`, after handler lookup, before the default MRTR invocation path. `mocapi-tasks` uses it to reroute `@McpTask` calls (ADR-0038). | `mocapi-server` |
| `McpRoutedParamContributor` | The Streamable HTTP transport's `Mcp-Name` routing-header validation table (`-32020 HeaderMismatch`). `mocapi-tasks` contributes `tasks/get\|update\|cancel` → `params.taskId` (ADR-0038). | `mocapi-server` |
| `JsonRpcMethodHandlerCustomizer` | Every `@JsonRpcMethod` on the dispatcher (ripcurl). Used by `mocapi-o11y` to attach the semconv `mcp.server.operation` observation (ADR-0030). | `mocapi-o11y` |
| `McpFilterChainCustomizer` | The `SecurityFilterChain` serving `/mcp/**`. | `mocapi-oauth2` |
| `McpMetadataFilterChainCustomizer` | The `SecurityFilterChain` serving `/.well-known/oauth-protected-resource`. | `mocapi-oauth2` |
| `McpMetadataCustomizer` | The RFC 9728 protected-resource metadata document. | `mocapi-oauth2` |
| `McpTokenStrategy` | The `oauth2ResourceServer` DSL on both filter chains. | `mocapi-oauth2` |

These are documented in the
[Authorization model design doc](authorization-model.md) and the
[Authorization guide](../guides/authorization.md).

### Tool-call dispatch interception

```java
@FunctionalInterface
public interface ToolCallDispatchCustomizer {
  Optional<Object> dispatch(CallToolHandler handler, CallToolRequestParams params);
}
```

Registered customizers are consulted in bean order after handler lookup;
the first to return a non-empty `Optional` short-circuits the request,
and its value becomes the `tools/call` response as-is (the spec's
response union — core neither inspects nor interprets the claimed
result). An empty customizer list, or one where every customizer
declines, falls through to the default synchronous MRTR path
byte-for-byte. This is the seam [MCP Tasks](tasks.md) uses to turn an
`@McpTask` call into a `CreateTaskResult` instead of running the handler
inline.

Detached (off-dispatch-thread) re-invocation of a registered tool is a
separate, lower-level seam, `ToolCallReplayInvoker`:

```java
public interface ToolCallReplayInvoker {
  sealed interface Outcome {
    record Completed(CallToolResult result) implements Outcome {}
    record InputRequired(String key, ElicitRequest request,
                          List<ResponseLedgerEntry> ledger) implements Outcome {}
  }
  Outcome invoke(String toolName, JsonNode arguments, List<ResponseLedgerEntry> ledger,
                 McpProgressSource progressOverride, McpExchange exchange);
}
```

`McpToolsService` implements this directly, reusing the same handler
lookup, context construction, and six-stratum chain the synchronous path
uses — with no wire envelope (no `requestState`, no principal/target
verification; the caller owns the ledger's identity). `mocapi-tasks`'s
`TaskExecutionEngine` calls it to run a task's execution against a
store-loaded ledger. See [elicitation-mrtr.md](elicitation-mrtr.md#the-shared-replay-core-and-its-two-carriers)
and [tasks.md](tasks.md) for the full mechanics.

### Routed-param contribution

```java
@FunctionalInterface
public interface McpRoutedParamContributor {
  Map<String, String> namedParamFields();
}
```

Lives in `mocapi-server` (transport-agnostic) so any module can extend
the `Mcp-Name` validation table transports enforce; transports that
don't validate routing headers (stdio) ignore contributed instances. See
[transports.md](transports.md#routing-header-validation--32020-headermismatch).

## Thread-safety contract

Customizers run once per handler at application startup on a single
thread. The interceptors / guards / resolvers they attach run
per-invocation on whatever thread the handler is dispatched on
(typically a virtual thread). The Methodical interceptor chain is
shared across every concurrent invocation of the same handler, so
attached objects must be thread-safe.

A common gotcha: libraries that look stateless but have internal
mutable state (e.g., json-sKema's `Validator`). When in doubt,
allocate fresh per-call rather than caching at construction.
