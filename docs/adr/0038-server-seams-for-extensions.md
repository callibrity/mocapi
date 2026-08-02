# ADR-0038 — Three generic `mocapi-server` seams for the Tasks extension

- **Status:** Accepted
- **Date:** 2026-08-02

## Context

[ADR-0037](0037-mcp-tasks-extension.md) needs `mocapi-tasks` to run an
`@McpTask` tool a second time, off the `tools/call` dispatch thread,
resuming against a ledger that lives in a `TaskStore` record instead of
an encrypted wire token. Before this change, the MRTR replay mechanics
(call-ordinal cursor, fingerprint enforcement,
`InputRequiredException` raising) were private to `MrtrElicitationEngine`,
and full tool invocation (handler lookup, `DefaultMcpToolContext`
construction, ScopedValue binding, the six-stratum interceptor chain,
result/exception → `CallToolResult` mapping) was private to
`McpToolsService.invokeTool`. Nothing in `mocapi-server` could be called
from outside the wire request/retry path.

`mocapi-tasks` also needs to intercept `tools/call` before it runs the
default path (to redirect an `@McpTask` call into task creation instead
of synchronous execution), and needs the `Mcp-Name` routing-header
validation table — currently a fixed map owned by the transport — to
accept `tasks/get|update|cancel` alongside the built-in methods.

All three needs are generic (extension-agnostic): nothing about them is
Tasks-specific, and the existing MRTR test suite passing unchanged after
each extraction is the acceptance gate.

## Decision

Add three seams to `mocapi-server`, each a small, behavior-preserving
refactor of an existing private mechanism.

1. **`ReplayExecutor` — the extracted ledger-replay core.** The
   ordinal-cursor / fingerprint-check / `InputRequiredException`
   machinery moves out of `MrtrElicitationEngine` into a standalone
   class implementing the existing `ElicitationDispatcher` seam.
   `execute(ledger, invocation)` runs a `Supplier<Object>` with the
   ledger bound via `ScopedValue` and returns a sealed `ReplayOutcome`
   (`Completed` / `InputRequired`). `MrtrElicitationEngine` becomes a
   caller: it owns only the wire-token carrier (`RequestStateCodec`
   decode/encode, the `-32602` validation table, principal/target
   verification) around a call to `ReplayExecutor.execute(...)`.
2. **`ToolCallReplayInvoker` — detached tool invocation.** A new public
   interface whose `invoke(toolName, arguments, ledger, progressOverride,
   exchange)` runs a registered tool by name against a caller-supplied
   ledger, off the normal dispatch thread, with no wire envelope (no
   `requestState`, no principal/target verification — the caller owns
   the ledger's identity and lifecycle). `McpToolsService` implements it
   directly, reusing the same handler lookup, `DefaultMcpToolContext`
   construction, and six-stratum chain the synchronous path uses, so
   wire and task execution cannot drift semantically — there is exactly
   one execution core with two carriers (wire token, task store).
3. **`ToolCallDispatchCustomizer` — a `tools/call` dispatch hook.**
   `McpToolsService.callTool` consults an ordered
   `List<ToolCallDispatchCustomizer>` after handler lookup and before the
   default MRTR invocation path. Each customizer's
   `dispatch(handler, params)` returns `Optional<Object>`; the first
   non-empty result short-circuits the request and becomes the response
   as-is (the spec's response union — core neither inspects nor
   interprets it). `mocapi-tasks` registers the sole v1 implementation,
   `TaskToolCallDispatcher`: it claims a call iff the handler carries
   `@McpTask` (via `AnnotatedElementUtils.findMergedAnnotation`, the
   [ADR-0032](0032-meta-annotation-aware-handler-discovery.md)
   meta-annotation mechanism) and the request declares the `tasks`
   client capability. Unclaimed calls fall through to today's behavior,
   byte-for-byte — an empty customizer list is a no-op.
4. **`McpRoutedParamContributor` — routing-header validation
   contribution.** The transport's `Mcp-Name` validation table
   (`-32020 HeaderMismatch`) gains a contribution seam:
   `namedParamFields()` returns a `Map<String, String>` of JSON-RPC
   method → the `params` field `Mcp-Name` must mirror. It lives in
   `mocapi-server` (transport-agnostic) so any module can implement it;
   transports that don't validate routing headers (stdio) ignore
   contributed instances. `mocapi-tasks` contributes `tasks/get`,
   `tasks/update`, `tasks/cancel` → `params.taskId` via
   `TasksRoutedParamContributor`. Transports keep owning wire validation
   ([I2](../constitution.md#i2--single-protocoltransport-coupling))
   without hardcoding extension knowledge.

A fourth, smaller seam rides alongside: **`ProgressSink`** — the
delivery point every progress emitter calls once its monotonic-increase
guard accepts an update. Extracting it from the wire-only
`notifications/progress` send lets `mocapi-tasks` supply a sink that
writes a task's `statusMessage` via store mutation instead, while
`ProgressChannel`'s validation logic runs identically in both modes.

## Consequences

**What this buys us.** `mocapi-tasks` needs zero core changes beyond
these four seams, and each is generic enough that a future extension
needing the same shapes (detached replay, dispatch interception, routed
params, alternate progress delivery) reuses them without touching core
again. The wire MRTR path is unchanged in behavior — the refactor gate
for seam 1 and 2 is the existing MRTR unit/integration suite passing
unchanged before any task code is built on top.

**Costs.** `McpToolsService` now carries two public-facing roles
(`ToolCallReplayInvoker` implementor and dispatch-customizer host)
instead of one opaque service; `ToolCallReplayInvoker` and
`ToolCallDispatchCustomizer` are now part of `mocapi-server`'s public
surface and need the same compatibility discipline as any other SPI.

**Non-goals.** Prompts and resources keep using `ReplayExecutor`
internally through their own MRTR engines; only the tool invoker
(`ToolCallReplayInvoker`) is public in v1 — a task-augmenting
`prompts/get`/`resources/read` is explicitly out of scope for the
Tasks extension ([ADR-0037](0037-mcp-tasks-extension.md)), so there is
no detached invoker for those kinds yet.

**Code anchors:**

- `mocapi-server/src/main/java/com/callibrity/mocapi/server/mrtr/ReplayExecutor.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolCallReplayInvoker.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/ToolCallDispatchCustomizer.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/routing/McpRoutedParamContributor.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/progress/ProgressSink.java`
