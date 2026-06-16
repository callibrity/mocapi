# ADR-0025 — Typed progress emitters and the `MrtrContext` super-interface

- **Status:** Accepted
- **Date:** 2026-06-16

## Context

mocapi's only progress API is `McpToolContext.sendProgress(long, long)`.
The MCP 2026-07-28 progress notification rules are broader than it
exposes:

- "The progress value MUST increase with each notification, even if the
  total is unknown." — `sendProgress` enforces nothing; monotonicity is
  the caller's burden and a non-increasing value ships silently.
- "The progress and the total values MAY be floating point." —
  `sendProgress` is integer-only and cannot express `0.5`, ratios, or
  fractional percents.
- "The message field SHOULD provide relevant human-readable progress
  information." — `sendProgress` has no `message` parameter; the field is
  hard-coded `null`.

The wire model is already correct
(`ProgressNotificationParams(ValueNode progressToken, double progress,
Double total, String message)`); only the public surface is narrow.

Progress is also tool-only. `sendProgress` lives on `McpToolContext`;
prompt and resource handlers receive just `McpElicitor`
([ADR-0024](0024-mcp-elicitor-spi.md)) and cannot report progress. The
Streamable HTTP transport's JSON-vs-SSE choice is method-agnostic
([ADR-0004](0004-lazy-json-vs-sse-state-machine.md)), so the sole reason
`prompts/get` and `resources/read` never stream is that they have no way
to emit a pre-final notification. The spec scopes `progressToken` to any
request, so progress on prompts/resources is legitimate — and the spec's
`InputRequiredResult` table names exactly the same three methods
(`tools/call`, `prompts/get`, `resources/read`) that already share the
elicitation surface.

ADR-0024 deferred this explicitly ("no progress API for
prompts/resources … revisit on demand") and chose *not* to mint per-kind
context types because their only member would have been `elicit`. Adding
a progress surface changes that calculus: there is now a second
mid-execution capability to carry, and a shared super-interface for the
three MRTR-capable contexts becomes worth its weight.

## Decision

Replace `sendProgress(long, long)` with a family of stateful, type-safe
progress emitters, exposed through a new `McpProgressSource` factory, and
introduce an `MrtrContext` super-interface that unifies elicitation and
progress for the three MRTR-capable handler kinds.

```java
// com.callibrity.mocapi.api.progress
public interface McpProgressSource {
    DoubleProgressEmitter doubleProgress(Double total);   // null = unknown
    LongProgressEmitter   longProgress(Long total);       // null = unknown
    CountingProgressEmitter countingProgress(Long total); // auto +1 per emit
    PercentageCompleteProgressEmitter percentProgress();  // wire total = 1.0
}
public interface DoubleProgressEmitter {
    void emit(double progress, String message);
    void emit(double progress);
}
public interface LongProgressEmitter {
    void emit(long progress, String message);
    void emit(long progress);
}
public interface CountingProgressEmitter {
    void emit(String message);  // advances progress by one (1, 2, 3, …)
    void emit();
}
public interface PercentageCompleteProgressEmitter {
    void complete(double fraction, String message);  // 0.0–1.0
    void complete(double fraction);
}

// com.callibrity.mocapi.api.context
public interface MrtrContext extends McpElicitor, McpProgressSource {
    String handlerName();
}
```

**Rules:**

1. `McpToolContext`, `McpPromptContext`, and `McpResourceContext` each
   `extend MrtrContext`. The leaves are empty today; they exist so each
   handler kind can grow type-specific members later without disturbing
   the others. `handlerName()` moves up from `McpToolContext` to
   `MrtrContext`.
2. `sendProgress(long, long)` is **removed** from `McpToolContext`. The
   emitters supersede it (they add floats, the `message` field, and the
   monotonic guard). This is a breaking change, taken cleanly per the
   pre-1.0 / 2026-07-28 posture ([ADR-0019](0019-clean-break-2026-07-28.md)).
3. Each emitter captures its `total` and binds the request's progress
   token at creation and tracks the last-emitted value. A `null` total
   means "unknown" and is omitted on the wire.
4. Every `progress(...)` / `complete(...)` call must be **strictly
   greater** than the previous value or the emitter throws
   `IllegalArgumentException`. `percentProgress()` additionally bounds
   the fraction to `[0.0, 1.0]`. Validation runs **regardless of whether
   a progress token was supplied** — the token gates only the network
   send, so a non-increasing bug surfaces the same way for every client.
5. The server binds an `MrtrContext` (and resolves
   `McpToolContext` / `McpPromptContext` / `McpResourceContext`
   parameters) around dispatch of the three MRTR-capable methods only,
   reusing the seams of [ADR-0021](0021-mrtr-elicitation-replay.md).
   `McpElicitor` parameters keep resolving as before.

**Code anchors:** `mocapi-api/.../progress/McpProgressSource.java` (+
`DoubleProgressEmitter`, `LongProgressEmitter`, `CountingProgressEmitter`,
`PercentageCompleteProgressEmitter`), `mocapi-api/.../context/MrtrContext.java`,
`mocapi-api/.../tools/McpToolContext.java`,
`mocapi-api/.../prompts/McpPromptContext.java`,
`mocapi-api/.../resources/McpResourceContext.java`,
`mocapi-server/.../progress/DefaultMcpProgressSource.java` (+
`ProgressChannel` and the four emitter impls),
`mocapi-server/.../context/AbstractMrtrContext.java`,
`mocapi-server/.../tools/DefaultMcpToolContext.java`,
`mocapi-server/.../prompts/DefaultMcpPromptContext.java`,
`mocapi-server/.../resources/DefaultMcpResourceContext.java`, and the
`McpPromptContextResolver` / `McpResourceContextResolver`. Full design and
implementation plan:
[docs/plans/2026-06-16-progress-emitters-design.md](../plans/2026-06-16-progress-emitters-design.md).

## Consequences

**What this buys us.** The progress API matches what the spec permits:
floating-point progress/total, the human-readable `message`, and a
monotonic-increase guarantee the type system enforces by construction.
Prompts and resources gain progress — and, because the transport is
method-agnostic, SSE streaming — for free. `MrtrContext` turns the
spec's "these three methods only" boundary into a compile-time fact and
gives the two mid-execution capabilities (elicit, progress) one home.

**Costs.** A breaking change: every `ctx.sendProgress(p, t)` call site
must migrate to `ctx.longProgress(t).emit(p)` (mechanical). The API
surface grows by one factory interface, three emitter interfaces, one
super-interface, and two leaf context interfaces. Progress now shares the
MRTR idempotency hazard: a replayed handler re-emits the progress it
produced before its last `elicit(...)` (documented, ADR-0021).

**Non-goals.** No per-call `total` (it is fixed at emitter creation; make
a new emitter if it changes). No sampling/roots progress equivalents
(removed features, [ADR-0022](0022-2026-07-28-features-not-implemented.md)).
No change to the transport, the MRTR engine, or the elicitation schema.

**Supersedes the progress non-goal of
[ADR-0024](0024-mcp-elicitor-spi.md)**, which deferred a prompt/resource
progress API and declined per-kind context types. Both are now adopted;
ADR-0024's elicitation decision otherwise stands, with `McpElicitor`
becoming a super-interface of `MrtrContext`.
