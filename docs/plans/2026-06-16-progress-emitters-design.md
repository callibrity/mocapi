# Design — Typed progress emitters via `MrtrContext`

- **Date:** 2026-06-16
- **Status:** Implemented.
- **ADR:** [ADR-0025](../adr/0025-progress-emitters-and-mrtr-context.md)
  (Accepted). The living design docs (`handlers.md`, `elicitation-mrtr.md`)
  and guides (`interactive-tools.md`, `prompts.md`, `resources.md`) and the
  CHANGELOG were updated alongside the implementation.

## Problem

mocapi's only progress API is `McpToolContext.sendProgress(long, long)`.
Measured against the MCP 2026-07-28 progress notification rules, it is
narrower than the protocol allows on three counts:

| Spec (current) | `sendProgress(long, long)` |
|---|---|
| "The progress value MUST increase with each notification." | Not enforced — caller's burden, easy to violate silently. |
| "The progress and total values MAY be floating point." | Integer-only. Cannot express `0.5`, ratios, fractional percents. |
| "The message field SHOULD provide relevant human-readable progress information." | No `message` parameter at all — the field is hard-coded `null`. |

The wire model already supports the full shape:
`ProgressNotificationParams(ValueNode progressToken, double progress,
Double total, String message)`. Only the public API is the bottleneck.

A second gap: progress is reachable only from tool handlers.
`sendProgress` lives on `McpToolContext`; prompt and resource handlers
receive only `McpElicitor` and have no progress surface. Because the
Streamable HTTP transport's JSON-vs-SSE decision is method-agnostic
(`DirectMessageWriter`), the *only* reason `prompts/get` and
`resources/read` never stream today is that they have no way to emit a
pre-final notification. Give them a progress API and SSE works for them
for free. The spec scopes `progressToken` to any request, so progress on
prompts/resources is spec-legitimate.

## Decision summary

Replace `sendProgress(long, long)` with a small family of **stateful,
type-safe progress emitters**, reachable from all three MRTR-capable
handler kinds through a shared `MrtrContext` super-interface.

### New SPI (`mocapi-api`)

```java
// com.callibrity.mocapi.api.progress
public interface McpProgressSource {
    DoubleProgressEmitter doubleProgress(Double total);   // null total = unknown
    LongProgressEmitter   longProgress(Long total);       // null total = unknown
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
    void emit(String message);  // advances progress by 1 each call (1, 2, 3, …)
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

```java
// leaf contexts — empty today, room to diverge later
public interface McpToolContext     extends MrtrContext {}  // com.callibrity.mocapi.api.tools
public interface McpPromptContext   extends MrtrContext {}  // com.callibrity.mocapi.api.prompts
public interface McpResourceContext extends MrtrContext {}  // com.callibrity.mocapi.api.resources
```

`MrtrContext` makes the spec's "`InputRequiredResult` MAY be returned on
`tools/call`, `prompts/get`, `resources/read`; MUST NOT on any other
request" boundary a *type*: an `MrtrContext` (and therefore `elicit` and
progress) is only ever bound inside those three handler kinds. The same
boundary `McpElicitor` already lives on (ADR-0024) and the same three
seams `MrtrElicitationEngine` wraps (ADR-0021).

### Emitter semantics

1. **Stateful, captured at creation.** Each factory call captures the
   `total` and binds the request's progress token once; the returned
   emitter tracks the last-sent value. `null` total means "unknown" and
   is omitted from the wire (spec: progress still MUST increase).
2. **Monotonic guard — fail fast.** Every `progress(...)` / `complete(...)`
   call must be strictly greater than the previous value, or the emitter
   throws `IllegalArgumentException` with a diagnostic naming the prior
   and offending values. `percentProgress()` additionally bounds the
   fraction to `[0.0, 1.0]`.
3. **Validate always; token gates only the send.** The monotonic (and
   range) checks run regardless of whether the client supplied a
   `progressToken`. When no token is present the emitter is a validated
   sink: the call is checked and then discarded — so a non-increasing
   bug surfaces identically whether or not a particular client requested
   progress. Only the actual `transport.send` is conditional on the
   token.
4. **Wire mapping.** All three map onto
   `ProgressNotificationParams(token, progress(double), total(Double),
   message)`. `LongProgressEmitter` widens to `double`;
   `percentProgress()` sends `total = 1.0`; a `null` message is omitted.

### Breaking change

`McpToolContext.sendProgress(long, long)` is **removed** — clean break,
consistent with the project's pre-1.0 posture and the 2026-07-28 rewrite.
Migration is mechanical:

```java
// before
ctx.sendProgress(1, 3);

