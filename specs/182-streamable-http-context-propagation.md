# Propagate thread-local context across the HTTP transport's handler-VT spawn

## What to build

`StreamableHttpController.handleCall` spawns a fresh virtual thread
per MCP call via `Thread.ofVirtual().start(...)`. Virtual threads
created that way do **not** inherit `ThreadLocal`s, so every
`ThreadLocal`-backed context living on the request thread — Spring
Security's `SecurityContextHolder`, Micrometer's observation scope,
SLF4J MDC, Reactor context, etc. — vanishes at the spawn boundary.

Wire `io.micrometer:context-propagation` into the spawn so every
`ThreadLocalAccessor` registered via the SPI gets captured on the
request thread and restored on the handler VT. One change at one call
site, and every library that ships an accessor (Spring Security 6,
Micrometer Observation, SLF4J MDC via Reactor, etc.) participates
automatically.

### Why this matters

1. **Future Spring Security-backed guards need it.** The guards work
   queued on the observability roadmap lets plugins read
   `SecurityContextHolder` to decide whether a caller is entitled to
   see / invoke a handler. Without propagation, the handler VT sees
   an empty security context and every such guard returns "denied"
   (or worse, "allowed by accident" depending on default).
2. **Spec 183 (o11y) needs it for tracing parent linkage.** Spring
   Boot's `ServerHttpObservationFilter` opens an HTTP observation on
   the request thread. If `mcp.tool` / `mcp.prompt` / etc. start on
   the spawned VT without a captured observation scope, those spans
   become orphans — no parent HTTP span, no correlation back to the
   inbound request. Meters still work (they don't need the scope),
   but tracing dashboards show disconnected handler spans.
3. **MDC continuity.** SLF4J's MDC is a `ThreadLocal`. Even with
   spec 181's MDC customizer (which re-stamps keys on the handler
   VT), any MDC state set by upstream Spring filters (`TraceId`,
   request id, custom request filters) is already gone before our
   customizer runs. Propagation restores it.

### Design

Snapshot the context on the request thread, wrap the `Runnable`
before handing it to `Thread.ofVirtual().start(...)`:

```java
// StreamableHttpController — abbreviated
private final ContextSnapshotFactory contextSnapshotFactory; // @Bean

private CompletableFuture<ResponseEntity<Object>> handleCall(
        McpContext context, JsonRpcCall call) {
    var transport = new StreamableHttpTransport(() -> sseStreamFactory.responseStream(context));
    ContextSnapshot snapshot = contextSnapshotFactory.captureAll();
    Thread.ofVirtual()
            .start(snapshot.wrap(() -> {
                try {
                    server.handleCall(context, call, transport);
                } catch (Exception e) {
                    transport.response().completeExceptionally(e);
                }
            }));
    return transport.response();
}
```

`snapshot.wrap(runnable)` returns a `Runnable` that:

1. On `.run()`, restores every captured `ThreadLocal` on the current
   thread (the handler VT) using the registered accessors.
2. Executes the original runnable.
3. On exit, clears the restored `ThreadLocal`s (or restores whatever
   the VT previously had — which, for a just-spawned VT, is nothing).

When no accessors are registered (stdio-only style app that somehow
pulls the HTTP transport without Spring Security, actuator, or any
Micrometer integration), `captureAll()` returns an effectively empty
snapshot and `wrap` is a near-no-op wrapper.

### Bean wiring

`ContextSnapshotFactory` has a default constructor that discovers
accessors via `ServiceLoader`. We expose it as a Spring `@Bean` in
the HTTP transport autoconfig so users can override it (e.g. to
register custom accessors programmatically):

```java
@Bean
@ConditionalOnMissingBean
public ContextSnapshotFactory mcpContextSnapshotFactory() {
    return ContextSnapshotFactory.builder().build();
}
```

`StreamableHttpController` takes `ContextSnapshotFactory` as a
constructor argument.

### Dependency change

