# ADR-0008 — Substrate Mailbox is the cross-node rendezvous for elicitation and sampling

- **Status:** Superseded by [ADR-0021](0021-mrtr-elicitation-replay.md)
- **Date:** 2026-04-12

## Context

MCP defines two server-to-client request patterns:

- **Elicitation** (`elicitation/create`) — the server asks the user a
  structured question (a form schema) mid-tool-execution and waits for
  the user's response.
- **Sampling** (`sampling/createMessage`) — the server asks the client's
  LLM to complete a prompt and waits for the result.

In both cases a tool's execution thread issues a JSON-RPC request, then
**blocks** until the matching JSON-RPC response arrives. The response is
delivered as a separate inbound HTTP POST on the Streamable HTTP transport
(a stdin line for stdio). In a clustered deployment the response may land
on a different node from the one that issued the request — load balancers
do not pin clients to a node mid-session.

The handler's virtual thread therefore needs a rendezvous primitive that:

1. Lets one node park a thread on a key.
2. Lets a different node deliver a value to that key and unblock the
   waiter.
3. Has a TTL so an abandoned request eventually expires instead of
   leaking the thread forever.

This is exactly the contract of Substrate's `Mailbox`.

## Decision

Mocapi uses Substrate's `MailboxFactory` (one of the four storage SPIs;
see [ADR-0007](0007-substrate-storage-spi.md)) as the rendezvous for
every server-initiated request:

1. The handler's virtual thread, holding `McpToolContext.CURRENT`, calls
   `elicit(...)` or `sample(...)`.
2. The server allocates a JSON-RPC request id (encrypted via the same
   codec as SSE event IDs; see
   [ADR-0005](0005-encrypted-sse-event-ids.md)) and creates a Mailbox
   keyed by that id, typed as `JsonRpcResponse`.
3. The server sends the request via `transport.send(request)`. On the
   Streamable HTTP transport this either upgrades the response to SSE
   ([ADR-0004](0004-lazy-json-vs-sse-state-machine.md)) or publishes
   on the existing stream; on stdio it writes one line to stdout.
4. The handler thread blocks on `mailbox.poll(timeout)`.
5. When the client POSTs its response, the controller dispatches via
   `server.handleResponse(context, response)`. The server looks up the
   Mailbox by `response.id()` and calls `mailbox.deliver(response)`.
   This may happen on **any node** — Substrate's clustered backends
   deliver the value to the parked thread regardless of which node owns
   it.
6. The handler thread unblocks, parses the response, and returns the
   typed result to the tool.

**Rules:**

- Elicitation and sampling have separate, independently-configurable
  timeouts (`mocapi.elicitation-timeout`, `mocapi.sampling-timeout`).
  Sampling typically takes longer (LLM generation) and warrants a
  larger default.
- On timeout, the server sends a `notifications/cancelled` to the client
  (per spec) and throws a typed exception
  (`McpElicitationTimeoutException` / `McpSamplingTimeoutException`)
  into the tool's virtual thread.
- When `handleResponse` finds no awaiting Mailbox for the response id
  (the request already timed out or the id is bogus), the response is
  acknowledged with `202 Accepted` and dropped. This matches the spec's
  language that the server accepts the response.
- The transport never calls `transport.send(...)` for client responses —
  there is no outgoing message. The transport returns 202 directly. See
  [ADR-0002](0002-protocol-transport-contract.md).

## Consequences

**Wins:**

- Elicitation and sampling work transparently across nodes. A handler
  on node A waits on a Mailbox; a response arriving on node B wakes
  it up. No sticky sessions required.
- The pattern is symmetric — same Mailbox primitive, same id encoding,
  same timeout-and-cancel semantics — for both server-initiated request
  types. Future request patterns (if MCP adds any) drop into the same
  shape.
- The handler's thread model stays straightforward: a virtual thread
  blocks on a poll, the runtime parks it cheaply, the response wakes
  it. No reactive plumbing leaks into tool authoring code.

**Costs:**

- Backend choice matters. The in-memory Substrate backend ships
  Mailbox support but is single-node only — clustered MCP deployments
  must use a clustered backend
  ([ADR-0007](0007-substrate-storage-spi.md)).
- Tools that mis-configure their timeout get a hard failure on
  legitimate-but-slow responses. The two-timeout split lets sampling
  run longer than elicitation, which mostly mitigates this.
- A virtual thread parked for the elicitation/sampling timeout window
  retains its reachable state. Cheap by VT standards, but tools that
  fan out hundreds of concurrent elicitations are still bounded by VM
  memory.

**Non-goals:** mocapi does not implement URL-mode elicitation. It also
does not interrupt in-flight tools when the client sends
`notifications/cancelled` — the spec's "MAY ignore" language applies and
cooperative cancellation is left to a future spec.

**Code anchors:** `mocapi-server/.../McpResponseCorrelationService.java`; `mocapi.elicitation.timeout` and `mocapi.sampling.timeout` properties (`mocapi-server-defaults.properties`). Mailbox rendezvous landed in commit `1e3b27e2` (2026-04-12).
