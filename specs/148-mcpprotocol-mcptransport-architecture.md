# McpProtocol + McpTransport architecture

## What to build

Split mocapi-core into two modules that cleanly separate MCP
protocol logic from transport delivery:

- **`mocapi-protocol`** — transport-agnostic MCP protocol
  implementation. Owns session lifecycle, JSON-RPC dispatch,
  response correlation, tool/resource/prompt registries, and the
  `McpProtocol` / `McpTransport` / `McpContext` interfaces. **No
  Spring MVC, no HTTP, no SSE, no Odyssey dependency.** Pure
  protocol logic.

- **`mocapi-transport-streamable-http`** — the Spring MVC
  `StreamableHttpController` + `SynchronousTransport` +
  `OdysseyTransport` + encrypting SSE event mapper. Depends on
  `mocapi-protocol` + Spring WebMVC + Odyssey. This is the
  Streamable HTTP transport implementation from the MCP spec.

The current `mocapi-core` dissolves into these two modules.
`mocapi-spring-boot-starter` depends on both (giving users the
full stack via one dependency). Future transports
(`mocapi-transport-stdio`, `mocapi-transport-websocket`) depend
only on `mocapi-protocol` and provide their own delivery.

The current `StreamableHttpController` mixes HTTP transport
concerns (Accept headers, status codes, SSE emitter management)
with MCP protocol logic (session lifecycle, JSON-RPC dispatch,
response correlation). This spec extracts the protocol logic into
`McpProtocol` and introduces `McpTransport` as the abstraction
through which the protocol layer sends messages to clients —
without knowing whether the transport is HTTP/SSE, WebSocket,
stdio, or anything else.

### The four interfaces

These already exist in `com.callibrity.mocapi` (added by the
user):

```java
public interface McpTransport {
    void emit(McpLifecycleEvent event);
    void send(JsonRpcMessage message);
}

public interface McpProtocol {
    void handle(McpContext context, JsonRpcMessage message, McpTransport transport);
}

public interface McpContext {
    String sessionId();
    String protocolVersion();
}

public sealed interface McpLifecycleEvent {
    record SessionInitialized(String sessionId) implements McpLifecycleEvent {}
}
```

### How it works

The protocol layer receives an incoming `JsonRpcMessage`, processes
it (dispatch, session management, response correlation), and writes
outgoing messages to the `McpTransport` via `send()`. The protocol
signals lifecycle events via `emit()`. **The protocol layer never
returns a value** — all output flows through the transport.

The transport layer creates transport instances, passes them to
the protocol handler, and delivers messages to the client using
whatever wire mechanism it owns. It also reacts to lifecycle events
(e.g., capturing the session ID from `SessionInitialized` to set
an HTTP response header).

### Two transport implementations for Streamable HTTP

The `StreamableHttpController` creates one of two transport
implementations based on whether the incoming request has an
`MCP-Session-Id` header:

#### 1. `SynchronousTransport` — no session ID (initialize only)

The only valid request without a session ID is `initialize`.
This transport:

- **Buffers a single `JsonRpcResponse`** (result or error). If
  the protocol layer tries to `send()` anything other than a
  `JsonRpcResponse`, the transport throws — initialize does not
  support notifications or server-to-client requests.
- **Captures `SessionInitialized` events** from `emit()` to
  record the newly-created session ID.
- **Exposes `toResponseEntity()`** which the controller calls
  after `handle()` returns to build the HTTP response:
  `application/json` body + `MCP-Session-Id` header.
- **Synchronous** — `McpProtocol.handle()` runs to completion
  before the controller reads the buffered response and returns.

```java
class SynchronousTransport implements McpTransport {
  private JsonRpcResponse response;
  private String sessionId;

  @Override
  public void emit(McpLifecycleEvent event) {
    if (event instanceof McpLifecycleEvent.SessionInitialized init) {
      this.sessionId = init.sessionId();
    }
  }

  @Override
  public void send(JsonRpcMessage message) {
    if (!(message instanceof JsonRpcResponse resp)) {
      throw new IllegalStateException(
          "SynchronousTransport only accepts JsonRpcResponse, got " +
          message.getClass().getSimpleName());
    }
    this.response = resp;
  }

  public ResponseEntity<Object> toResponseEntity() {
    var builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON);
    if (sessionId != null) {
      builder.header("MCP-Session-Id", sessionId);
    }
    return builder.body(response);
  }
}
```

