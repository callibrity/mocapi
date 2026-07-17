# Interactive Features

Tools can communicate with the client mid-execution using `McpToolContext`. Add it as a parameter to your tool method -- the framework injects it automatically.

```java
import com.callibrity.mocapi.api.tools.McpToolContext;

@McpTool(name = "process", description = "Processes data with progress")
public ProcessResult process(String data, McpToolContext ctx) {
    var progress = ctx.longProgress(3L);
    progress.emit(1);
    // ... step 1 ...
    progress.emit(2);
    // ... step 2 ...
    progress.emit(3);
    return new ProcessResult("Done");
}
```

The `McpToolContext` parameter does not appear in the tool's input schema -- it is resolved by the framework, not provided by the client.

> **Where did `ctx.sample(...)` and `ctx.logger(...)` go?** MCP 2026-07-28
> deprecates Sampling and MCP Logging (SEP-2577), and mocapi does not
> implement deprecated features. Per the spec's migration guidance:
> integrate directly with your LLM provider's API instead of sampling, and
> use stderr (for stdio servers) or OpenTelemetry -- which `mocapi-otel`
> covers -- instead of MCP logging. See ADR-0022.

## Progress Notifications

Progress is reported through a typed emitter obtained from the context. Pick
the variant that fits how you measure progress; each captures the total once
and tracks its own running value:

```java
// Counting — one notification per item, no counter to manage (the common case)
var p = ctx.countingProgress((long) items.size());
for (var item : items) {
    process(item);
    p.emit("processed " + item.name());   // advances 1, 2, 3, …
}

// Long / Double — when you already hold the running value (total may be null = unknown)
ctx.longProgress(3L).emit(2, "step 2 of 3");
ctx.doubleProgress(1.0).emit(0.5, "halfway");

// Percentage — a fraction in [0.0, 1.0], reported against a total of 1.0
var pct = ctx.percentProgress();
pct.complete(0.5, "halfway");
pct.complete(1.0, "done");
```

The MCP spec requires progress to **strictly increase** with each
notification; the emitter enforces this and throws `IllegalArgumentException`
on a non-increasing value (and on a percentage outside `[0.0, 1.0]`). The
optional `message` is the spec's human-readable progress text. Notifications
are sent as `notifications/progress` on the request's own response stream
(which switches it to SSE), and only when the client included a
`progressToken` in the request's `_meta` — without one the emitter still
validates each call but sends nothing.

Progress isn't tool-only: prompt (`McpPromptContext`) and resource
(`McpResourceContext`) handlers expose the same emitters.

## Elicitation

Tools can ask the user for input during execution. In MCP 2026-07-28
elicitation is a **multi round-trip request (MRTR)**: when your tool calls
`ctx.elicit(...)` and the answer isn't available yet, the server responds
to the client with an `input_required` result describing the question plus
an opaque `requestState` token. The client shows the form, then *retries
the same call* with the answers attached -- and **your tool method runs
again from the top**. This time `ctx.elicit(...)` returns the answer
immediately and your code continues past it.

From your code's point of view, `elicit()` still looks like "ask a
question, get an answer":

```java
@McpTool(name = "onboard", description = "Onboards a new user")
public OnboardResult onboard(McpToolContext ctx) {
    ElicitResult result = ctx.elicit("Please enter your details", schema -> schema
        .string("name", "Your name")
        .string("email", "Email address", s -> s.email())
    );

    if (result.isAccepted()) {
        String name = result.getString("name");
        return new OnboardResult("Welcome, " + name + "!");
    }
    return new OnboardResult("Onboarding cancelled.");
}
```

### The idempotency contract (read this one paragraph)

**Code before your last `elicit()` call re-executes once per round trip --
put side effects after the final elicitation or make them idempotent.**

The replay model means your method body runs N+1 times for N unanswered
elicitations. Answered `elicit()` calls return instantly on replay, but
everything *between* the top of your method and the last `elicit()` runs
again on every round trip:

```java
@McpTool(name = "upgrade-plan", description = "Upgrades the user's plan")
public UpgradeResult upgrade(String userId, McpToolContext ctx) {
    // Runs once per round trip: keep it read-only / idempotent.
    Plan current = plans.lookup(userId);          // fine: a read
    // billing.charge(userId, ...);               // WRONG here: would charge once per round trip!

    ElicitResult choice = ctx.elicit("Pick a plan", schema -> schema
        .singleSelectEnum("plan", "New plan", e -> e.options("pro", "team")));
    if (!choice.isAccepted()) {
        return UpgradeResult.cancelled();
    }

    ElicitResult confirm = ctx.elicit("Confirm upgrade to " + choice.getChoice("plan") + "?",
        schema -> schema.bool("confirmed", "I understand the new price"));
    if (!confirm.isAccepted() || !confirm.getBool("confirmed")) {
        return UpgradeResult.cancelled();
    }

    // After the FINAL elicit(): runs exactly once, on the last round trip.
    billing.charge(userId, choice.getChoice("plan"));
    return UpgradeResult.upgraded(choice.getChoice("plan"));
}
```

### Elicitation from prompts and resources

Elicitation is not tool-only. Prompt and resource handler methods may
declare an `McpElicitor` parameter (ADR-0024) and call the same
`elicit(...)` API — `McpToolContext` itself extends `McpElicitor`, so
tool code is unchanged:

