# Move call-time guard evaluation into the MethodInvoker chain

## What to build

Refactor call-time guard evaluation from the service layer
(where spec 187 landed it) into a single `MethodInterceptor`
placed in each handler's MethodInvoker chain. Services stop
special-casing guards for calls; they just invoke the handler
as they do any other method, and the interceptor aborts the
invocation on `Deny`.

List-time guard evaluation stays where it is — there's no
MethodInvocation context available when filtering descriptor
lists for `tools/list` / `prompts/list` / etc.

### Why

Today, after spec 187:

```java
// McpToolsService.call — abbreviated
CallToolHandler handler = lookup(name);
GuardDecision decision = Guards.evaluate(handler.guards());
return switch (decision) {
    case Allow a  -> invokeAndBuildResult(handler, arguments);
    case Deny(var reason) -> throw new JsonRpcException(FORBIDDEN, "Forbidden: " + reason);
};
```

Guards are the only cross-cutting concern that gets special-cased
in the service layer. Every other attachment — MDC, o11y,
JakartaValidation, schema-validation, user interceptors — runs
inside the MethodInvoker chain. The inconsistency is:

1. **Cognitive cost.** Readers of `McpToolsService` (and its
   prompt / resource / resource-template siblings) have to
   remember that guards evaluate here but nothing else does.
2. **Ordering is inexpressible.** Interceptors that run around
   the invocation can be ordered relative to each other. Guards
   outside that chain can't participate in ordering — they're
   always first, regardless of whether that's the right shape.
   For observability specifically: denied calls today aren't
   observed at all (o11y interceptor never runs), so ops teams
   don't see denial counts on the handler's meter. If guards
   were inside the MethodInvoker chain *after* o11y, denials
   would surface as errored observations — meaningful
   operational data.
3. **Test surface.** Guards-in-service means every service test
   has its own guard-evaluation mock path. Guards-as-interceptor
   means one unit-tested class and the services forget about
   guards entirely.

Not a correctness issue — 187 works. This is a "last place the
handler invocation model isn't uniform" cleanup.

### Target shape

New class in `mocapi-server/src/main/java/com/callibrity/mocapi/server/guards/`:

```java
public final class GuardEvaluationInterceptor implements MethodInterceptor<Object> {

    private final List<Guard> guards;

    public GuardEvaluationInterceptor(List<Guard> guards) {
        this.guards = List.copyOf(guards);
    }

    @Override
    public Object intercept(MethodInvocation<?> invocation) throws Throwable {
        GuardDecision decision = Guards.evaluate(guards);
        if (decision instanceof GuardDecision.Deny(var reason)) {
            throw new JsonRpcException(
                    JsonRpcErrorCodes.FORBIDDEN,
                    "Forbidden: " + reason);
        }
        return invocation.proceed();
    }
}
```

Built once per handler after customizers have run, added to the
MethodInvoker chain in a specific position (see below), no
allocation per invocation.

### Chain ordering

Each `*Handlers.build(...)` helper assembles the final
interceptor list in this order:

1. **Customizer-added interceptors**, in iteration order
   (MDC, o11y, user interceptors — same as today).
2. **`GuardEvaluationInterceptor`** — if the handler has any
   guards attached. Inserted between customizer interceptors
   and the kind-specific trailing slot.
3. **Kind-specific trailing interceptors** — e.g.
   `InputSchemaValidatingInterceptor` for tools; nothing for
   prompts / resources / resource-templates today.

Why guards go *after* customizer interceptors and *before*
schema validation:

- **After MDC / o11y:** MDC is set before guard evaluation, so
  any log lines emitted during the guard's own logic carry
  handler-kind / handler-name context. O11y has started its
  observation; a denial throws and the observation closes with
  ERROR status, giving meters and traces a record of denied
  calls — enough for ops to dashboard denial rates by handler.
- **Before schema validation:** a denied call shouldn't waste
  cycles validating arguments that won't be used. Schema errors
  on a denied call would also be confusing in logs
  ("validation failed on a call that was never going to run").

Ordering is fixed by the builder, not customizer-controlled.
Guards aren't an attachment users manage — they're a protocol-
level gate whose position in the chain is mocapi's concern.

