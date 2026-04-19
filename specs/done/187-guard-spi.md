# Guard SPI: per-handler visibility + call-time authorization

## What to build

A minimal per-handler guard mechanism. Plugins (Spring Security,
RBAC, tenant checks, per-caller rate predicates, whatever the user
writes) attach guards to individual handlers via the customizer SPI
from spec 180. At runtime, guards govern both visibility (handler
omitted from list operations) and invocation (call rejected with a
JSON-RPC error) with identical semantics — if you can't call it,
you can't see it.

Mocapi owns the SPI shape and the runtime application. Mocapi
does **not** own any auth model. The reference Spring-Security
implementation lands separately in spec 188
(`mocapi-spring-security-guards`).

### SPI shape

Three types total. No context object, no framework coupling in
the core:

```java
// mocapi-server — com.callibrity.mocapi.server.guards

@FunctionalInterface
public interface Guard {
    GuardDecision check();
}

public sealed interface GuardDecision {
    record Allow() implements GuardDecision {}
    record Deny(String reason) implements GuardDecision {}
}
```

That's the whole surface. A Guard implementation pulls whatever
runtime state it needs from its own framework (Spring Security's
`SecurityContextHolder.getContext()`, `McpSession.CURRENT.get()`,
servlet request, plain ScopedValue, whatever). Mocapi's core
neither knows about `Authentication` nor passes anything
implementation-specific.

### Why nothing richer

- **No `GuardContext`:** handler metadata (method, bean,
  descriptor, annotations) is known at customizer attachment
  time. A Guard instance closes over whatever it needs then —
  same pattern as the MDC and o11y interceptors. Nothing dynamic
  to pass at check-time.
- **No `Authentication caller()`:** that's Spring Security's
  type. Putting it in the interface forces every mocapi user to
  transitively know about it; a non-Spring-Security guard can't
  use it anyway. Each guard implementation reaches into its own
  framework for the caller it cares about.
- **No `McpSession session()`:** `McpSession.CURRENT` is a
  ScopedValue bound on the handler VT (spec 182 propagates it).
  Guards that need it call `McpSession.CURRENT.get()` directly.
- **No `purpose()` enum for list vs call:** unified semantics —
  any Deny hides the handler *and* rejects the call. Splitting
  the two would leak information (clients see handlers they
  can't call) and the "list-visible-but-call-denied" case is
  almost always a bug.

### Composition

Multiple guards attach to the same handler freely. Evaluation is
**AND with short-circuit on first `Deny`**:

- All guards pass → `Allow`.
- First `Deny` wins; remaining guards not invoked.
- Denying guard's `reason` string surfaces on call-time failure;
  ignored for list-time (info leak otherwise).

Semantically AND is commutative; ordering only matters for
performance (cheap checks before expensive ones). Spring's
`@Order` on the customizer bean handles this — no new API.

Users who want OR semantics ("scope A OR scope B") implement
that inside a single Guard class. Keeping OR combination
co-located with the auth model avoids accidental loosening
when attaching guards from multiple sources.

### Customizer API addition

Each `*HandlerConfig` gains a `guard(Guard)` mutator:

```java
public interface CallToolHandlerConfig {
    Tool descriptor();
    Method method();
    Object bean();
    CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor);
    CallToolHandlerConfig guard(Guard guard);      // NEW
}
```

Same shape for `GetPromptHandlerConfig`,
`ReadResourceHandlerConfig`,
`ReadResourceTemplateHandlerConfig`.

Attachment example (shape only — real impl ships in spec 188):

```java
@Bean
CallToolHandlerCustomizer scopeGuardCustomizer() {
    return config -> {
        RequiresScope ann = config.method().getAnnotation(RequiresScope.class);
        if (ann != null) {
            config.guard(new ScopeGuard(ann.value()));
        }
    };
}
```

Customizer reads method metadata once at startup; Guard closes
over whatever it extracted; hot path does no reflection.

### Handler storage

Each handler class (`CallToolHandler` / `GetPromptHandler` /
`ReadResourceHandler` / `ReadResourceTemplateHandler`) gains an
immutable `List<Guard>` field, populated by the `*Handlers.build(...)`
helper from the Config's collected guards (exactly as it does for
interceptors today). Handlers expose `guards()` as a read-only list.

### Runtime application — call-time

Each service (`McpToolsService`, `McpPromptsService`,
`McpResourcesService`, `McpResourceTemplatesService`) performs a
guard evaluation step between handler lookup and invocation:

```java
// McpToolsService.call — abbreviated
CallToolHandler handler = lookup(name);
GuardDecision decision = Guards.evaluate(handler.guards());
return switch (decision) {
    case Allow a  -> invokeAndBuildResult(handler, arguments);
    case Deny(var reason) -> throw new JsonRpcException(FORBIDDEN_CODE, "Forbidden: " + reason);
};
```

`Guards.evaluate` is a static helper that walks the list,
short-circuits, returns the decision.

**JSON-RPC error shape for denied calls:**

- Code: **-32003** (in JSON-RPC's implementation-defined
  server-error range; not conflicting with standard codes).
- Message: `"Forbidden: <reason>"` where `<reason>` is the
  first denying guard's `Deny.reason()`.

Applies uniformly across all four handler kinds. Tools do *not*
return `CallToolResult.isError=true` for guard denies — that
would let LLMs attempt "self-correction" on an auth failure,
which is nonsense. Guard failure is an infrastructure-level
rejection, JSON-RPC error is the right shape.

### Runtime application — list-time