#### 2. `OdysseyTransport` — has session ID (everything else)

Every post-initialize request gets an Odyssey-backed transport.
This transport:

- **Publishes every message to an `OdysseyPublisher<JsonRpcMessage>`**
  backed by a substrate journal. Notifications (progress, logging),
  server-to-client requests (elicitation, sampling), and the final
  response all go through the same `publisher.publish()` call.
- **Auto-completes on `JsonRpcResponse`**: when `send()` receives
  a `JsonRpcResult` or `JsonRpcError`, it publishes the message
  and then calls `publisher.complete()`. No more messages after
  the final response — the stream is done. **The protocol layer
  does not need to explicitly close the transport.** Sending the
  result IS the close signal.
- **Ignores lifecycle events** — post-initialize requests don't
  generate session lifecycle events.
- **The controller returns immediately**: it creates the transport,
  kicks off `mcpProtocol.handle(context, message, transport)` (which
  may run asynchronously for streaming tools on a virtual thread),
  subscribes to the same Odyssey stream via
  `odyssey.subscribe(streamName, JsonRpcMessage.class, cfg -> cfg.mapper(encryptingMapper))`,
  and returns the `SseEmitter`. The protocol handler and the SSE
  delivery are decoupled by the journal.

```java
class OdysseyTransport implements McpTransport {
  private final OdysseyPublisher<JsonRpcMessage> publisher;

  OdysseyTransport(OdysseyPublisher<JsonRpcMessage> publisher) {
    this.publisher = publisher;
  }

  @Override
  public void emit(McpLifecycleEvent event) {
    // post-initialize — nothing to do
  }

  @Override
  public void send(JsonRpcMessage message) {
    publisher.publish(message);
    if (message instanceof JsonRpcResponse) {
      publisher.complete();
    }
  }

  public String streamName() {
    return publisher.name();
  }
}
```

#### Controller flow

```java
@PostMapping
public ResponseEntity<Object> handlePost(
    @RequestBody JsonRpcMessage message,
    @RequestHeader(value = "MCP-Session-Id", required = false) String sessionId,
    @RequestHeader(value = "MCP-Protocol-Version", required = false) String protocolVersion,
    @RequestHeader(value = "Accept", required = false) String accept,
    @RequestHeader(value = "Origin", required = false) String origin) {

  if (!acceptsJsonAndSse(accept)) return notAcceptable();
  if (!validator.isValidOrigin(origin)) return forbidden();

  String version = protocolVersion != null
      ? protocolVersion : InitializeResult.PROTOCOL_VERSION;

  if (sessionId == null) {
    // Initialize — synchronous, JSON response
    var transport = new SynchronousTransport();
    mcpProtocol.handle(new McpContext(null, version), message, transport);
    return transport.toResponseEntity();
  }

  // Everything else — Odyssey-backed SSE
  var odysseyTransport = createOdysseyTransport(sessionId);
  mcpProtocol.handle(new McpContext(sessionId, version), message, odysseyTransport);
  return ResponseEntity.ok().body(
      odyssey.subscribe(odysseyTransport.streamName(), JsonRpcMessage.class,
          cfg -> cfg.mapper(encryptingMapper(sessionId))));
}
```

### McpProtocol implementation

The `McpProtocol` implementation (call it `DefaultMcpProtocol` or
`McpServer`) encapsulates everything the controller currently does
beyond HTTP:

#### Session enforcement

The protocol is inherently session-based. **Every message except
`initialize` requires a session.** This is enforced at the
protocol level, not the transport level — transports don't know
about sessions, they just pass through whatever `McpContext` they
build from their wire format (HTTP header, JSON field in a
WebSocket frame, etc.).