### Services — what changes

`McpToolsService.call`, `McpPromptsService.get`,
`McpResourcesService.read`, `McpResourceTemplatesService.read`:
drop the explicit `Guards.evaluate(...)` block; just look up
the handler and invoke it. The interceptor handles denial.

`JsonRpcException` thrown from the interceptor propagates up
the same way ripcurl / the transport expects any other JSON-RPC
exception — status code mapping already treats `-32003` as a
Forbidden response.

**List-time filtering is unchanged.** `PaginatedService`'s
descriptor filtering walks `handler.guards()` directly — no
MethodInvocation available there, so an interceptor doesn't
help. Keep that path as spec 187 left it.

### Handler API

`CallToolHandler.guards()` / `GetPromptHandler.guards()` /
`ReadResourceHandler.guards()` /
`ReadResourceTemplateHandler.guards()` stay public — list-time
filtering reads them, and exposing them is harmless.

### Non-goals

- **Changing the Guard SPI.** `Guard`, `GuardDecision`, `Allow`,
  `Deny` unchanged.
- **Changing list-time filtering.** `PaginatedService` still
  walks `handler.guards()` per request.
- **Making guards user-orderable within the interceptor chain.**
  Position is mocapi-owned: between customizer-added
  interceptors and kind-specific trailing. If someone
  genuinely wants guard evaluation *after* schema validation
  (unlikely — you'd be validating args for a call you won't
  run), that's a separate spec.
- **Exposing the `GuardEvaluationInterceptor` for user reuse.**
  It's an internal implementation detail of how guards hook
  into the handler chain. Users attach via `config.guard(...)`,
  not by constructing this class themselves.

## Acceptance criteria

- [ ] New class `GuardEvaluationInterceptor` in
      `com.callibrity.mocapi.server.guards` with a single public
      constructor taking `List<Guard>` and an `intercept(...)`
      method that evaluates guards and aborts with
      `JsonRpcException` (code `FORBIDDEN`) on `Deny`.
- [ ] Each `*Handlers.build(...)` helper assembles its final
      interceptor list in the documented order: customizer-added
      → `GuardEvaluationInterceptor` (when guards present) →
      kind-specific trailing.
- [ ] When no guards are attached, no `GuardEvaluationInterceptor`
      is added (single-guard-instance case stays zero overhead;
      no-guard case stays zero overhead).
- [ ] `McpToolsService.call` no longer invokes
      `Guards.evaluate(...)`; it looks up the handler and
      invokes. Same for the three sibling services.
- [ ] `PaginatedService` list-time filtering unchanged —
      `handler.guards()` still consulted per request for
      visibility filtering.
- [ ] Unit test: `GuardEvaluationInterceptor.intercept` with a
      single `Allow` guard → proceeds; single `Deny` → throws
      `JsonRpcException` with code -32003 and the reason in the
      message; empty list → proceeds.
- [ ] Unit test: chain ordering verified — guards run after
      MDC / o11y interceptors, before schema validation, for
      each of the four handler kinds.
- [ ] Integration test (all four handler kinds): register a
      denying guard and an o11y customizer; invoke; assert the
      observation was recorded (as an errored observation) and
      the response was a -32003 error.
- [ ] Integration test: list-time filtering still drops denied
      handlers' descriptors (spec 187's behavior preserved).
- [ ] Services' existing `Guards.evaluate` / service-layer
      tests either migrate to test the interceptor directly or
      get retired as redundant.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Implementation notes

- `JsonRpcException` is the same type spec 187's service-layer
  path throws; the transport-level mapping stays the same.
- `MethodInterceptor#intercept` can throw; the MethodInvoker
  chain propagates whatever exception bubbles up. No new
  infrastructure needed.
- Consider a tiny helper on `*HandlerConfig` or the builder
  that constructs the `GuardEvaluationInterceptor` once per
  handler from the collected guards, rather than inlining the
  `if (!guards.isEmpty()) interceptors.add(new GEI(guards))`
  block in four builders. Optional micro-refactor.
- This spec supersedes the "guards evaluate in the service
  layer" section of spec 187. Update
  `docs/guards.md` (once written) to reflect the new shape.
