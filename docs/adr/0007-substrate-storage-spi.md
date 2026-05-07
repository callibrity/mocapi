# ADR-0007 — All durable state goes through Substrate's four SPIs; no per-backend mocapi modules

- **Status:** Accepted
- **Date:** 2026-04-11

## Context

A multi-node MCP deployment has four kinds of durable state:

1. **Sessions.** Each `McpSession` (session ID, protocol version, client
   capabilities, log level, initialized flag) must be readable by any
   node a client lands on, and must expire when idle.
2. **Pending elicitation/sampling rendezvous.** When a tool calls
   `elicit()` or `sample()`, the request goes out via the transport on
   one node; the client's response may arrive on a different node. The
   handler's virtual thread on the originating node must unblock when
   that response lands. See
   [ADR-0008](0008-mailbox-elicitation-sampling.md).
3. **SSE event journals.** A client that drops its connection reconnects
   with `Last-Event-ID` and expects to replay missed events. Across
   nodes, the journal must be shared.
4. **Cross-node notifications.** A `notifications/log` published on one
   node must reach a client connected to a GET stream on another.

An earlier mocapi design shipped a `McpSessionStore` interface and a
matrix of per-backend modules (`mocapi-session-store-redis`,
`-hazelcast`, `-jdbc`, `-cassandra`, `-mongodb`, `-dynamodb`, `-nats`),
each with its own auto-configuration, conditional ordering, and
connection bean wiring. Adding a backend meant a new mocapi module;
keeping all of them in lockstep meant duplicated effort across
specs 113, 114, 115, 130, 131, 134, 136, 146.

## Decision

Mocapi delegates every durable-state concern to Substrate's four SPIs and
ships no backend-specific modules of its own.

| Substrate SPI | Mocapi usage |
|---|---|
| `AtomFactory` | Session storage (`AtomMcpSessionStore`) |
| `MailboxFactory` | Elicitation / sampling rendezvous |
| `JournalFactory` | SSE event journaling for resumption |
| `NotifierFactory` | Cross-node fan-out for the GET notification channel |

**Rules:**

1. The backend is a pom-level decision. Adding `substrate-redis` /
   `substrate-jdbc` / `substrate-hazelcast` / `substrate-dynamodb` to
   the classpath causes Substrate to auto-configure the four SPIs on
   that backend. Mocapi never names the backend.
2. The default backend is in-memory (Substrate's fallback). It works for
   development and single-node deployments. The application logs a
   visible warning at startup when the in-memory fallback is in use, so
   nobody ships to production by accident (spec 052).
3. `McpSessionStore` is a thin facade over `Atom<McpSession>`. The
   adapter is roughly thirty lines: `find(id)` reads the atom, `save`
   writes it, `touch` refreshes the TTL on access, `delete` removes it.
   No per-backend code lives in mocapi.
4. Sessions carry a configurable TTL; reads refresh it. Backend choice
   determines persistence semantics — Postgres and DynamoDB persist
   across restarts; Redis can be configured either way; in-memory is
   process-local.
5. **Storage-at-rest encryption is a Substrate concern.** Adding
   `substrate-crypto` to the classpath wraps the configured
   `AtomFactory` / `MailboxFactory` / `JournalFactory` with AES-GCM
   encryption transparently — values are encrypted before they leave
   the JVM. This is a separate concern from the SSE event-ID encryption
   in [ADR-0005](0005-encrypted-sse-event-ids.md); the two keys are
   independent and serve different threat models. Production deployments
   should set both.
6. Mocapi publishes no backend-specific Spring Boot starters
   (`mocapi-redis-spring-boot-starter` etc.). Users wire the backend
   themselves: add `mocapi-spring-boot-starter`, the Substrate backend,
   and the appropriate Spring Boot data starter. The examples module
   shows the pattern for each backend.

## Consequences

**Wins:**

- Adding a new backend is a Substrate problem, not a mocapi problem.
  Substrate ships an SPI for it; mocapi inherits the support without
  any code change here.
- The reactor lost seven session-store modules and four backend
  starters during the migration. Maintenance burden dropped sharply.
- Multi-node correctness is built in: with a clustered Substrate
  backend, sessions, mailboxes, journals, and notifiers all work
  across nodes. No mocapi-specific clustering code.

**Costs:**

- Users now pick Substrate dependencies directly. The docs make this
  visible (`docs/backends.md`); the examples module carries one app per
  backend so the pom recipe is copy-pasteable.
- Mocapi cannot tune backend-specific behavior (Redis pipelining,
  Postgres connection pooling, DynamoDB read consistency) — that is
  Substrate's job. If a tuning knob is missing, the right answer is to
  push it into Substrate.
- A development server that forgets to add a Substrate backend silently
  uses in-memory storage and loses sessions across restarts. The
  startup warning is the mitigation.

**Non-goals:** mocapi does not abstract over Substrate. The
`McpSessionStore` facade exists because session lookup is a hot path
and a typed session API is more pleasant than raw atom reads; the
mailbox/journal/notifier APIs are used directly. There is no
"mocapi storage abstraction layer" sitting on top of Substrate.

**Code anchors:** `mocapi-server/.../AtomMcpSessionStore.java`. Substrate migration landed in commit `83bbdc74` (2026-04-11).
