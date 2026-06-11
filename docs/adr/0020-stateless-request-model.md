# ADR-0020 — Stateless request model; sessions removed

- **Status:** Accepted — supersedes [ADR-0007](0007-substrate-storage-spi.md) and [ADR-0009](0009-mcpcontextresult-sealed-validation.md)
- **Date:** 2026-06-11

## Context

MCP 2026-07-28 removes sessions from the protocol (SEP-2567) and the
`initialize` handshake with them (SEP-2575). There is no
`Mcp-Session-Id` header, no negotiated per-client state, and no
"initialized" lifecycle to enforce. Instead, every request is
self-contained: the client sends its protocol version, identity, and
capabilities in `_meta` on each call, and a mandatory `server/discover`
method lets clients learn the server's supported versions,
capabilities, and identity up front.

Mocapi's previous architecture was organized around the session.
[ADR-0009](0009-mcpcontextresult-sealed-validation.md) defined the
session lifecycle: `createContext(sessionId, protocolVersion)` returned
a sealed `McpContextResult` whose error variants (`SessionIdRequired`,
`SessionNotFound`, `ProtocolVersionMismatch`) transports mapped to
native errors, with a carve-out for the session-less `initialize`
bootstrap. [ADR-0007](0007-substrate-storage-spi.md) made session
storage durable and multi-node via Substrate's `AtomFactory`, alongside
Substrate's three other roles: Mailbox rendezvous for server-initiated
requests, journal-backed SSE resumability, and cross-node notification
fan-out. Under the clean break ([ADR-0019](0019-clean-break-2026-07-28.md)),
all of that machinery serves a protocol that no longer exists.

## Decision

Every request is self-contained; mocapi holds no per-client state
between calls. A per-request, immutable `McpExchange` replaces
`McpSession`.

**Rules:**

1. Protocol version, client info, and client capabilities arrive in
   `_meta` under the spec-defined keys
   `io.modelcontextprotocol/protocolVersion`,
   `io.modelcontextprotocol/clientInfo`, and
   `io.modelcontextprotocol/clientCapabilities`. The server parses them
   into an `McpExchange` at dispatch time; the exchange is immutable
   and scoped to the single request.
2. `server/discover` — mandatory in the spec — advertises the server's
   supported protocol versions, capabilities, and identity. It replaces
   handshake-time negotiation entirely.
3. A request carrying an unsupported protocol version is rejected with
   `UnsupportedProtocolVersionError`. There is no lenient default and
   no negotiation.
4. Sessions are deleted, not bypassed: `McpSession`, the session store,
   the `Mcp-Session-Id` header handling, session TTLs, and the
   `initialize` carve-out are all removed from the codebase.
5. Application state that must cross calls uses the spec's
   explicit-handle pattern: a tool returns an identifier (e.g.,
   `basket_id`) and the model passes it back as an argument on
   subsequent calls. This is a userland pattern documented in the
   guides — mocapi ships no framework machinery for it.
6. **Substrate is removed entirely.** Its three mocapi consumers are
   all obsolete in this revision: the session store dies with sessions;
   the Mailbox rendezvous dies with server-initiated requests
   ([ADR-0021](0021-mrtr-elicitation-replay.md)); and journal-backed
   SSE resumability dies with the
   [draft transport spec's](https://modelcontextprotocol.io/specification/draft/basic/transports/streamable-http)
   statement that "Resumable SSE streams via `Last-Event-ID` are not
   supported."

## Consequences

**What this buys us.** Multi-node deployment becomes trivial: any node
can serve any request with no shared store, no sticky sessions, and no
clustered backend to configure. The Substrate dependency — and the
entire "pick a backend" decision surface of ADR-0007 — disappears from
every pom and every deployment guide. The sealed session-validation
result of ADR-0009 collapses to a single error case
(`UnsupportedProtocolVersionError`), since there is no session to be
missing or expired. Serverless and scale-to-zero deployments, which the
stateful design explicitly could not serve (see the old ADR-0018's
"Stateless / Serverless Mode" entry), are now the natural shape.

**Costs.** Per-request `_meta` parsing replaces a one-time handshake —
a small, bounded cost on every call. Handlers can no longer stash state
on a session object; cross-call state is the application's problem, via
explicit handles backed by whatever store the application already has.
Capability gating (e.g., "does this client support elicitation?") reads
the per-request `McpExchange` rather than a negotiated session, so a
client that sends inconsistent capabilities across requests gets
inconsistent behavior — the spec accepts this.

**Non-goals.** Mocapi does not provide a session-emulation layer, a
server-side state store, or helpers for the explicit-handle pattern
beyond documentation. It also does not retain Substrate for "future
use" — if a future feature needs durable state, that is a new decision
with its own ADR.

This ADR supersedes [ADR-0007](0007-substrate-storage-spi.md)
(Substrate storage SPI and session store) and
[ADR-0009](0009-mcpcontextresult-sealed-validation.md) (session
lifecycle validation via `McpContextResult`).