```java
@Override
public void handle(McpContext context, JsonRpcMessage message, McpTransport transport) {
  // Session enforcement — the protocol's job, not the transport's
  if (context.sessionId() == null) {
    if (message instanceof JsonRpcCall call && "initialize".equals(call.method())) {
      handleInitialize(context, call, transport);
      return;
    }
    transport.send(new JsonRpcError(-32600, "Session required", extractId(message)));
    return;
  }

  // Validate session exists
  var session = sessionService.find(context.sessionId());
  if (session.isEmpty()) {
    transport.send(new JsonRpcError(-32600, "Session not found or expired", extractId(message)));
    return;
  }

  // Route by message type
  switch (message) {
    case JsonRpcCall call -> handleCall(context, session.get(), call, transport);
    case JsonRpcNotification notification -> handleNotification(context, notification);
    case JsonRpcResponse response -> deliverClientResponse(response);
  }
}
```

This means:
- The HTTP controller doesn't validate session IDs — the protocol does.
- A future stdio transport doesn't validate session IDs — the protocol does.
- A future WebSocket transport doesn't validate session IDs — the protocol does.
- Session enforcement lives in **one place** for all transports.

#### Message routing

- **`JsonRpcCall`** — dispatches to the JSON-RPC method registry
  (tools/call, resources/read, prompts/get, etc.).
  The result (or error) is sent via `transport.send(response)`.
  For streaming tools, the tool runs on a virtual thread and
  sends notifications + the final result via the transport.
- **`JsonRpcNotification`** — handles client-to-server notifications
  (e.g., `notifications/initialized`). No response needed.
- **`JsonRpcResponse`** — this is a client response to a
  server-initiated request (elicitation result, sampling result).
  Delivers to the correlation service (Mailbox-based). The
  protocol does NOT call `transport.send()` for these — there is
  no outgoing message. The controller returns `202 Accepted`
  independently.

**Important nuance for client responses**: When the client POSTs
a `JsonRpcResult` or `JsonRpcError` (responding to an elicitation
or sampling request), the controller should return `202 Accepted`
immediately. The protocol layer delivers the response to the
waiting Mailbox internally. The transport is not used for output
in this case. The controller can detect `message instanceof
JsonRpcResponse` and handle it separately:

```java
if (message instanceof JsonRpcResponse response) {
  mcpProtocol.handle(new McpContext(sessionId, version), message, NO_OP_TRANSPORT);
  return ResponseEntity.accepted().build();
}
```

Or the protocol's `handle()` simply doesn't call `transport.send()`
for response messages — it delivers internally and returns.

#### Session management

The protocol implementation owns:
- **`initialize`** — creates a session in the `McpSessionStore`,
  emits `SessionInitialized(sessionId)` via the transport, sends
  the `JsonRpcResult` with capabilities/server info.
- **Session lookup** — validates `context.sessionId()` maps to a
  live session. If not, sends a `JsonRpcError` via the transport.
- **Protocol version negotiation** — validates
  `context.protocolVersion()`.
