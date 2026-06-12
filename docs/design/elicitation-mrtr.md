# Elicitation — MRTR Replay

MCP 2026-07-28 removes server-initiated requests: a server can no longer
push an `elicitation/create` JSON-RPC request at the client and block a
thread waiting for the reply. In its place the spec defines Multi
Round-Trip Requests (MRTR): when a handler needs input mid-execution, the
server *returns* an `InputRequiredResult` naming the inputs it needs; the
client gathers answers and retries the original call; the server
re-executes the handler with the answers available. Sampling does not move
to MRTR — it is removed outright (deprecated by SEP-2577; see
[ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md)).

This document describes the implemented mechanics. The decision record is
[ADR-0021](../adr/0021-mrtr-elicitation-replay.md), which supersedes the
Mailbox rendezvous ([ADR-0008](../adr/0008-mailbox-elicitation-sampling.md)).

## The shape of one conversation

```
Round trip 1:
  client → tools/call {name, arguments, _meta}
  handler runs … ctx.elicit("Your email?") has no answer
  server → result {resultType: "input_required",
                   inputRequests: {"elicit-1": {method: "elicitation/create",
                                                params: {message, requestedSchema}}},
                   requestState: "<opaque token>"}

Round trip 2:
  client → tools/call {name, arguments,
                       inputResponses: {"elicit-1": {action: "accept", content: {…}}},
                       requestState: "<same token>", _meta}
  handler runs FROM THE TOP … ctx.elicit("Your email?") returns the answer
  server → result {resultType: "complete", …}
```

The loop repeats once per unanswered elicitation: a handler with two
sequential `elicit()` calls completes in three round trips. Exactly three
RPC methods participate — `tools/call`, `prompts/get`, `resources/read` —
because those are the only methods whose params extend the spec's
`InputResponseRequestParams` and whose responses admit
`InputRequiredResult`.

## The four moving parts

All of this lives in `mocapi-server`'s
`com.callibrity.mocapi.server.mrtr` package.

### 1. `RequestStateCodec` — the token is the state

The server stores nothing between round trips. Everything needed to resume
the conversation is folded into the `requestState` token:

```json
{
  "method": "tools/call",
  "originalParams": { "name": "onboard", "arguments": { "plan": "pro" } },
  "inputResponses": [
    { "key": "elicit-1", "fingerprint": "sha256:…", "response": { "action": "accept", "content": { … } } },
    { "key": "elicit-2", "fingerprint": "sha256:…" }
  ],
  "issuedAt": 1781524800000
}
```

(`originalParams` is the request's params minus `_meta`, `inputResponses`,
and `requestState`.)

That payload is JSON-serialized, encrypted with AES-256-GCM (fresh random
96-bit nonce per encode, prepended to the ciphertext), and
Base64URL-encoded. GCM is an AEAD mode, so the token is simultaneously
opaque (the client cannot read the ledger) and tamper-evident (any
modification, or a decode under a different key, fails authentication).
Clients must treat it as an opaque blob per the spec; mocapi enforces
that by construction.

Configuration:

- `mocapi.mrtr.secret` — Base64-encoded 256-bit key, shared by every
  instance behind a load balancer. When unset, an ephemeral key is
  generated at startup with a prominent WARN: in-flight elicitations then
  die on restart and cannot be retried against another instance.
- `mocapi.mrtr.ttl` — token time-to-live (`Duration`, default `PT5M`,
  carried over from the retired `mocapi.elicitation.timeout` default).
  A token older than the TTL is rejected with a typed
  `ExpiredRequestStateException`.

### 2. The response ledger and call ordinals

Each `ctx.elicit(...)` call site is identified by its **call ordinal**:
the Nth `elicit()` reached during a handler execution maps to ledger
position N (key `elicit-<N>`, 1-based). On replay:

- An **answered** ordinal returns its recorded `ElicitResult`
  immediately — the handler never notices the round trip happened.
- The **first unanswered** ordinal stops the execution (see the signal
  below) and becomes the next `InputRequiredResult`.

