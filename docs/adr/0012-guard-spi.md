# ADR-0012 — Guard SPI: unified visibility and call-time authorization

- **Status:** Accepted (amended by [ADR-0023](0023-guard-denial-code-relocation.md))
- **Date:** 2026-04-19

> **Amended 2026-07-28 (ADR-0023):** the Guard SPI, the visibility ≡
> invocation guarantee, and all semantics below stand. Only the wire
> error code for a guard denial changed: the `-32003` referenced in
> "Call time" and the Consequences was relocated to `-32010` by
> [ADR-0023](0023-guard-denial-code-relocation.md) (which reserves
> `-32003` for the spec's missing-capability meaning). See ADR-0023 for
> the current code.

## Context

Once handlers exist, deployments need to gate access to them. The
requirements for that gate are not negotiable:

- A caller who isn't allowed to invoke a handler must not see it in
  `tools/list`, `prompts/list`, `resources/list`, or
  `resources/templates/list`. List leakage of forbidden handler names
  invites enumeration attacks and confuses LLM clients that build prompts
  from the visible inventory.
- The visibility check and the invocation check must be the same check.
  Two parallel implementations drift; one says "you can see it" and the
  other says "you can't call it" and the user is left debugging an
  inconsistency.
- Mocapi has no business owning an auth model. Some deployments use Spring
  Security with OAuth2 scopes, others use a homegrown tenant header,
  others use rate-limit predicates, others use custom RBAC. The framework
  defines the *shape* of an authorization decision; each deployment plugs
  in its own *content*.
- Spring Security's `@PreAuthorize` is not sufficient. It fires only at
  call time (no list filtering), it requires CGLIB proxies (handler beans
  may be final), and a denied call escapes as an `AccessDeniedException`
  that bubbles out as JSON-RPC `-32603 Internal error` — wrong shape on
  the wire.

A handler-level Guard SPI satisfies all of these without coupling the core
to any auth framework.

## Decision

Mocapi defines and owns the Guard SPI. Three types, no framework coupling:

```java
package com.callibrity.mocapi.server.guards;

@FunctionalInterface
public interface Guard {
    GuardDecision check();
}

public sealed interface GuardDecision {
    record Allow() implements GuardDecision {}
    record Deny(String reason) implements GuardDecision {}
}
```

A `Guards.evaluate(List<Guard>)` helper walks the list with **AND
semantics and short-circuits on first `Deny`**. Empty list returns
`Allow`. Cheap checks should be registered first.

**Attachment** is via the customizer SPI
([ADR-0011](0011-customizer-spi-and-strata.md)) — the per-handler config
exposes `guard(Guard)` alongside the interceptor mutators. The customizer
runs once at startup; the `Guard` instance closes over whatever annotation
state it needs, so the per-call check is one method invocation with no
reflection on the hot path.

**Runtime semantics:**

- **List time.** List operations stream registered handlers, filter by
  guard evaluation, and paginate the filtered result. Denied handlers do
  not appear. The `Deny.reason` is never surfaced at list time — that
  would leak which handlers exist to unauthorized callers.
- **Call time.** Service-layer code evaluates the guard list *before*
  the interceptor chain executes. A denied call never reaches its
  interceptors. Denial throws `JsonRpcException` with code `-32003`
  (`JsonRpcErrorCodes.FORBIDDEN`) and message
  `"Forbidden: <reason>"`.
- **Tools never return `CallToolResult.isError=true` for guard denial.**
  An LLM seeing `isError=true` interprets it as a recoverable failure
  and may "self-correct" — that's nonsense for an authorization
  rejection. Guard denial is an infrastructure-level rejection; JSON-RPC
  error is the right shape.
- **`initialize` is exempt.** The initialize request doesn't pass through
  any handler, so guards don't apply to it.

Guards run in the service layer, before the Methodical interceptor chain.
This is deliberately *outside* the AUTHORIZATION stratum's interceptor
slot — guards control *whether* the chain runs at all, not what happens
inside it. The customizer SPI's `guard(...)` mutator and the AUTHORIZATION
stratum's interceptor slot are wired together by the handler builder into a
single guard-evaluation interceptor that short-circuits on deny.

## Consequences

**What this buys us.** Visibility and invocation share one check by
construction; deployments can't accidentally show a handler they then
refuse to call. Auth implementations are pluggable — Spring Security
scopes, tenant headers, rate-limit predicates, custom RBAC all fit the
same SPI ([ADR-0013](0013-oauth2-and-reference-guards.md) ships the
reference implementation). Denials get the right wire-level error code.
The hot path is one Java method call per registered guard.

**Costs.** Mocapi-shipped modules cannot include any auth content beyond
the SPI shape — every concrete authorization scheme lives in a separate
module or in user code. Users wanting OR semantics across multiple
predicates implement that inside a single `Guard` class; the framework
only does AND.

**Non-goals.** A `GuardContext` parameter object passed at check time was
considered and rejected. Handler metadata (method, bean, descriptor,
annotations) is known at customizer attachment time; a `Guard` closes
over what it needs then. Nothing dynamic needs to flow through `check()`
beyond what `ScopedValue.CURRENT` lookups (session, transport, security
context) already provide.

**Code anchors:** `mocapi-server/.../guards/Guard.java`, `GuardDecision.java`, `Guards.java`, `GuardEvaluationInterceptor.java`. See the [Guards guide](../guides/guards.md). Landed in commit `e2e847fa` (2026-04-19).
