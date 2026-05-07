# Transports

Mocapi ships two transports today: Streamable HTTP for web-accessible
deployments and stdio for subprocess-launched MCP clients. Both
implement the same `McpServer` + `McpTransport` contract (see
[ADR-0002](../adr/0002-protocol-transport-contract.md)) — handler code
is identical between them.

For decisions specific to transport behavior, see also:

- [ADR-0003](../adr/0003-streamable-http-and-stdio.md) — two peer transports
- [ADR-0004](../adr/0004-lazy-json-vs-sse-state-machine.md) — `MessageWriter` state machine
- [ADR-0005](../adr/0005-encrypted-sse-event-ids.md) — encrypted event IDs
- [ADR-0006](../adr/0006-virtual-thread-per-call.md) — virtual-thread-per-call

## Picking a transport

| Transport | When to use | Module |
|-----------|-------------|--------|
| Streamable HTTP | Web-accessible servers, long-running deployments, multiple concurrent clients, sessions that survive restarts (with a Substrate backend like Redis/Postgres) | `mocapi-streamable-http-transport` (or `mocapi-streamable-http-spring-boot-starter`) |
| Stdio | Desktop MCP clients that spawn the server as a subprocess (Claude Desktop, Cursor, MCP Inspector), single-session per process, no network exposure | `mocapi-stdio-transport` (or `mocapi-stdio-spring-boot-starter`) |

## Streamable HTTP

### Lazy JSON-vs-SSE response shape

Every `JsonRpcCall` POST runs on a virtual thread through
`StreamableHttpTransport`, which chooses JSON vs SSE based on the
first outbound message:

1. Controller creates a `StreamableHttpTransport` with an `SseStream` supplier and spawns a virtual thread to run `server.handleCall()`.
2. The transport holds a `MessageWriter` state machine starting in `DirectMessageWriter`.
3. First `send()` decides the response shape:
   - `JsonRpcResponse` → commit as `application/json` body, transition to `ClosedMessageWriter`.
   - `JsonRpcRequest` (notification or server-initiated call) → pull an `SseStream` from the supplier, commit `text/event-stream` body, transition to `SseMessageWriter`.
4. Subsequent `send()` calls on `SseMessageWriter` publish to the stream; a terminal `JsonRpcResponse` closes the stream and transitions to `ClosedMessageWriter`.
5. `ClosedMessageWriter` rejects any further writes.

Simple tools that only return a response get JSON — no unnecessary SSE
upgrade. Tools that emit progress/log notifications or issue
sampling/elicitation requests upgrade lazily when the first
notification or server-initiated call is sent.

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

### Encrypted event IDs

SSE event IDs are encrypted using AES-256-GCM with the session ID as
context. This prevents cross-session event enumeration and forged
resumption requests. Encryption lives in `DefaultSseStreamFactory`;
the controller and transport have no direct knowledge of cipher
details. See [ADR-0005](../adr/0005-encrypted-sse-event-ids.md).

### GET SSE streams

Clients can open a GET connection to receive server-initiated messages
on a session-scoped stream
(`SseStreamFactory.sessionStream(context)`, named by session ID). A
`Last-Event-ID` header on reconnect routes through
`SseStreamFactory.resumeStream(context, lastEventId)`, which decodes
the stream name and event ID from the encrypted token. Stream
journaling is provided by Substrate's `JournalFactory` (see
[Storage & Substrate](storage-and-substrate.md)).

## Stdio

The MCP client launches the server as a subprocess and communicates
via newline-delimited JSON on stdin/stdout; stderr carries logs. When
the client closes stdin the server exits.

1. `StdioServer` reads lines from stdin in a single blocking loop.
2. Each line is dispatched on its own virtual thread via a try-with-resources `ExecutorService`. Per-message threads are required because handlers may block awaiting a client response (elicitation, sampling) — serial dispatch would deadlock on stdin.
3. `StdioTransport.send(message)` serializes to a single JSON line on stdout. `PrintStream` is internally synchronized, so concurrent dispatch threads can't interleave partial lines.
4. `StdioTransport.emit(SessionInitialized)` stashes the session ID in a shared `AtomicReference` that the reader loop consults on subsequent dispatches — stdio has exactly one implicit session per process.
5. On EOF, the try-with-resources block closes the executor (shutdown + awaitTermination) so in-flight handlers finish before the JVM exits.

Session storage uses the same `McpSessionStore` as HTTP — the
in-memory Substrate backend is the default, but nothing prevents using
Redis or Postgres for a stdio server that persists state across
restarts.

Stdout is reserved for MCP protocol traffic — **all logging must go to
stderr**. The stdio example ships a `logback-spring.xml` that wires
the root logger to `System.err` and sets
`spring.main.banner-mode=off` so nothing else touches stdout. A stray
`System.out.println` anywhere in your code (or a logger pointed at
stdout) will corrupt the JSON stream.