Every ledger slot stores a SHA-256 **fingerprint** of the elicitation it
was issued for (the serialized message + requested schema). On replay,
the elicit call reaching position N must produce the same fingerprint; a
handler that asks a *different* question at an answered position has
violated the idempotency contract, and the request is rejected with
`-32602` and a diagnostic naming the contract. Without the fingerprint
check, a non-deterministic handler would silently receive answer N to a
question it never asked.

A `decline` or `cancel` `ElicitResult` is an answer like any other: it is
recorded in the ledger and returned to the handler, which decides what to
do (`ElicitResult.isAccepted()` semantics are unchanged).

### 3. `InputRequiredException` — internal unwinding

When `ctx.elicit(...)` reaches an unanswered ordinal, it cannot "block
until the client replies" — there is no reply channel. Instead it throws
`InputRequiredException`, an internal `RuntimeException` (stack-trace
suppressed; it is control flow, not an error — and it is named for the
`InputRequiredResult` it becomes) carrying the issued key and
the built `ElicitRequestFormParams`. The exception unwinds the handler stack
to the MRTR engine, which converts it into the `InputRequiredResult`.
It is never user-visible and never crosses the dispatch boundary; the
tool-error wrapping in `McpToolsService` explicitly rethrows it (along
with `ElicitationLedgerMismatchException` and
`McpElicitationNotSupportedException`) instead of converting it into an
`isError` tool result.

### 4. `MrtrElicitationEngine` — the seam

The engine is the production implementation of the internal
`ElicitationDispatcher` seam that `DefaultMcpToolContext.elicit(...)`
delegates to, and it wraps handler invocation inside the three
`@JsonRpcMethod` service methods (`McpToolsService.callTool`,
`McpPromptsService.getPrompt`, `McpResourcesService.readResource`):

```
execute(method, params, inputResponses, requestState, invocation)
  ├── ledger ← decode + validate requestState, merge inputResponses
  ├── run invocation with the ledger bound (ScopedValue)
  │     └── ctx.elicit(...) consults the ledger by ordinal
  ├── InputRequiredException → InputRequiredResult + fresh requestState
  └── otherwise → the handler's own result
```

Why that seam and not `DefaultMcpServer` or per-handler interceptors: the
service methods cover *exactly* the three MRTR-capable RPC methods (the
server entry point covers every method, including ones that may not
return `input_required`), they have the typed params carrying
`inputResponses`/`requestState`, and they are the last frame that can
return a value before ripcurl's catch-all exception translation would
swallow the signal. Their declared return type is `Object` because the
spec declares the response as a union (`CallToolResult |
InputRequiredResult`, etc.); ripcurl serializes the runtime type.

## Retry validation — what gets rejected with `-32602`

The engine never replays against state it cannot trust. Each of these is
rejected as JSON-RPC `-32602` (Invalid params), HTTP 400 on the
Streamable HTTP transport:

| Condition | Why |
|---|---|
| Tampered / wrong-key / malformed `requestState` | fails AES-GCM authentication |
| Expired `requestState` (older than `mocapi.mrtr.ttl`) | conversation lapsed |
| Retry method ≠ the method the token was issued for | token is not transferable across methods |
| Retry `name`/`uri` ≠ the original target | token is not transferable across tools/prompts/resources |
| `inputResponses` without `requestState` | answers with no conversation |
| `inputResponses` key the server never issued | unknown slot |
| `inputResponses` key already answered in a prior round trip | replayed answer |
| Non-elicitation `InputResponse` at an elicitation key | mocapi only ever issues `elicitation/create` requests |
| Fingerprint mismatch at an answered ordinal | handler violated the idempotency contract |

A retry that simply *doesn't* answer the pending question is not an
error: the handler re-executes, reaches the same unanswered ordinal, and
the same `InputRequiredResult` (with a re-minted token) goes back out.

## The user-facing surface: `McpElicitor`

`McpElicitor` (ADR-0024) owns the `elicit(...)` API. `McpToolContext`
extends it, so tools are unchanged; prompt and resource handlers declare
an `McpElicitor` parameter (resolved by the structural
`McpElicitorResolver`) and get identical semantics — the services bind
`McpElicitor.CURRENT` around handler invocation on all three MRTR seams,
with the same capability pre-check (`DefaultMcpElicitor`).