// after
var p = ctx.longProgress(3L);
p.emit(1, "step 1 done");
```

## Examples

```java
// Counting, known total — the loop-friendly default (no caller-managed counter)
var p = ctx.countingProgress((long) items.size());
for (var item : items) {
    process(item);
    p.emit("processed " + item.name());  // advances 1, 2, 3, …
}

// Long, known total — when you already hold the running value
var lp = ctx.longProgress((long) items.size());
long done = 0;
for (var item : items) {
    process(item);
    lp.emit(++done, "processed " + item.name());
}

// Percent — the sensible default
var pct = ctx.percentProgress();
pct.complete(0.25, "a quarter");
pct.complete(0.5);
pct.complete(1.0, "done");

// Double, unknown total
var d = ctx.doubleProgress(null);
d.emit(12.5, "bytes so far: 12.5MB");
d.emit(40.0, "bytes so far: 40MB");
```

A prompt or resource handler reaches the same API through its context:

```java
@McpPrompt(name = "summarize")
public String summarize(String topic, McpPromptContext ctx) {
    var pct = ctx.percentProgress();
    pct.complete(0.5, "gathering sources");
    // ...
    pct.complete(1.0, "done");
    return result;
}
```

Because a pre-final progress notification flips the transport to
`text/event-stream`, prompt and resource responses can now stream — the
transport needs no change.

## Interaction with MRTR replay

Progress emission obeys the same idempotency contract as elicitation
(ADR-0021): a handler that re-executes from the top on each round trip
re-emits the progress notifications it produced before its last
`elicit(...)`. This is expected, not a bug — clients see early progress
events repeated across round trips. The monotonic guard still holds
within a single execution; across round trips the per-execution emitter
starts fresh.

## Implementation plan (for the follow-up code PR)

1. **`mocapi-api`**
   - Add package `com.callibrity.mocapi.api.progress`:
     `McpProgressSource`, `DoubleProgressEmitter`, `LongProgressEmitter`,
     `CountingProgressEmitter`, `PercentageCompleteProgressEmitter`.
   - Add `com.callibrity.mocapi.api.context.MrtrContext extends
     McpElicitor, McpProgressSource` with `handlerName()`.
   - Add `McpPromptContext` (`api.prompts`) and `McpResourceContext`
     (`api.resources`), both `extends MrtrContext`.
   - Change `McpToolContext` to `extends MrtrContext`; **remove**
     `sendProgress` and the now-inherited `handlerName()` declaration;
     keep its `CURRENT` `ScopedValue`.
2. **`mocapi-server`**
   - Add a shared `DefaultMcpProgressSource` (token + transport +
     `ObjectMapper`) producing the three emitters; factor the existing
     `DefaultMcpToolContext` progress logic into it.
   - Implement `AbstractProgressEmitter` holding the monotonic state and
     token-gated send; three thin subclasses for double/long/percent.
   - Add `DefaultMcpPromptContext` and `DefaultMcpResourceContext`
     (compose `DefaultMcpElicitor` + `DefaultMcpProgressSource`).
   - Wire the request's `progressToken` (already parsed into
     `RequestMeta` for all three params types) into the prompt/resource
     contexts; today it is read and dropped for prompts/resources.
   - Add resolver(s) so prompt/resource handlers can declare
     `McpPromptContext` / `McpResourceContext` parameters, mirroring
     `McpElicitorResolver`. `McpElicitor` parameters keep working.
3. **Docs (same PR as code)**
   - Flip ADR-0025 to Accepted; update its code anchors to real paths.
   - Update `docs/design/handlers.md` and
     `docs/design/elicitation-mrtr.md` (the "user-facing surface"
     section) to describe `MrtrContext` + progress.
   - Update `docs/guides/interactive-tools.md`,
     `docs/guides/prompts.md`, `docs/guides/resources.md` with the new
     progress API and the `sendProgress` removal.
   - CHANGELOG entry under the breaking-changes heading.
4. **Tests**
   - Emitter unit tests: monotonic throw, percent range throw,
     validate-without-token, null-total omission, message passthrough,
     long→double widening.
   - Server tests: prompt/resource handler emitting progress produces
     `notifications/progress`; transport returns `text/event-stream` for
     a prompt that emits progress first.

## Open items / non-goals

- **Package placement** above is a recommendation; the implementation PR
  may consolidate (e.g. keep all three leaf contexts beside their
  existing handler packages, which is what is written here).
- **No per-call `total`.** Total is fixed at emitter creation by design;
  a handler whose total changes mid-stream creates a new emitter.
- **No sampling/roots** progress equivalents — those features are gone
  (SEP-2577, ADR-0022).