```java
@McpPrompt(name = "plan-review", description = "Reviews a plan with user-supplied context")
public GetPromptResult planReview(McpElicitor elicitor) {
    ElicitResult ctx = elicitor.elicit("What context should the review use?",
        schema -> schema.string("context", "Context"));
    // ...
}
```

The replay model — and the idempotency contract above — applies to
prompts and resources exactly as it does to tools.

### Don't catch what you can't handle

`ctx.elicit(...)` pauses your tool by throwing an internal control-flow
exception that mocapi catches at the dispatch layer. A broad
`catch (Exception e)` wrapped around an `elicit()` call will intercept it,
and whatever your catch block returns silently replaces the elicitation
round trip — the client never sees the question.

```java
// WRONG — swallows the elicitation control signal along with real errors
try {
    ElicitResult r = ctx.elicit("Confirm?", s -> s.bool("ok", "OK?"));
} catch (Exception e) {
    return Result.error("something went wrong");
}

// RIGHT — catch only what you can actually handle
try {
    riskyPreparation();
} catch (PreparationException e) {
    return Result.error("preparation failed: " + e.getMessage());
}
ElicitResult r = ctx.elicit("Confirm?", s -> s.bool("ok", "OK?"));
```

If you must wrap broadly, rethrow `RuntimeException`s you did not create.

This worked example completes in three round trips: the first returns the
plan question, the second returns the confirmation question, the third
charges the card and returns the result. The charge happens exactly once
because it sits after the final `elicit()`.

Two more consequences of the contract:

- **Be deterministic up to your last `elicit()`.** On replay, the Nth
  `elicit()` your code reaches must ask the same question (same message
  and schema) as the original execution -- the framework fingerprints
  each question and rejects the retry with a clear `-32602` diagnostic
  if a replay asks something different at an answered position.
- **Expensive pre-elicitation work is re-paid per round trip.** Cache it
  by your own means if that matters.

### Response Actions

The `ElicitResult` contains an `action` field:

- `ACCEPT` -- the user submitted the form. Access data via `result.content()` or the typed getters (`getString`, `getInteger`, `getBool`, `getChoice`, ...).
- `DECLINE` -- the user explicitly declined (clicked "No" or "Reject").
- `CANCEL` -- the user dismissed without choosing (closed the dialog, pressed Escape).

Declines and cancels are delivered to your handler as normal answers -- you
decide what to do with them, as in the examples above.

### Client capabilities

Every request carries the client's capabilities in `_meta`
(`io.modelcontextprotocol/clientCapabilities`). If the client did not
declare the `elicitation` capability and your tool calls `ctx.elicit()`,
the request fails with the spec's `MissingRequiredClientCapabilityError`
(JSON-RPC `-32021`, HTTP 400) telling the client exactly which capability
is required. There is no timeout involved -- the rejection is immediate.

### Server configuration

The `requestState` token that carries the conversation between round trips
is encrypted and signed. Two properties control it:

```properties
# Base64-encoded 256-bit key. REQUIRED in production / multi-instance
# deployments; when unset, an ephemeral key is generated at startup and
# in-flight elicitations will not survive a restart.
mocapi.mrtr.secret=

# How long a round trip may take before the token expires (default PT5M).
mocapi.mrtr.ttl=PT5M
```

An expired or tampered token is rejected with `-32602`; the client has to
start the call over.

### Elicit from the dispatch thread only

`ctx.elicit(...)` works while your handler executes on the dispatch thread
of `tools/call` (or `prompts/get` / `resources/read`). A tool that returns
a `CompletionStage` and elicits from a detached async thread gets an
`IllegalStateException`.

## Three Tool Patterns

Mocapi supports three patterns for tool methods. All are invoked identically -- there is no `isInteractive` flag or streaming mode switch.

### 1. Simple Return

The tool takes parameters, does work, returns a result:

```java
@McpTool(name = "hello", description = "Greets someone")
public HelloResponse hello(String name) {
    return new HelloResponse("Hello, " + name + "!");
}
```

### 2. Void Return

The tool performs a side effect and returns nothing:

```java
@McpTool(name = "notify", description = "Sends a push notification")
public void notify(String message) {
    pushService.send(message);
}
```

### 3. Interactive

The tool declares `McpToolContext` and uses it for mid-execution communication:

```java
@McpTool(name = "wizard", description = "Multi-step wizard")
public WizardResult wizard(McpToolContext ctx) {
    var progress = ctx.longProgress(3L);
    progress.emit(1);
    ElicitResult step1 = ctx.elicit(step1Params);
    progress.emit(2);
    ElicitResult step2 = ctx.elicit(step2Params);
    progress.emit(3);
    return new WizardResult(step1, step2);
}
```

Remember the replay model: this wizard completes in three round trips, and
the method body (including the `progress.emit(1)` line) runs three
times. Keep pre-elicitation code idempotent.

In all three patterns, the tool returns its result (or void). The framework wraps it in a `CallToolResult` and sends it as the JSON-RPC response. Tools never send their own result on the transport.

## See also

- [Elicitation — MRTR Replay (design)](../design/elicitation-mrtr.md) --
  how the replay engine, response ledger, and `requestState` codec work.
- ADR-0021 -- the replay decision and its trade-offs.
