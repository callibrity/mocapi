# Transports

Mocapi ships two transports today: Streamable HTTP for web-accessible
deployments and stdio for subprocess-launched MCP clients. Both
implement the same `McpServer` + `McpTransport` contract (see
[ADR-0002](../adr/0002-protocol-transport-contract.md)) — handler code
is identical between them. Under MCP 2026-07-28 both are stateless
([ADR-0019](../adr/0019-clean-break-2026-07-28.md),
[ADR-0020](../adr/0020-stateless-request-model.md)): every request is
self-contained, there is no handshake, no session, and no
server-initiated request channel.

For decisions specific to transport behavior, see also:

- [ADR-0003](../adr/0003-streamable-http-and-stdio.md) — two peer transports
- [ADR-0004](../adr/0004-lazy-json-vs-sse-state-machine.md) — `MessageWriter` state machine
- [ADR-0006](../adr/0006-virtual-thread-per-call.md) — virtual-thread-per-call
- [ADR-0020](../adr/0020-stateless-request-model.md) — sessions, resumability, Substrate, and Odyssey removed
- [ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md) — cancellation stance, `x-mcp-header` skipped

## Picking a transport

| Transport | When to use | Module |
|-----------|-------------|--------|
| Streamable HTTP | Web-accessible servers, long-running deployments, multiple concurrent clients, multi-node deployments (no shared state required) | `mocapi-streamable-http-transport` (or `mocapi-streamable-http-spring-boot-starter`) |
| Stdio | Desktop MCP clients that spawn the server as a subprocess (Claude Desktop, Cursor, MCP Inspector), one client per process, no network exposure | `mocapi-stdio-transport` (or `mocapi-stdio-spring-boot-starter`) |

## Streamable HTTP

### POST-only endpoint

The MCP endpoint accepts only POST. `GET` and `DELETE` return
`405 Method Not Allowed` (`Allow: POST`): the standalone GET SSE
stream and the DELETE session-termination request are gone with
sessions. An incoming `Mcp-Session-Id` header is ignored — never
minted, never echoed — and `Last-Event-ID` is ignored because the
2026-07-28 transport spec removed SSE resumability. The encrypted
event-ID machinery and the Odyssey-backed named/resumable streams
(`DefaultSseStream`/`DefaultSseStreamFactory`) were deleted with it;
[ADR-0005](../adr/0005-encrypted-sse-event-ids.md) is historical.

### Routing-header validation (`-32020 HeaderMismatch`)

The 2026-07-28 transport spec requires routing headers on every POST so
intermediaries can route without parsing the body. `McpHeaderValidator`
validates them against the body **before** dispatch:

- `MCP-Protocol-Version` — required on every request and notification;
  must equal the body's `_meta`
  `io.modelcontextprotocol/protocolVersion` when present.
- `Mcp-Method` — required on every request and notification; must
  equal the body's `method`.
- `Mcp-Name` — required on `tools/call` / `prompts/get` (must equal
  `params.name`) and `resources/read` (must equal `params.uri`); not
  expected on any other method.

Any missing/mismatched/malformed header → HTTP `400` + JSON-RPC error
`-32020` (`HeaderMismatch`). The constant lives in the transport module;
mocapi sources it from the transport spec (the value now also appears in
`schema.ts` as `HEADER_MISMATCH`). Body envelope semantics (`-32602`
invalid params, `-32022` unsupported protocol version) remain the server
core's job; a request that fails both header and envelope validation
fails with `-32020` (transport first). Unrecognized `Mcp-Param-*` headers
are ignored — mocapi
designates no custom parameter headers
([ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md)).

### HTTP status from JSON-RPC error codes

Direct JSON replies map their HTTP status from the JSON-RPC error code
in a single table (`HttpStatusMapping`):

| JSON-RPC code | HTTP status |
|---|---|
| `-32700`, `-32600`, `-32602`, `-32020`, `-32021`, `-32022` | `400 Bad Request` |
| `-32601` (method not found) | `404 Not Found` — distinguishes a modern server from a legacy HTTP+SSE endpoint |
| anything else (internal/application errors) | `200 OK` — the error is a well-formed JSON-RPC response |

Once the response has committed as SSE the status is already `200` and
errors travel on the stream.

### Lazy JSON-vs-SSE response shape

Every `JsonRpcCall` POST runs on a virtual thread through
`StreamableHttpTransport`, which chooses JSON vs SSE based on the
first outbound message (ADR-0004, unchanged):

