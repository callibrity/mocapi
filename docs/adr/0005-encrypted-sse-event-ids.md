# ADR-0005 — Encrypt SSE event IDs with AES-256-GCM bound to the session

- **Status:** Superseded by [ADR-0020](0020-stateless-request-model.md)
- **Date:** 2026-04-17

> **Superseded 2026-07-28 (ADR-0020):** the entire SSE-event-ID
> encryption subsystem was deleted. Both purposes it served are gone:
> `Last-Event-ID` resumption was removed when the 2026-07-28 transport
> dropped resumable SSE streams ([ADR-0020](0020-stateless-request-model.md),
> which also removed Odyssey and `DefaultSseStreamFactory`), and
> server-initiated-request correlation was removed when elicitation moved
> to the MRTR replay model ([ADR-0021](0021-mrtr-elicitation-replay.md)).
> The only SSE that remains is a per-POST response stream with no
> resumption and no server-initiated request IDs to encrypt. Note that
> `requestState` MRTR tokens are still AES-256-GCM protected — see
> [ADR-0021](0021-mrtr-elicitation-replay.md) — but that is a separate
> mechanism, not this event-ID codec. The Context/Decision text is
> preserved as the historical record.

## Context

The MCP Streamable HTTP spec uses SSE event IDs for two purposes:

1. **Resumption.** A client that drops its connection reconnects with
   `Last-Event-ID: <id>`. The server uses the ID to find the right
   journal position and replay missed events.
2. **JSON-RPC correlation.** Server-initiated requests (elicitation,
   sampling) carry an `id` that the client echoes in its response so
   the server can route the response back to the awaiting handler.

A naive implementation uses plaintext IDs — a stream name and a journal
offset, or a UUID, sent on the wire as-is. Two attacks follow:

- **Cross-session enumeration.** A client that knows or guesses another
  session's stream name and event ID can request resumption against it
  and pull events that belong to a different user.
- **Forged correlation.** A client can echo a fabricated request ID in a
  `JsonRpcResult` and try to slip a response into another session's
  awaiting Mailbox.

Plaintext IDs also leak internal layout (stream-naming convention,
journal offsets) that an attacker can use to fingerprint the deployment.

## Decision

Every SSE event ID handed to a client is encrypted with AES-256-GCM. The
session ID is fed in as Additional Authenticated Data (AAD) so a token
issued to session A fails authentication when presented under session B.

**Algorithm:**

- Cipher: `AES/GCM/NoPadding`, 128-bit auth tag.
- Master key: 32 bytes, configured via
  `mocapi.session-encryption-master-key`. Required in production.
- Per-call nonce: 12 bytes from `SecureRandom`.
- AAD: the session ID bytes (UTF-8). Decryption with a different session
  ID fails the GCM tag check; no decrypted bytes are returned.
- Wire format: Base64(`nonce || ciphertext || tag`).

**Scope:**

- Encryption is confined to `DefaultSseStreamFactory` (and the
  `Ciphers` helper it delegates to). The HTTP controller, the
  `StreamableHttpTransport`, and the server know nothing about cipher
  details. They see opaque strings.
- The same codec encrypts the `id` field of any server-initiated
  JSON-RPC request (elicitation/sampling), since clients must echo it
  on `Last-Event-ID`-style reconnection paths and on the response.

**Threat model boundaries:**

- This protects in-transit tokens handed to clients. It is **not**
  storage-at-rest encryption; sessions persisted to Redis / Postgres /
  DynamoDB / Hazelcast use a separate Substrate-level mechanism
  (`substrate-crypto`). The two keys are independent. See
  [ADR-0007](0007-substrate-storage-spi.md).
- The master key must be the same across every node in a clustered
  deployment, otherwise resumption tokens issued by one node fail on
  another. The `backends.md` doc calls this out.

## Consequences

**Wins:**

- A leaked or guessed event ID is useless against another session — GCM
  authentication fails, decode raises an exception, and the transport
  returns a 4xx without exposing internal layout.
- The transport-layer code stays simple: emit/consume opaque strings,
  let the codec handle correctness.
- Rotating the master key is straightforward — stand up new instances
  with the new key and let existing sessions expire naturally.

**Costs:**

- Every SSE event pays one AES-GCM encrypt + one Base64 encode (~tens of
  microseconds, negligible at MCP-call rates).
- The master key is mandatory operational state. Forgetting to set it
  in production is a deployment bug; the application logs a startup
  warning when the key is unset.
- Encrypted IDs are ~112 chars of Base64. SSE clients see opaque blobs
  rather than human-readable IDs — a tradeoff for the security
  properties.

**Non-goals:** the codec does not provide replay protection on its own.
A captured-and-replayed `Last-Event-ID` from the same session reconnects
to the same stream (which is the legitimate use case). Cross-session
replay is blocked by the AAD binding.

**Code anchors:** none — the `DefaultSseStreamFactory` (and the `Ciphers` helper) this decision described were deleted under [ADR-0020](0020-stateless-request-model.md). The encrypted-event-id refactor originally landed in commit `53d9cbc4` (2026-04-17).