`mocapi-streamable-http-transport/pom.xml` gains:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>context-propagation</artifactId>
</dependency>
```

`context-propagation` is ~50KB, pure-Java, and already transitively
present for virtually every user (Spring Security 6 ships a
`ThreadLocalAccessor` for `SecurityContextHolder`; Micrometer
Observation ships one for its registry; Spring Boot's starters pull
it in). Users with none of those on classpath pay the jar-size cost
but get a true no-op at the wrap site.

Version is managed by the Spring Boot BOM already imported at the
root — no explicit `<version>` needed in the transport pom.

### Non-impact on stdio

`mocapi-stdio-transport` does not spawn a handler VT per call — it
reads/writes on the stdio loop thread. No change needed there; this
spec is exclusively about the HTTP transport's spawn point.

## Acceptance criteria

- [ ] `mocapi-streamable-http-transport` pom declares a `compile`-scoped
      dependency on `io.micrometer:context-propagation` (no explicit
      version — BOM-managed).
- [ ] `StreamableHttpController` takes a `ContextSnapshotFactory`
      constructor arg.
- [ ] `handleCall` captures a snapshot on the request thread and
      wraps the VT `Runnable` via `ContextSnapshot.wrap(...)` before
      `Thread.ofVirtual().start(...)`.
- [ ] HTTP transport autoconfig exposes a default
      `ContextSnapshotFactory` bean guarded by
      `@ConditionalOnMissingBean`.
- [ ] Unit test: stub `ThreadLocalAccessor` registered in the test
      classpath; set a value on the request thread; invoke a tool
      handler; assert the value is visible on the handler VT during
      the invocation and cleared after.
- [ ] Unit test: with Spring Security on the test classpath, set a
      `SecurityContextHolder.getContext()` on the request thread;
      assert `SecurityContextHolder.getContext()` returns the same
      context during handler invocation on the VT.
- [ ] Integration test (in `mocapi-streamable-http-transport/src/test`
      or conformance-style): start a Micrometer `Observation` on the
      request thread, invoke a tool; assert a child observation
      started inside the handler sees the outer observation as its
      parent.
- [ ] `docs/architecture.md` (or a new `docs/transports.md` section
      if that's cleaner) documents the VT spawn + context
      propagation guarantee.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Changed`: entry noting
      request-thread context now propagates to handler VTs on the
      streamable-HTTP transport.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Stdio transport changes.** No VT boundary crossed there.
- **Adding ThreadLocal accessors ourselves.** We don't own any
  `ThreadLocal`-backed context (mocapi's own per-request state is
  explicitly threaded through `McpContext`). This spec only enables
  the mechanism.
- **Rewiring how handlers are dispatched** (e.g. dropping the VT
  spawn entirely, running the handler on the HTTP thread). The
  per-call VT spawn serves asynchrony / cancellation / SSE-streaming
  semantics — keeping it, just making it context-aware.
- **`ScopedValue` propagation.** Mocapi's own `ScopedValue`-backed
  context (`McpSession.CURRENT`, `McpToolContext.CURRENT`,
  `McpTransport.CURRENT`) is bound inside `server.handleCall` via
  explicit `ScopedValue.where(...).run(...)` calls; nothing to
  propagate at the transport layer.

## Implementation notes

- `ContextSnapshotFactory` is thread-safe and reusable; one bean is
  enough. The default factory discovers accessors via
  `ServiceLoader` at construction time.
- Spring Boot 3.2+ ships the `ObservationThreadLocalAccessor` (from
  Micrometer Observation) and — with Spring Security 6 on the
  classpath — a `SecurityContextHolder`-backed accessor. Users get
  both for free in the common case.
- `ContextSnapshot.wrap(Runnable)` returns a regular `Runnable`, so
  no changes to the `Thread.ofVirtual().start(...)` shape.
- Exception path: `wrap` does not interfere with exception
  propagation; the existing `try/catch` around `server.handleCall`
  inside the runnable stays.
- Spec 183 (`mocapi-o11y`) depends on this landing — without
  context propagation, `mcp.tool` spans are orphans rather than
  children of the inbound HTTP span.
