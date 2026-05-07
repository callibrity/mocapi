# ADR-0004 — Streamable HTTP transport picks JSON vs SSE lazily, via a `MessageWriter` state machine

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

The MCP Streamable HTTP spec lets a server respond to a POSTed
`JsonRpcCall` in one of two shapes:

- A single `application/json` body containing the `JsonRpcResponse`. Cheap,
  one round trip, fine for `tools/list`, `resources/read`, and any tool
  that just computes and returns.
- A `text/event-stream` body that delivers zero or more notifications
  (progress, log, server-initiated elicitation/sampling calls) followed
  by a terminal `JsonRpcResponse` event. Required when a tool wants to
  stream progress or interact with the client mid-execution.

An earlier mocapi design forced every post-initialize request through
SSE (the "always-SSE" decision in spec 148, lifted in spec 149b/spec 012).
That worked, but it meant a `tools/list` response was published to a
short-lived Odyssey stream, framed as one SSE event, and then completed —
unnecessary cost, weird tracing shape, and incompatible with clients that
expected a JSON body for simple methods.

A purely declarative split ("if the tool is streamable, use SSE; otherwise
JSON") would force tool authors to declare their I/O shape up front. That
contradicts the goal of letting `McpToolContext.sendProgress(...)` Just
Work without metadata.

## Decision

The Streamable HTTP transport defers the response-shape decision until
the handler emits its first message. A sealed `MessageWriter` state
machine inside `StreamableHttpTransport` encodes the three reachable
states:

```java
sealed interface MessageWriter
        permits DirectMessageWriter, SseMessageWriter, ClosedMessageWriter {
    MessageWriter send(JsonRpcMessage message);
}
```

- **`DirectMessageWriter`** — initial state. Holds the
  `CompletableFuture<ResponseEntity<Object>>` that Spring MVC is awaiting.
  - On `send(JsonRpcResponse)`: completes the future with an
    `application/json` body whose entity is the response. Transitions to
    `ClosedMessageWriter`.
  - On `send(JsonRpcRequest)` (notification or server-initiated call):
    pulls a fresh `OdysseyStream` from the supplier, builds an
    `SseEmitter`, completes the future with a `text/event-stream` body,
    publishes the first message to the stream, and transitions to
    `SseMessageWriter`.
- **`SseMessageWriter`** — wraps the live Odyssey stream. Each `send`
  publishes the message; a terminal `JsonRpcResponse` additionally calls
  `stream.complete()` and transitions to `ClosedMessageWriter`.
- **`ClosedMessageWriter`** — every `send` throws `IllegalStateException`.
  Either the JSON response was committed and no further writes are
  legal, or the SSE stream was completed.

`McpEvent.SessionInitialized` is captured on `emit` and stashed; both
upgrade paths set the `MCP-Session-Id` header from it before completing
the future.

The handler runs on a fresh virtual thread for every `JsonRpcCall`,
including `initialize` and other one-shot methods. Uniformity beats
micro-optimization (virtual threads are cheap; see
[ADR-0006](0006-virtual-thread-per-call.md)).

The transport is created per call. `SynchronousTransport` and
`OdysseyTransport` from the prior design are deleted —
`StreamableHttpTransport` replaces both.

## Consequences

**Wins:**

- Simple tools get plain JSON. No SSE upgrade, no Odyssey stream
  allocation, no event framing, no priming event.
- Streaming tools get SSE without declaring themselves streamable.
  `sendProgress`, `log`, `elicit`, and `sample` all upgrade the
  response on first invocation, transparently.
- The state machine is a sealed type with exhaustive pattern matching;
  illegal transitions are compile errors, not runtime regressions.

**Costs:**

- The session-ID header is set when the future is completed, not before.
  For `initialize`, `emit(SessionInitialized)` happens during
  `handleCall` and the first `send` carries the header on the JSON
  response — works fine.
- A misbehaving handler that never sends anything leaves the future
  incomplete until Spring's async timeout fires. Same behavior as
  before; out of scope to "fix" without per-call timeout tuning.
- A handler that sends a notification, then throws, has already
  committed the SSE response; the thrown exception is dropped on the
  virtual thread floor (logged but not surfaced to the client). Also
  pre-existing behavior; a follow-up may add a stream-error event.

**Non-goals:** the transport does not look at the JSON-RPC method name to
decide JSON vs SSE. The handler's first `send` is the signal. Tool authors
do not declare a "streaming" flag.

**Code anchors:** `mocapi-streamable-http-transport/.../MessageWriter.java` (sealed: `DirectMessageWriter`, `SseMessageWriter`, `ClosedMessageWriter`); `StreamableHttpTransport.java`. Lazy-upgrade refactor landed in commit `68c3b658` (2026-04-17).
