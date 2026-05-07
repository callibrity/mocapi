# Elicitation and Sampling

Elicitation (`elicitation/create`) and sampling (`sampling/createMessage`) are
*server-to-client* JSON-RPC requests. A handler running on the server pauses
mid-execution, asks the client for input (a form response or an LLM
completion), and resumes when the client replies. Both follow the same
mechanical pattern; only the request and result types differ.

This document describes the rendezvous mechanism that makes a synchronous
handler API work over an asynchronous, possibly multi-node transport.

See also [ADR-0008](../adr/0008-mailbox-elicitation-sampling.md) for the
underlying decision and [ADR-0006](../adr/0006-virtual-thread-per-call.md)
for the virtual-thread execution model the rendezvous depends on.

## The request/response shape

When user code calls `ctx.elicit(...)` or `ctx.sample(...)` on
`McpToolContext`, mocapi sends a JSON-RPC **request** (not a notification)
back to the client over the same session's transport. The MCP spec requires
the client to reply with a JSON-RPC **response** carrying the same `id`. The
server handler is suspended until that response arrives.

Both sides of the conversation flow over the existing transport — for
Streamable HTTP, the server-to-client request is delivered on the open SSE
stream; the client's reply comes back as a POST whose body is a JSON-RPC
response (not a request). For stdio, both directions are framed messages on
the same pipe.

## The rendezvous: Substrate Mailbox

`McpResponseCorrelationService` (in `mocapi-server`) is the single seam
where the suspend/resume happens. It uses Substrate's `Mailbox<JsonNode>` as
a one-shot rendezvous keyed by a UUID correlation id:

```
sendAndAwait(method, params, ResultType.class, transport)
  ├── correlationId = UUID.randomUUID()
  ├── mailbox = mailboxFactory.create("mcp:correlation:" + correlationId, ...)
  ├── transport.send(JsonRpcCall(method, params, id=correlationId))
  ├── subscription.next(timeout)        ← virtual thread blocks here
  │     ├── Value(node)  → deserialize to ResultType, return
  │     └── Timeout      → send notifications/cancelled, throw
  └── mailbox.delete()                   (finally)
```

The handler virtual thread parks on `subscription.next(timeout)`. When the
client's reply arrives at the transport layer, the inbound dispatcher
recognizes the message as a `JsonRpcResponse` (response, not call) and
invokes `McpResponseCorrelationService.deliver(response)`. `deliver`
connects to the mailbox by correlation id and calls `mailbox.deliver(...)`,
which unparks the waiter. The handler thread resumes inside `sendAndAwait`,
deserializes the result into the expected type, and returns it to user
code.

Because each call gets its own mailbox and its own correlation id, multiple
in-flight elicitations or samplings on the same session are independent.

## Why a Mailbox and not a `CompletableFuture`

A `CompletableFuture` parked in a `ConcurrentHashMap` would work for a
single-node deployment and nothing else. The two pieces of the rendezvous
— the handler thread waiting and the inbound message that wakes it — can
land on **different JVMs** in a clustered deployment: handler runs on
node A, client's POST with the response hits node B because of load
balancing. The waiter on node A would never wake.

`Mailbox` is part of the Substrate storage SPI ([ADR-0007](../adr/0007-substrate-storage-spi.md)),
so its implementation is pluggable. The in-memory backend is single-JVM;
the Redis, PostgreSQL, NATS, and Hazelcast backends are cluster-aware. A
`mailbox.deliver(value)` call on node B reaches the subscriber waiting on
node A through the shared store. The design doc for the storage SPI
covers the consistency guarantees each backend offers; for elicitation
and sampling, the only requirement is that a `deliver` posted before a
`next(timeout)` returns timeout will be observed.

This is the entire reason elicitation and sampling exist as a separate
abstraction in the codebase rather than as ad-hoc futures: cluster
correctness comes for free as long as the mailbox primitive is shared.

## Cancellation

When `subscription.next(timeout)` returns `Timeout`, the correlation
service:

1. Sends a `notifications/cancelled` JSON-RPC notification to the client
   carrying the original correlation id and a reason (`"Server timeout
   waiting for client response"`).