- **Session termination** — the `DELETE` endpoint still lives in
  the controller (it's an HTTP verb concern), but delegates to
  `mcpProtocol.terminate(sessionId)` which cleans up the session
  store + notification channel.

#### Response correlation service

The protocol implementation owns the Mailbox-based correlation
for server-to-client request-response patterns:

- **Sending a server request** (elicitation, sampling): creates
  a Mailbox, sends the request via `transport.send(JsonRpcCall)`,
  blocks on the Mailbox subscription for the client's response.
- **Delivering a client response**: when `handle()` receives a
  `JsonRpcResponse`, it looks up the Mailbox by `response.id()`
  and delivers.
- **Timeout/expiration**: the Mailbox handles TTL; the tool gets
  an exception if the client doesn't respond in time.

This is the same Mailbox pattern as today, but encapsulated inside
the protocol implementation rather than split between
`DefaultMcpStreamContext` and `StreamableHttpController`.

#### Tool stream context

The protocol creates the `McpStreamContext` for streaming tools
and gives it a reference to the transport. The stream context
uses the transport for:

- **Progress notifications**: `transport.send(progressNotification)`
- **Logging notifications**: `transport.send(logNotification)`
- **Elicitation requests**: `transport.send(elicitCall)` + wait
  on Mailbox
- **Sampling requests**: `transport.send(sampleCall)` + wait on
  Mailbox
- **Final result**: `transport.send(resultResponse)` — triggers
  auto-complete on the Odyssey transport

### GET endpoint (notification channel)

The `GET /mcp` endpoint is separate from the `McpProtocol.handle()`
flow — it's a long-lived SSE subscription to the session's
notification channel. The controller handles it directly:

```java
@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public ResponseEntity<Object> handleGet(
    @RequestHeader("MCP-Session-Id") String sessionId,
    @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId, ...) {

  if (!validator.isValidOrigin(origin)) return forbidden();
  if (sessionId == null) return badRequest();
  if (sessionService.find(sessionId).isEmpty()) return notFound();

  SseEmitter emitter = lastEventId != null
      ? odyssey.resume(sessionId, JsonRpcMessage.class, lastEventId,
            cfg -> cfg.mapper(encryptingMapper(sessionId)))
      : odyssey.subscribe(sessionId, JsonRpcMessage.class,
            cfg -> cfg.mapper(encryptingMapper(sessionId)));

  return ResponseEntity.ok().body(emitter);
}
```

The protocol layer publishes to the session's notification channel
(named by session ID) when resource subscriptions fire, logging
level changes, etc. The controller subscribes to the same channel
and delivers via SSE. Same Odyssey pub/sub pattern as the POST
response streams.

### DELETE endpoint

```java
@DeleteMapping
public ResponseEntity<Object> handleDelete(
    @RequestHeader("MCP-Session-Id") String sessionId, ...) {

  if (!validator.isValidOrigin(origin)) return forbidden();
  if (sessionId == null) return badRequest();
  if (sessionService.find(sessionId).isEmpty()) return notFound();

  mcpProtocol.terminate(sessionId);
  return ResponseEntity.noContent().build();
}
```

### What gets deleted / replaced

- **`StreamableHttpController`** — rewritten from ~300 lines of
  mixed protocol/transport logic to a thin HTTP adapter (~100
  lines) that creates transports and delegates to `McpProtocol`.
- **`DefaultMcpSessionStream`** — replaced by `OdysseyTransport`.
  The transport IS the stream. No separate stream abstraction
  needed.
- **`McpSessionStream` interface** — deleted. The transport
  replaces it.
- **`McpToolMethods.SSE_EMITTER_KEY` metadata hack** — deleted.
  The emitter is never smuggled through a `JsonRpcResult`'s
  metadata. The controller creates the subscription directly.
- **`DefaultMcpStreamContext`'s direct stream dependency** — the
  stream context receives an `McpTransport` instead of a
  `McpSessionStream`. It calls `transport.send()` for
  notifications and results.
- **`McpToolMethods.callTool` streaming path** — simplified. The
  streaming tool distinction (virtual thread + emitter return)
  moves into the protocol implementation. The protocol decides
  whether to run the tool synchronously or on a virtual thread
  based on `McpTool.isStreamable()`. Either way, the result goes
  through `transport.send()`.

### What stays the same

- **`McpSessionStore` / `SubstrateAtomMcpSessionStore`** — session
  persistence is unchanged.
- **`McpSessionService`** — session lifecycle (create, find,
  delete, encrypt/decrypt) stays. The protocol implementation
  delegates to it.
- **`ToolsRegistry`, `ResourcesRegistry`, `PromptsRegistry`** —
  the capability registries are unchanged. The protocol
  implementation uses them for dispatch.
- **Ripcurl `JsonRpcDispatcher`** — still handles method-level
  dispatch for calls and notifications. The protocol creates it
  and delegates.
- **`MailboxFactory`** — still used for elicitation/sampling
  correlation. Stays inside the protocol implementation.
- **Odyssey** — still provides the journal-backed streaming. The
  `OdysseyTransport` wraps `OdysseyPublisher<JsonRpcMessage>`;
  the controller subscribes via `Odyssey.subscribe()`.
- **The encrypting `SseEventMapper`** — stays in the controller /
  transport layer. The mapper encrypts event IDs with session-bound
  keys for SSE delivery. This is a transport concern (wire-level
  event ID format).

### Always-SSE for POST responses

Post-initialize, **every** POST response is delivered via SSE.
Even simple one-shot responses (tools/list, resources/read,
prompts/get) are published to a short-lived Odyssey stream, delivered
as a single SSE event, and the stream auto-completes when the
`JsonRpcResponse` is sent.

This eliminates the JSON-vs-SSE branching in the controller. The
MCP Streamable HTTP spec allows this — the server MAY return
`text/event-stream` for any response as long as the client accepts
it (and MCP clients MUST accept both).

The only JSON response is `initialize` (via `SynchronousTransport`),
which is the one request that doesn't have a session and thus has
no Odyssey stream.

### Typed Odyssey publishers

The Odyssey publisher and subscriber are typed as
`OdysseyPublisher<JsonRpcMessage>` / `odyssey.subscribe(name, JsonRpcMessage.class, ...)`.
Odyssey's codec serializes `JsonRpcMessage` to bytes via Jackson.
The subscriber's mapper receives `DeliveredEvent<JsonRpcMessage>`
with a fully-typed message. mocapi no longer manually converts to
`JsonNode` — the type system carries the message end-to-end.

### McpContext extensions

`McpContext` currently has `sessionId()` and `protocolVersion()`.
The protocol implementation may need to extend it with:

- **`McpSession session()`** — the full session record (client
  capabilities, log level, etc.) looked up from the store. Avoids
  repeated `sessionService.find()` calls inside the protocol.
- This can be a future enhancement — start minimal, add as needed.

## Acceptance criteria

### Interfaces

- [ ] `McpTransport`, `McpProtocol`, `McpContext`, and
      `McpLifecycleEvent` exist in `com.callibrity.mocapi`.
- [ ] `McpProtocol` has a single `handle(McpContext, JsonRpcMessage,
      McpTransport)` method.
- [ ] `McpTransport` has `emit(McpLifecycleEvent)` and
      `send(JsonRpcMessage)`.
- [ ] `McpLifecycleEvent` is sealed with at least
      `SessionInitialized(String sessionId)`.

### Transport implementations

- [ ] `SynchronousTransport` buffers a single `JsonRpcResponse`,
      captures `SessionInitialized`, and exposes
      `toResponseEntity()`.
- [ ] `SynchronousTransport.send()` throws on non-`JsonRpcResponse`
      messages.
- [ ] `OdysseyTransport` wraps an `OdysseyPublisher<JsonRpcMessage>`
      and publishes every message via `publisher.publish()`.
- [ ] `OdysseyTransport` auto-completes the publisher when
      `send()` receives a `JsonRpcResponse`.
- [ ] `OdysseyTransport` exposes `streamName()` for the controller
      to subscribe.

### Protocol implementation

- [ ] `DefaultMcpProtocol` (or equivalent) implements `McpProtocol`.
- [ ] Routes `JsonRpcCall` to the `JsonRpcDispatcher`.
- [ ] Routes `JsonRpcNotification` to the notification handler.
- [ ] Routes `JsonRpcResponse` to the response correlation service
      (Mailbox delivery). Does NOT call `transport.send()` for
      client responses.
- [ ] Handles `initialize` — creates session, emits
      `SessionInitialized`, sends the result via transport.
- [ ] Validates session existence for non-initialize calls.
- [ ] For streaming tools: starts the tool on a virtual thread,
      provides an `McpStreamContext` that uses the transport for
      notifications/requests/results.
- [ ] For non-streaming tools: runs synchronously, sends the
      result via transport.
- [ ] Exposes `terminate(sessionId)` for the DELETE endpoint.

### Controller simplification

- [ ] `StreamableHttpController.handlePost()` creates either
      `SynchronousTransport` (no session ID) or `OdysseyTransport`
      (has session ID) and delegates to `McpProtocol.handle()`.
- [ ] No `switch` on `JsonRpcMessage` subtypes in the controller —
      the protocol handles routing.
- [ ] Exception: `JsonRpcResponse` from client MAY be detected in
      the controller to return `202 Accepted` directly, OR the
      protocol returns without calling `transport.send()` and the
      controller returns `202` for the no-output case.
- [ ] `SSE_EMITTER_KEY` metadata pattern is eliminated.
- [ ] `DefaultMcpSessionStream` and `McpSessionStream` are deleted.
- [ ] The encrypting `SseEventMapper` stays in the controller /
      transport layer.

### Always-SSE

- [ ] Every POST with a session ID returns `text/event-stream`.
- [ ] Even simple one-shot responses (tools/list, resources/read)
      are delivered as a single SSE event followed by stream
      completion.
- [ ] Only `initialize` (no session ID) returns
      `application/json`.

### Tests

The protocol module is testable in complete isolation — no HTTP,
no Spring MVC, no MockMvc, no Tomcat. Tests create a simple
in-memory `McpTransport` (a `List<JsonRpcMessage>` + a
`List<McpLifecycleEvent>`), call `mcpProtocol.handle()`, and
assert on what was sent/emitted. This is a massive improvement
over the current test suite which routes everything through
MockMvc and can't distinguish protocol bugs from transport bugs.

```java
// Example protocol test — zero HTTP
var transport = new CapturingTransport();  // captures sent messages + events
var protocol = new DefaultMcpProtocol(dispatcher, sessionService, ...);

protocol.handle(new McpContext(null, "2025-11-25"), initializeCall, transport);

assertThat(transport.events()).hasSize(1);
assertThat(transport.events().getFirst()).isInstanceOf(SessionInitialized.class);
assertThat(transport.messages()).hasSize(1);
assertThat(transport.messages().getFirst()).isInstanceOf(JsonRpcResult.class);
```

- [ ] `SynchronousTransport` unit tests:
  - Buffers a `JsonRpcResult`, exposes it via `toResponseEntity()`.
  - Buffers a `JsonRpcError`, exposes it via `toResponseEntity()`.
  - Throws on `send(JsonRpcNotification)`.
  - Throws on `send(JsonRpcCall)`.
  - Captures `SessionInitialized` event, includes session ID in
    response header.
- [ ] `OdysseyTransport` unit tests:
  - Publishes notifications via the Odyssey publisher.
  - Publishes server-to-client requests via the publisher.
  - Publishes a `JsonRpcResult` and auto-completes the publisher.
  - Publishes a `JsonRpcError` and auto-completes the publisher.
  - Rejects `send()` after auto-complete.
- [ ] `DefaultMcpProtocol` unit tests:
  - Initialize flow: creates session, emits event, sends result.
  - Call dispatch: delegates to `JsonRpcDispatcher`, sends result
    via transport.
  - Client response delivery: delivers to Mailbox, does NOT call
    transport.send().
  - Session validation: sends error via transport for missing/
    expired sessions.
  - Streaming tool: runs on virtual thread, notifications +
    result go through transport.
- [ ] Controller tests (rewritten to be thin):
  - No session ID → uses `SynchronousTransport`, returns JSON.
  - Has session ID → uses `OdysseyTransport`, returns SSE emitter.
  - Invalid Accept header → 406.
  - Invalid origin → 403.
  - DELETE → delegates to `mcpProtocol.terminate()`.
  - GET → subscribes to notification channel.
- [ ] `mvn verify` green across the full reactor.
- [ ] MCP conformance suite: 39/39 passing.

## Implementation notes

- **Commit granularity**:
  1. Add `SynchronousTransport` + `OdysseyTransport` implementations
     with unit tests.
  2. Add `DefaultMcpProtocol` implementation + unit tests. Coexists
     with the old controller — not wired in yet.
  3. Rewrite `StreamableHttpController` to use `McpProtocol` +
     transports. Delete `DefaultMcpSessionStream`,
     `McpSessionStream`, `SSE_EMITTER_KEY` pattern.
  4. Update controller tests to verify thin-adapter behavior.
  5. Run conformance suite, fix any regressions.

- **The protocol implementation absorbs multiple existing classes**:
  - `McpSessionMethods` (initialize handling)
  - `McpToolMethods` (tool dispatch + streaming)
  - `McpLoggingMethods` (logging/setLevel)
  - `McpCompletionMethods` (completion/complete)
  - Response correlation from `DefaultMcpStreamContext`
  - Session validation from `StreamableHttpController`

  These can either be composed as delegates inside
  `DefaultMcpProtocol` or absorbed directly. Composition is
  cleaner — keeps each concern in its own class but orchestrated
  by the protocol.

- **`McpStreamContext` still exists** as the tool-author-facing
  API for streaming tools. Its implementation changes from holding
  a `McpSessionStream` to holding an `McpTransport`. The public
  API (`sendProgress`, `log`, `elicit`, `sample`, `sendResult`)
  stays the same — only the internals change.

- **The GET notification channel** is technically a second
  `OdysseyTransport` for the session, but it's long-lived (not
  per-request). The protocol publishes to it for resource change
  notifications, etc. The controller subscribes to it on the GET
  endpoint. This reuses the same Odyssey pub/sub pattern.

