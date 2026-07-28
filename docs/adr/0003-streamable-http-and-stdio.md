# ADR-0003 — Ship two peer transports: Streamable HTTP and stdio

- **Status:** Accepted
- **Date:** 2025-07-09

> **Amended 2026-07-28 (ADR-0020):** the "two peer transports" decision
> stands — mocapi still ships `mocapi-streamable-http-transport` and
> `mocapi-stdio-transport` against a single server contract. But the
> stateless clean break invalidated the session-specific rules below:
> there is no `createContext` call (Rule 3), no shared `McpSessionStore`
> (Rule 4), no `emit(SessionInitialized)` on stdio, and stateless is now
> the *only* mode — the "does not implement a stateless serverless mode"
> non-goal is reversed. Corrections are marked inline.

## Context

MCP clients connect to servers in two fundamentally different ways:

- **Web-style:** the server is a long-running process listening on a
  network port; multiple clients connect concurrently; sessions may
  outlive a single TCP connection; deployments are clustered. The MCP
  spec calls this Streamable HTTP — POST for client→server messages, SSE
  for server→client streaming, GET for a long-lived notification channel.
- **Subprocess-style:** the client (Claude Desktop, Cursor, MCP Inspector)
  launches the server as a child process and communicates over stdin/stdout
  using newline-delimited JSON-RPC. There is exactly one implicit session
  per process; logs go to stderr; the server exits when stdin closes.

Both delivery models carry the same JSON-RPC payloads and exercise the
same MCP semantics. Tool authors should not have to know which transport
their server runs under.

## Decision

Mocapi ships two transport modules, both implementing the
`McpServer` / `McpTransport` contract from
[ADR-0002](0002-protocol-transport-contract.md):

- `mocapi-streamable-http-transport` — the MCP Streamable HTTP transport.
  Spring MVC controller, Odyssey-backed SSE streams, encrypted event IDs
  ([ADR-0005](0005-encrypted-sse-event-ids.md)), virtual-thread-per-call
  with context propagation ([ADR-0006](0006-virtual-thread-per-call.md)),
  and a lazy JSON-vs-SSE response writer
  ([ADR-0004](0004-lazy-json-vs-sse-state-machine.md)).
- `mocapi-stdio-transport` — newline-delimited JSON on stdin/stdout for
  subprocess-launched MCP clients.

**Rules for any transport:**

1. Tool, prompt, and resource code is identical across transports. A bean
   annotated `@ToolService` works in either.
2. The transport owns wire-level concerns only: framing, headers,
   content-type negotiation, origin validation, encryption of any tokens
   handed back to clients.
3. ~~The transport calls `server.createContext(sessionId, protocolVersion)`
   for every non-`initialize` message and maps the
   `McpContextResult` variants to its native error format
   ([ADR-0009](0009-mcpcontextresult-sealed-validation.md)).~~
   **(Amended, ADR-0020):** there is no `createContext`. Each transport
   simply forwards the `JsonRpcCall` to `server.handleCall(call, transport)`;
   the server parses the per-request `_meta` envelope into an immutable
   `McpExchange` and rejects an unsupported protocol version with
   `UnsupportedProtocolVersionError`.
4. ~~Both transports use the same `McpSessionStore` (Substrate Atom; see
   [ADR-0007](0007-substrate-storage-spi.md)). Stdio defaults to the
   in-memory backend, but nothing prevents a stdio server from persisting
   sessions to Redis or Postgres.~~
   **(Amended, ADR-0020):** there is no session store. Both transports
   are stateless; no per-client state is retained between calls and no
   Substrate backend is configured.

**Stdio specifics:**

- A single blocking reader loop on stdin dispatches each line on its own
  virtual thread via a try-with-resources `ExecutorService`. Per-message
  threads are required because handlers may block awaiting a client
  response (elicitation, sampling); a serial loop would deadlock on stdin.
- `StdioTransport.send` writes one JSON line to stdout. `PrintStream` is
  internally synchronized so concurrent dispatch threads cannot interleave
  partial lines.
- ~~`StdioTransport.emit(SessionInitialized)` stashes the session ID in a
  shared `AtomicReference` that the reader loop consults on subsequent
  dispatches — stdio has exactly one implicit session per process.~~
  **(Amended, ADR-0020):** there is no `emit`/`SessionInitialized` and no
  implicit session; stdio has a single `send(...)` method and holds no
  per-process session state.
- **Stdout is reserved for MCP traffic.** All logging must go to stderr.
  The stdio example ships a `logback-spring.xml` that routes the root
  logger to `System.err` and sets `spring.main.banner-mode=off`; a stray
  `System.out.println` corrupts the JSON stream.
- On EOF, the try-with-resources block closes the executor (shutdown +
  awaitTermination) so in-flight handlers finish before the JVM exits.

## Consequences

**Wins:**

- The same tool jar runs in a Claude Desktop subprocess and behind a
  load balancer with no source changes.
- Future transports (WebSocket, Unix socket) drop into the same contract
  without forcing changes to the server or to existing transports.

**Costs:**

- Two transports means two CI matrices. The HTTP transport has its own
  test suite, MockMvc-based integration tests, and the npx conformance
  harness; stdio has its own conformance run via the MCP Inspector.
- Stdio's stdout discipline is a footgun. The architecture doc and the
  stdio README call it out explicitly; the example's logback config is
  the canonical way to avoid corruption.

**Non-goals:** ~~mocapi does not implement a stateless serverless mode.
Both transports always create sessions. A stateless variant may follow
once the MCP spec stabilizes its sessionless guidance.~~
**(Reversed, ADR-0020):** stateless is now the *only* mode. Neither
transport creates sessions; every request is self-contained, and
serverless/scale-to-zero deployment is the natural shape.

**Code anchors:** `mocapi-streamable-http-transport/`, `mocapi-stdio-transport/`. Stdio transport added in 2026-04 alongside the protocol/transport split.