`PaginatedService.allDescriptors()` today returns a pre-sorted
static list computed at construction. For guard-aware filtering,
the list step becomes per-request: walk handlers, evaluate
guards, emit descriptors only for handlers where all guards
allow, then paginate the filtered list.

Precompute the sorted `(item, descriptor)` pairs at construction
(same work we do today). At list-time, stream the pairs, filter
by guard evaluation, map to descriptors, return. Pagination
slices the filtered result.

No info leak: denied handlers don't appear. No cursor stability
issue: cursors already encode position in the filtered-and-paginated
list; guards being deterministic (or at least caller-stable)
keeps the cursor meaningful within a session.

### Initialize-time edge

The `initialize` protocol call doesn't pass through any handler
— it's method-level on the server. Guards don't apply.
(For completeness: guards are only attached via the handler-kind
customizers; there's no path for guards to be evaluated outside
handler dispatch.)

### Interaction with existing interceptors

Interceptors and guards run at different times and levels:

- **Guards**: run in the service layer, *before* the handler's
  MethodInvoker chain executes. Failure rejects the call with a
  JSON-RPC error.
- **Interceptors**: run inside the MethodInvoker chain (MDC
  setup, o11y observation, input-schema validation for tools,
  user-attached logic). Don't see denied calls at all — guard
  rejection happens first.

This is the right split: guards decide whether an invocation
should happen; interceptors wrap an invocation that is
happening.

## Acceptance criteria

- [ ] New package `com.callibrity.mocapi.server.guards` in
      `mocapi-server` containing `Guard` (functional interface),
      `GuardDecision` (sealed), `GuardDecision.Allow`,
      `GuardDecision.Deny`.
- [ ] New static helper class
      `com.callibrity.mocapi.server.guards.Guards` with an
      `evaluate(List<Guard>)` method implementing AND /
      short-circuit-on-deny.
- [ ] Each `*HandlerConfig` interface gains a
      `guard(Guard) -> *HandlerConfig` method.
- [ ] Each `*Handlers.build(...)` helper collects guards from the
      Config after customizers run and passes them into the
      handler constructor.
- [ ] Each handler class gains an immutable `List<Guard>` field
      plus a `guards()` accessor.
- [ ] Each service (`McpToolsService` / `McpPromptsService` /
      `McpResourcesService` / `McpResourceTemplatesService`)
      evaluates guards between lookup and invocation; on `Deny`,
      throws a `JsonRpcException` with code `-32003` and message
      `"Forbidden: <reason>"`.
- [ ] `PaginatedService` list operations filter descriptors
      per-request by evaluating guards; denied descriptors don't
      appear in `*/list` responses; pagination cursors operate
      over the filtered list.
- [ ] Error code constant lives somewhere sensible (e.g.,
      `com.callibrity.mocapi.server.JsonRpcErrorCodes.FORBIDDEN`)
      so it's not a magic number scattered across services.
- [ ] Unit tests:
    - `Guards.evaluate`: empty list → Allow; all Allow → Allow;
      first Deny short-circuits; second Deny after Allow returns
      that Deny; guards after a Deny are never invoked.
    - Per handler kind: register a guard that denies, confirm
      call rejected with code -32003 and reason in the message.
    - Per handler kind: register a guard that denies, call the
      list operation, confirm the descriptor is absent.
    - With mixed guards (some allow, one denies): confirm hidden
      in list, rejected on call.
- [ ] Integration test against the Streamable HTTP transport:
      register a guard that inspects `McpSession.CURRENT` or a
      test ScopedValue, exercise from a POST request, confirm
      both list and call behavior.
- [ ] `docs/` — new `docs/guards.md` explaining the SPI,
      attachment pattern, composition semantics, and pointing to
      spec 188 (`mocapi-spring-security-guards`) as the first
      real implementation.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Added`: entry for
      the Guard SPI.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Non-goals

- **No reference Guard implementation in this spec.** Spec 188
  ships `mocapi-spring-security-guards`; other packages (tenant
  checks, rate limits) are user / third-party concerns.
- **No list-visible-but-call-denied mode.** Deliberately omitted.
- **No guard-evaluation metrics / observations.** If useful
  later, add a MethodInterceptor that wraps guard evaluation —
  orthogonal to this spec.
- **No `Authentication` or any Spring Security type in the SPI.**
  Framework-specific reads live inside specific Guard
  implementations.
- **No session-attribute plumbing for guards.** Guards that want
  session state call `McpSession.CURRENT.get()` directly.
- **No initialize-time guards.** The initialize protocol call
  doesn't pass through handlers; there's no attachment point.

## Implementation notes

- `PaginatedService` currently has `allItems` as a map and
  `sortedDescriptors` as a precomputed list. Rework: precompute
  a sorted `List<ItemEntry<T, D>>` (or similar record pairing
  item + descriptor); `allDescriptors()` streams, filters via
  `Guards.evaluate(item.guards())`, maps to D. Preserves current
  sort-once behavior while making filter per-request.
- `lookup(name)` still throws when a handler is not found; guard
  denies produce a different error code (`-32003` vs standard
  "not found" `-32601`). Guard evaluation happens after lookup
  succeeds.
- Guard evaluation is called in both the service layer
  (`call`, `get`, `read`) and the list layer (`list` / pagination
  input path). One helper method, two call sites.
- `Guards.evaluate` is static, pure, and trivial to test.
  Keep it that way.
- The `@ConditionalOnClass` trigger for `mocapi-spring-security-guards`
  (spec 188) is the annotation / guard class that module exports.
  No changes to `mocapi-autoconfigure` required from this spec —
  guards attach via Spring `@Bean` methods returning customizers,
  same wiring pattern as MDC and o11y.