- **Client response routing**: when a client POSTs a
  `JsonRpcResult` or `JsonRpcError` (responding to an elicitation
  or sampling request), the controller has two options:
  1. Detect `message instanceof JsonRpcResponse` before calling
     `mcpProtocol.handle()` and return `202 Accepted` directly
     after the protocol processes the delivery.
  2. Let `mcpProtocol.handle()` process it with a no-op transport
     (or the SynchronousTransport, which would just buffer nothing
     since the protocol doesn't send anything back for client
     responses).
  Option 1 is simpler.

- **Thread model**: for non-streaming tools, `handle()` runs
  synchronously on the Tomcat thread. The tool executes, the
  result is published to the journal, `handle()` returns. For
  streaming tools, `handle()` starts a virtual thread for the
  tool and returns immediately — the virtual thread publishes
  notifications + the result to the journal asynchronously. The
  controller has already returned the SseEmitter in both cases
  (for the Odyssey path).

- **Error handling**: if `handle()` throws an unexpected exception,
  the protocol should catch it and send a `JsonRpcError` via the
  transport before returning. This ensures the client always gets
  a well-formed JSON-RPC error even for unhandled server failures.

### Module structure

```
mocapi-protocol/
├── pom.xml   (depends on: ripcurl-core, substrate-api, mocapi-model)
└── src/main/java/com/callibrity/mocapi/
    ├── McpProtocol.java            (interface)
    ├── McpTransport.java           (interface)
    ├── McpContext.java             (interface)
    ├── McpLifecycleEvent.java      (sealed interface)
    ├── DefaultMcpProtocol.java     (implementation)
    ├── session/                    (McpSessionStore, SubstrateAtomMcpSessionStore, ...)
    ├── tools/                      (ToolsRegistry, McpTool, AnnotationMcpTool, ...)
    ├── resources/                  (ResourcesRegistry, ...)
    ├── prompts/                    (PromptsRegistry, ...)
    ├── stream/                     (McpStreamContext, DefaultMcpStreamContext, ...)
    └── server/                     (McpCompletionMethods, ...)

mocapi-transport-streamable-http/
├── pom.xml   (depends on: mocapi-protocol, spring-boot-starter-webmvc, odyssey)
└── src/main/java/com/callibrity/mocapi/http/
    ├── StreamableHttpController.java    (thin HTTP adapter)
    ├── SynchronousTransport.java        (initialize-only, JSON response)
    ├── OdysseyTransport.java            (always-SSE, journal-backed)
    └── McpRequestValidator.java         (origin/Accept validation)
```

