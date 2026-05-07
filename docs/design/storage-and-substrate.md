# Storage & Substrate

Mocapi uses [Substrate](https://github.com/jwcarman/substrate) as its
sole storage abstraction. Substrate exposes four SPIs that mocapi
depends on; the choice of backend (Redis, PostgreSQL, Hazelcast,
DynamoDB, in-memory) is a pom-level decision with no code impact.

For the rationale, see
[ADR-0007](../adr/0007-substrate-storage-spi.md). For backend
selection how-to, see the [Backends guide](../guides/backends.md).

## Four SPIs, four roles

| Substrate SPI | Mocapi usage |
|---|---|
| `AtomFactory` | Session storage (`AtomMcpSessionStore`). |
| `MailboxFactory` | Request/response correlation for elicitation and sampling (see [Elicitation & Sampling](elicitation-and-sampling.md) and [ADR-0008](../adr/0008-mailbox-elicitation-sampling.md)). |
| `JournalFactory` | SSE event journaling for stream resumption (Odyssey, used by the Streamable HTTP transport). |
| `NotifierFactory` | Cross-node event notification. |

Mocapi knows nothing else about storage. There is no JDBC code in
`mocapi-server`, no Redis client in `mocapi-streamable-http-transport`.
A single Substrate-backend artifact on the classpath wires all four
SPIs.

## Session store as a thin facade

`McpSessionStore` is a thin facade over Substrate's `AtomFactory`.
On `initialize`, an Atom is created in the session store containing
the `McpSession` record; each subsequent request refreshes the
session's TTL. On HTTP `DELETE` (or stdin EOF for stdio) the Atom is
removed. There is no in-memory session cache layered on top — every
session lookup hits the configured backend, which keeps multi-node
behavior consistent (a session created on node A is immediately
visible to node B).

## Why pluggable from day one

Stateless workers + durable session state was a non-negotiable up
front: MCP elicitation and sampling block a handler thread waiting
for a client response that may arrive on a different node, which
forces the rendezvous primitive (Mailbox) to be cross-node-correct,
which forces session storage to be cross-node-correct too. Once that
constraint exists, encrypting at rest is a small additional step:
add `substrate-crypto` and Substrate wraps the configured factories
with AES-GCM transparently. Values are encrypted before they leave
the JVM and decrypted on read; backends never see plaintext.

That encryption is independent of the SSE event-ID encryption (see
[ADR-0005](../adr/0005-encrypted-sse-event-ids.md)) — different
threat models, different keys.

## Multi-node implications

With a clustered backend (Redis, PostgreSQL, Hazelcast, DynamoDB):

- Sessions are shared across nodes — a client can hit any node.
- SSE reconnection works across nodes via Journal replay.
- Elicitation/sampling correlation works across nodes via Mailbox.
- The session-encryption master key for SSE event IDs must be the same on all nodes.

The in-memory backend does not support multi-node and is intended for
development and single-node deployments only. Substrate logs a
warning when it falls back to in-memory.

## What is *not* in mocapi

Mocapi does not implement its own session store, mailbox, journal,
or pubsub. There is no `mocapi-redis-session-store` module — the
backend modules (`substrate-redis`, `substrate-jdbc`, etc.) live in
the Substrate project. Mocapi previously shipped backend-specific
starters; those were removed because they added no value beyond a
direct Substrate dependency.
