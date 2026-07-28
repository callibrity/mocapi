# ADR-0006 — One virtual thread per JSON-RPC call, with `ContextSnapshot` propagation

- **Status:** Accepted
- **Date:** 2026-04-12

> **Amended 2026-07-28 (ADR-0020):** the core decision — one virtual
> thread per JSON-RPC call with `ContextSnapshot` propagation — is intact
> and still current. Two references below are stale: elicitation no
> longer parks a thread on a Substrate Mailbox (Substrate is gone;
> elicitation is now MRTR replay, [ADR-0021](0021-mrtr-elicitation-replay.md),
> and sampling was removed, [ADR-0022](0022-2026-07-28-features-not-implemented.md)),
> and `McpSession.CURRENT` no longer exists (sessions were removed,
> [ADR-0020](0020-stateless-request-model.md); the per-request handle is
> now the immutable `McpExchange`). Corrections are marked inline.

## Context

MCP handlers may block. A tool that does I/O against a database, a downstream API, or a slow
filesystem blocks its execution thread; MRTR elicitation
([ADR-0021](0021-mrtr-elicitation-replay.md)) re-executes the handler on
each round trip, and each re-execution can block on that same I/O.
Pinning a Tomcat worker to such a handler limits
concurrency to the worker pool size — fine for a handful of clients,
catastrophic for a multi-tenant deployment.

The MCP server also wants to commit its HTTP response shape lazily — the
JSON-vs-SSE decision lives in the transport
([ADR-0004](0004-lazy-json-vs-sse-state-machine.md)) and is driven by the
handler's first `send`. Spring MVC supports that via async return types
(`CompletableFuture<ResponseEntity>`), which require the handler to run
off the request thread.

Java 21 virtual threads solve both problems: cheap, scalable, parking-friendly.
But virtual threads created via `Thread.ofVirtual().start(...)` do
**not** inherit `ThreadLocal` values from their parent. Every `ThreadLocal`-backed
context living on the request thread vanishes at the spawn boundary:

- Spring Security's `SecurityContextHolder` — guards (current and
  planned) read it to authorize handlers.
- Micrometer Observation scope — `mcp.tool` / `mcp.prompt` spans become
  orphans without a parent HTTP span.
- SLF4J MDC — request id, trace id, custom keys set by upstream filters
  disappear before any per-handler MDC customizer can re-stamp them.

## Decision

Every JSON-RPC call routed through the Streamable HTTP transport runs on
a fresh virtual thread spawned per call. Before spawning, the controller
captures an `io.micrometer.context.ContextSnapshot` on the request thread
and wraps the handler `Runnable` via `snapshot.wrap(...)`:

```java
ContextSnapshot snapshot = contextSnapshotFactory.captureAll();
Thread.ofVirtual().start(snapshot.wrap(() -> {
    try {
        server.handleCall(context, call, transport);
    } catch (Exception e) {
        transport.response().completeExceptionally(e);
    }
}));
```

**Rules:**

1. Every `JsonRpcCall` handler runs on a freshly spawned virtual thread.
   Notifications and client responses (which return 202 immediately) do
   not need a VT spawn.
2. `ContextSnapshotFactory` is exposed as a `@Bean` under
   `@ConditionalOnMissingBean` in
   `MocapiStreamableHttpAutoConfiguration`. The default factory
   discovers `ThreadLocalAccessor`s via `ServiceLoader`. Users can
   register custom accessors via `ContextRegistry`.
3. `snapshot.wrap(Runnable)`:
   - On run, restores every captured `ThreadLocal` on the handler VT.
   - Executes the original runnable.
   - On exit, clears the restored `ThreadLocal`s.
4. The `mocapi-streamable-http-transport` pom carries a compile-scoped
   dependency on `io.micrometer:context-propagation` (~50 KB, BOM-managed
   version). Spring Security 6 and Micrometer Observation already ship
   accessors via the SPI; users get propagation for free in the common
   case.
5. Mocapi's own per-request state (the `MrtrContext` /
   `McpToolContext.CURRENT` family, `McpTransport.CURRENT`, and the
   per-request `McpExchange`) is bound inside `server.handleCall` via
   `ScopedValue.where(...).run(...)`. ScopedValues propagate naturally to
   the VT body; nothing in the transport layer needs to forward them.
   **(Amended, ADR-0020):** the original rule listed `McpSession.CURRENT`;
   sessions were removed and the immutable `McpExchange` replaced it.

The stdio transport does not spawn a handler VT per call — it dispatches
each stdin line on a VT inside its reader loop, but there is no "request
thread" with thread-local context to carry across. No propagation needed
there.

## Consequences

**Wins:**

- Concurrency scales with VM memory, not Tomcat worker count. A server
  blocked on twenty pending elicitations does not stop accepting new
  calls.
- Spring Security guards see the authenticated principal on the handler
  VT. Micrometer spans show the inbound HTTP span as their parent.
  SLF4J MDC keys set by upstream filters survive.
- One change at one call site enables every library that ships a
  `ThreadLocalAccessor`. New accessors (custom request id filters,
  tenant context) light up by registering with `ContextRegistry`.

**Costs:**

- A small, mostly-no-op cost on every call (snapshot capture, wrap,
  restore on entry, clear on exit). Measured in microseconds; not a
  bottleneck at any MCP call rate.
- Handlers that mutate `ThreadLocal` state on the VT see those mutations
  cleared on exit — the wrap restores the captured values, runs the
  body, then clears. This is a feature for security/MDC contexts; it
  would surprise a handler that legitimately wanted to leak state back
  to the calling thread (which it can't do across the VT boundary
  anyway).

**Non-goals:** mocapi does not own any `ThreadLocal`-backed context
itself. Per-request state is in `McpContext` or `ScopedValue`. The
context-propagation mechanism enables third-party integrations; it is
not a place to add mocapi-specific propagation logic.

**Code anchors:** `mocapi-streamable-http-transport/.../StreamableHttpController.java` (`ContextSnapshot` capture); `mocapi-autoconfigure/.../transport/http/StreamableHttpAutoConfiguration.java` (`ContextSnapshotFactory` bean). Landed in commit `75de336d` (2026-04-12).