1. Controller validates Accept, Origin, and routing headers, creates a `StreamableHttpTransport` backed by a per-request `PerRequestSseStream` supplier, and spawns a virtual thread to run `server.handleCall()`.
2. The transport holds a `MessageWriter` state machine starting in `DirectMessageWriter`.
3. First `send()` decides the response shape:
   - `JsonRpcResponse` → commit as `application/json` with the status from the mapping table, transition to `ClosedMessageWriter`.
   - `JsonRpcRequest` (a request-scoped notification such as `notifications/progress`) → commit `text/event-stream` with `X-Accel-Buffering: no`, transition to `SseMessageWriter`.
4. Subsequent `send()` calls on `SseMessageWriter` publish to the stream; the final `JsonRpcResponse` is written and then terminates the stream.
5. `ClosedMessageWriter` rejects any further writes.

Simple tools that only return a response get JSON — no unnecessary SSE
upgrade. Tools that emit progress notifications upgrade lazily. The
stream is a plain Spring `SseEmitter` scoped to the single POST: there
are no named streams, no journaling, and no replay.

Client disconnect of the response stream is cancellation: once the
emitter completes or errors, further writes become silent no-ops (the
server MUST NOT send more messages for that request). The in-flight
handler is not interrupted — its late output is simply dropped
([ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md)).

The stream is closed on every terminating path, never leaked: a
serialization failure for one message is logged and dropped without
tearing down the stream; writing the terminal response always closes it,
even if that write throws; and a handler exception thrown *after* the SSE
response has committed is caught by the controller and routed to
`StreamableHttpTransport.abort(...)`, which closes the committed stream
(before commit, `abort` fails the response future for a plain JSON
error). As a final backstop against a handler that hangs without ever
sending its terminal response, the emitter carries a configurable async
timeout (`mocapi.stream-timeout`, default 5 minutes) — there is no
resumability to recover, so an unbounded stream would otherwise hold the
connection forever.

Notifications POSTed by the client are dispatched and acknowledged with
`202 Accepted` and no body. A POSTed JSON-RPC *response* is rejected
with `-32600`: 2026-07-28 has no server-initiated requests, so clients
have nothing to respond to.

### Origin validation

`McpRequestValidator` checks the `Origin` header against the
allowed-origins list (DNS-rebinding protection); invalid origins get
`403 Forbidden`. Requests must accept both `application/json` and
`text/event-stream` or they are rejected with `406 Not Acceptable`.

### Thread-local context propagation

Virtual threads created via `Thread.ofVirtual().start(...)` do **not**
inherit `ThreadLocal` values from their parent, so request-thread
context (Spring Security's `SecurityContextHolder`, Micrometer
observation scope, SLF4J MDC, etc.) would otherwise vanish at the
handler spawn boundary. The controller captures an
`io.micrometer.context.ContextSnapshot` on the request thread and
wraps the handler `Runnable` via `snapshot.wrap(...)` before spawning
the VT. Every `ThreadLocalAccessor` registered via the
`context-propagation` SPI is restored on the handler VT at `run()`
entry and cleared on exit — Spring Security 6+ and Micrometer
Observation ship accessors out of the box, so authentication and
tracing parent linkage cross the spawn automatically. The
`ContextSnapshotFactory` bean is exposed by
`StreamableHttpAutoConfiguration` under `@ConditionalOnMissingBean`;
register custom `ThreadLocalAccessor`s via `ContextRegistry` to
propagate additional context objects.

## Stdio

The MCP client launches the server as a subprocess and communicates
via newline-delimited JSON on stdin/stdout; stderr carries logs. When
the client closes stdin the server exits.

1. `StdioServer` reads lines from stdin in a single blocking loop. Every line is an independent request or notification — there is no handshake gating, and `server/discover` (the back-compat probe) is answerable at any time, including as the first message.
2. Each line is dispatched on its own virtual thread via a try-with-resources `ExecutorService`, so a slow handler can't stall the reader.
3. `StdioTransport.send(message)` serializes to a single JSON line on stdout. `PrintStream` is internally synchronized, so concurrent dispatch threads can't interleave partial lines.
4. Envelope semantics live in the server core: a request without the `_meta` envelope gets the server's `-32602`, relayed verbatim to stdout. Client JSON-RPC responses are dropped with a warning — no server-initiated requests exist to correlate them to.
5. On EOF, the try-with-resources block closes the executor (shutdown + awaitTermination) so in-flight handlers finish before the JVM exits.

Stdout is reserved for MCP protocol traffic — **all logging must go to
stderr**. The stdio example ships a `logback-spring.xml` that wires
the root logger to `System.err` and sets
`spring.main.banner-mode=off` so nothing else touches stdout. A stray
`System.out.println` anywhere in your code (or a logger pointed at
stdout) will corrupt the JSON stream.
