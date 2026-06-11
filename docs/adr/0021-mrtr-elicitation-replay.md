# ADR-0021 — MRTR elicitation via replay

- **Status:** Accepted — supersedes [ADR-0008](0008-mailbox-elicitation-sampling.md)
- **Date:** 2026-06-11

## Context

MCP 2026-07-28 removes server-initiated requests: servers MUST NOT send
JSON-RPC requests on SSE streams. In their place, SEP-2322 defines
Multi Round-Trip Requests (MRTR). When a server needs input
mid-execution, it does not push a request to the client — it *returns*
an `InputRequiredResult` containing the `inputRequests` it needs
answered and an opaque `requestState` token. The client gathers the
answers and retries the original call, attaching `inputResponses` and
the unmodified `requestState`. The round trip repeats until the handler
can finish.

Mocapi's previous elicitation design
([ADR-0008](0008-mailbox-elicitation-sampling.md)) assumed the old
model: the handler's virtual thread blocked on a Substrate Mailbox
until the client's response arrived, possibly on another node. With no
server-initiated request channel left in the protocol, that rendezvous
has nothing to rendezvous. The question is how `ctx.elicit(...)` —
which from the handler author's perspective still looks like "ask a
question, get an answer" — maps onto a request/retry protocol in a
stateless server ([ADR-0020](0020-stateless-request-model.md)).

## Decision

Mocapi implements MRTR elicitation with the **replay pattern**: on each
retry the handler re-executes from the top, and `ctx.elicit(...)`
consults a ledger of accumulated responses to decide whether to return
or to yield.

**Rules:**

1. `requestState` is a self-contained, signed and encrypted blob
   containing `{method, originalParams, inputResponses[], issuedAt}`.
   The server stores nothing; the token *is* the state. Clients treat
   it as opaque per the spec; tampering fails signature verification.
2. When a handler calls `ctx.elicit(...)` and no answer is available,
   the call raises an internal control signal. The dispatcher converts
   it into an `InputRequiredResult` carrying the built `inputRequests`
   and a fresh `requestState` that folds in everything answered so far.
3. On retry, the server verifies and decrypts `requestState`, merges
   the incoming `inputResponses` into the ledger, and re-dispatches the
   original call (`method` + `originalParams`) from the top. Each
   `ctx.elicit(...)` call site is identified by its **call ordinal** —
   the Nth `elicit` reached during execution. Answered ordinals return
   their result immediately; the first unanswered ordinal yields a new
   `InputRequiredResult`.
4. The flat-schema `RequestedSchemaBuilder`
   ([ADR-0015](0015-constrained-elicitation-schema-builder.md)) is
   unchanged — the schema vocabulary handlers use to describe the
   input they need is the same; only the delivery mechanism moved.

**The honest consequence:** handlers must be idempotent up to their
last `elicit()` call. Code before an `elicit()` re-runs once per round
trip. A handler that charges a credit card and *then* elicits a
confirmation will charge the card again on every retry. Side effects
belong after the final `elicit()`, or behind the application's own
idempotency keys.

**Rejected alternative: park-and-relay on the Substrate Mailbox.** The
incumbent design could have been adapted: park the handler's virtual
thread mid-execution, return `InputRequiredResult` with a `requestState`
that names the parked continuation, and have the retry deliver
`inputResponses` to the Mailbox to wake it. That preserves blocking
semantics, imposes no idempotency requirement, and is cluster-correct
via a shared store. It was rejected because it holds a parked virtual
thread plus durable state per round trip; the parked work dies on
deploys and restarts; and its `requestState` would not be
self-contained — it would be a pointer into server-side state,
defeating exactly the statelessness this migration adopts
([ADR-0020](0020-stateless-request-model.md)).

## Consequences

**What this buys us.** Elicitation works with zero server-side state:
any node can serve any retry, restarts lose nothing (the client holds
the token), and there is no rendezvous store, no timeout-and-cancel
machinery, and no parked-thread accounting. The handler-author API
stays a plain blocking-looking call — no continuation-passing leaks
into tool code.

**Costs.** The idempotency contract is real and falls on handler
authors; it is documented prominently in the guides and in the
`ctx.elicit(...)` javadoc. Re-execution also re-pays the cost of the
code before the last `elicit()` on every round trip — handlers doing
expensive pre-elicitation work should cache via their own means.
`requestState` grows with the number of accumulated responses and rides
the wire on every retry; the flat-schema constraint keeps answers
small, but a handler with many round trips pays linearly.

**Non-goals.** No durable continuation store, no "resume exactly where
you left off" semantics, and no framework-level deduplication of
side effects — idempotency is the handler's responsibility. Sampling
does not move to MRTR; it is removed outright (deprecated by SEP-2577;
see [ADR-0022](0022-2026-07-28-features-not-implemented.md)).

This ADR supersedes [ADR-0008](0008-mailbox-elicitation-sampling.md)
(Substrate Mailbox rendezvous for elicitation/sampling).