## Capability gating

Form-mode elicitation requires the client to declare the `elicitation`
capability in the per-request `_meta`
(`io.modelcontextprotocol/clientCapabilities`); a bare
`"elicitation": {}` counts as form-capable
(`McpExchange.supportsElicitationForm()`). When a handler elicits against
a client that did not declare it, `DefaultMcpToolContext` throws
`McpElicitationNotSupportedException`, which
`ElicitationNotSupportedExceptionTranslator` maps onto the spec's
`MissingRequiredClientCapabilityError`: JSON-RPC code `-32003` with
`data.requiredCapabilities = {"elicitation": {"form": {}}}` (HTTP 400).

## The idempotency contract

Replay's honest consequence, stated once here and repeated in the
[interactive tools guide](../guides/interactive-tools.md): **code before
your last `elicit()` call re-executes once per round trip — put side
effects after the final elicitation or make them idempotent.** A handler
that charges a credit card and *then* elicits a confirmation charges the
card once per round trip. The framework deliberately provides no
deduplication; idempotency belongs to the handler (or the application's
own idempotency keys).

The flip side: zero server-side state. Any node can serve any retry,
restarts lose nothing the client doesn't still hold, and there is no
rendezvous store, no parked-thread accounting, and no timeout-and-cancel
machinery.

## Limitations

- **Elicit from the dispatch thread only.** The ledger is bound via a
  `ScopedValue` for the duration of the dispatch. A tool that returns a
  `CompletionStage` and calls `ctx.elicit(...)` from a detached async
  thread gets an `IllegalStateException` — the signal could not unwind a
  foreign stack even if the ledger were visible there.
- **Token size grows linearly** with the number of accumulated answers;
  it rides the wire on every retry. The flat-schema constraint keeps
  answers small, but handlers with many round trips pay linearly.
- **Pre-elicitation work is re-paid every round trip.** Handlers doing
  expensive work before an `elicit()` should cache by their own means.
- **Concurrent retries of the same token are safe but independent.** The
  engine is stateless and the ledger is immutable inside the token, so two
  simultaneous retries carrying the same `requestState` each replay
  correctly and return independent results. The client owns serializing
  round trips if it wants exactly-once handler completion.
- **Broad `catch (Exception)` blocks inside a handler are a hazard.** The
  pending signal unwinds the handler's stack as a `RuntimeException`; a
  handler that catches and swallows it converts a pending elicitation into
  whatever the catch block returns, silently ending the round trip. Catch
  specific exception types around `elicit()` calls, or rethrow anything
  you did not expect (see the interactive-tools guide).

## The elicitation schema is constrained

Unchanged from before the migration: `RequestedSchemaBuilder`
(`mocapi-api`) is the only public way to construct a `requestedSchema`,
and it structurally forbids nested objects and arrays — clients render a
form, not a JSON editor. See
[ADR-0015](../adr/0015-constrained-elicitation-schema-builder.md). Only
the delivery mechanism moved; the schema vocabulary did not.

## Form mode only; URL mode is not implemented

Mocapi implements form-mode elicitation only. URL-mode
(`ElicitRequestURLParams`, `notifications/elicitation/complete`) is
declared not implemented in
[ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md).
The model types exist in `mocapi-model` for 1:1 schema fidelity
([ADR-0014](../adr/0014-mocapi-model-from-schema-ts.md)) but are not
reachable through `McpToolContext`.

## Related

- [Interactive tools guide](../guides/interactive-tools.md) — user-facing
  documentation for `ctx.elicit(...)` and progress.
- [ADR-0021](../adr/0021-mrtr-elicitation-replay.md) — the replay
  decision, including the rejected park-and-relay alternative.
- [ADR-0020](../adr/0020-stateless-request-model.md) — the stateless
  request model MRTR depends on.
- [ADR-0015](../adr/0015-constrained-elicitation-schema-builder.md) —
  flat-only schema builder.
- [ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md) —
  URL-mode elicitation and sampling omissions.