`mocapi-spring-boot-starter` depends on both modules + auto-config.
Users add one dependency and get the full Streamable HTTP server.

`mocapi-protocol` has **zero** Spring MVC or Odyssey dependencies.
It depends on ripcurl-core (JSON-RPC), substrate-api (Mailbox,
Atom), and mocapi-model. A stdio or WebSocket transport would
depend on `mocapi-protocol` only and provide its own delivery.

## Follow-up work (not in scope)

- **`mocapi-transport-stdio`** — for CLI-based MCP servers. Same
  `McpProtocol`, reads/writes JSON-RPC lines on stdin/stdout.
  Depends only on `mocapi-protocol`. (Yes, it's idiotic. But
  it's possible.)
- **`mocapi-transport-websocket`** — if/when MCP adds a WebSocket
  transport spec. Same `McpProtocol`, sends JSON-RPC as WebSocket
  text frames.
- **`McpContext` enrichment** — add the full `McpSession` to the
  context so the protocol doesn't need to call
  `sessionService.find()` repeatedly.
- **Response correlation service extraction** — if the Mailbox
  correlation pattern grows, extract it into a dedicated
  `ResponseCorrelationService` that the protocol delegates to.
- **McpServer facade** — potentially wrap `McpProtocol` +
  `McpSessionService` + registries into a single `McpServer`
  facade for users who want to embed mocapi without Spring Boot.