2. Throws `McpClientResponseTimeoutException` from `sendAndAwait`.

The handler-side exception propagates up the invoker chain. In tools, the
framework catches it and surfaces it to the model as
`CallToolResult.isError=true` so the model can recover gracefully
([interactive tools guide](../guides/interactive-tools.md)). The
`notifications/cancelled` lets a cooperative client tear down any UI it
had open for the request — without it, a long-prompt elicitation could
sit open in the client's UI well after the server stopped caring.

There is no server-side handle for "give up early" beyond the timeout.
The handler virtual thread does not currently respond to interrupt; if
the timeout is wrong for the operation, raise it via
`mocapi.elicitation.timeout` / `mocapi.sampling.timeout` (both `Duration`).

## The elicitation schema is constrained

The MCP spec says elicitation requests carry a `requestedSchema` describing
a flat object. The server must not ask for nested objects or arrays —
clients render this as a form, not a JSON editor. Mocapi enforces the
constraint structurally rather than by runtime validation.

`RequestedSchemaBuilder` is the only public way to construct a requested
schema. It exposes typed methods (`string`, `number`, `integer`,
`boolean`, `singleSelectEnum`, `multiSelectEnum`, …) keyed by property
name. Each typed method takes a sub-builder for that primitive's
constraints (min/max, enum values, format hint). There is no
`object(...)` or `array(...)` method; there is no escape hatch like
`raw(JsonNode)`. Code that wants a nested schema cannot compile.

See [ADR-0015](../adr/0015-constrained-elicitation-schema-builder.md) for
the rationale — particularly why "no escape hatch" is treated as a
feature rather than a limitation.

## Form mode only; URL mode is not implemented

The MCP spec defines two elicitation modes:

- **Form mode** — the client renders a form from the requested schema and
  returns the user's responses inline.
- **URL mode** — the server returns a URL the client opens in a browser;
  the user completes some out-of-band flow; the result is delivered back
  by some implementation-defined mechanism.

Mocapi implements form mode only. URL mode is not supported. See
[ADR-0018](../adr/0018-mcp-spec-features-not-implemented.md) for the
rationale (URL mode pulls in a redirect/callback story that doesn't fit
the rendezvous model and has no compelling use case in practice).
`ElicitRequestURLParams` exists in `mocapi-model` because the model
module is a 1:1 translation of the spec's `schema.ts`
([ADR-0014](../adr/0014-mocapi-model-from-schema-ts.md)), but it is not
reachable through `McpToolContext`.

## What the handler API looks like

User code never sees correlation ids, mailboxes, or transports. From a
tool method, the synchronous shape is:

```java
ElicitResult result = ctx.elicit("Please confirm", schema -> schema
    .string("name", "Your name")
    .string("email", "Email", s -> s.email()));

if (result.isAccepted()) { ... }
```

```java
CreateMessageResult completion = ctx.sample(params);
```

Internally, both calls hit `McpResponseCorrelationService.sendAndAwait`
with `McpMethods.ELICITATION_CREATE` or `McpMethods.SAMPLING_CREATE_MESSAGE`
respectively, the typed result class, and the session's transport. The
rest of the handler keeps running on the same virtual thread once the
mailbox unparks.

## Capability check

Elicitation and sampling require the client to advertise the matching
capability during `initialize`. If the client did not, the server has no
contract that obligates the client to reply, and the call would simply
time out. `McpToolContext` checks the negotiated `ClientCapabilities` up
front and throws `McpElicitationNotSupportedException` (or the sampling
equivalent) immediately, rather than letting the timeout play out.

## Related

- [Interactive tools guide](../guides/interactive-tools.md) — user-facing
  documentation for `ctx.elicit(...)`, `ctx.sample(...)`, progress, and
  logging.
- [ADR-0008](../adr/0008-mailbox-elicitation-sampling.md) — Mailbox
  rendezvous decision.
- [ADR-0015](../adr/0015-constrained-elicitation-schema-builder.md) —
  flat-only schema builder.
- [ADR-0018](../adr/0018-mcp-spec-features-not-implemented.md) — URL-mode
  elicitation deliberately not implemented.
