# Wire McpProtocol into StreamableHttpController

## What to build

Rewrite `StreamableHttpController` to be a thin HTTP adapter that
delegates all protocol logic to `McpProtocol`. Delete the old
abstractions that the new architecture replaces.

### Controller rewrite

The controller becomes ~100 lines:

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

  // Client responses — deliver and return 202
  if (message instanceof JsonRpcResponse) {
    mcpProtocol.handle(new McpContext(sessionId, version), message, NO_OP_TRANSPORT);
    return ResponseEntity.accepted().build();
  }

  // Initialize — synchronous JSON response
  if (sessionId == null) {
    var transport = new SynchronousTransport();
    mcpProtocol.handle(new McpContext(null, version), message, transport);
    return transport.toResponseEntity();
  }

  // Everything else — always SSE
  var transport = createOdysseyTransport(sessionId);
  mcpProtocol.handle(new McpContext(sessionId, version), message, transport);
  return ResponseEntity.ok().body(
      odyssey.subscribe(transport.streamName(), JsonRpcMessage.class,
          cfg -> cfg.mapper(encryptingMapper(sessionId))));
}
```

### Always-SSE

Every POST with a session ID returns `text/event-stream`. Simple
one-shot responses (tools/list, resources/read) are a single SSE
event followed by auto-complete. Only `initialize` returns JSON.

### What gets deleted

- `DefaultMcpSessionStream` — replaced by `OdysseyTransport`.
- `McpSessionStream` interface — the transport replaces it.
- `McpToolMethods.SSE_EMITTER_KEY` — no more emitter smuggling.
- The `switch (message)` routing in the controller — the protocol
  handles it.
- Session validation logic in the controller — the protocol
  handles it.
- The `dispatch()` / `handleResult()` / `handleCall()` methods
  in the controller — absorbed by the protocol.

### What stays in the controller

- `handleGet()` — notification channel subscription (SSE).
- `handleDelete()` — session termination.
- Accept header validation.
- Origin validation.
- Encrypting SSE event mapper.
- `@ExceptionHandler` for malformed bodies.

## Acceptance criteria

- [ ] Controller delegates all POST handling to `McpProtocol`.
- [ ] No `switch` on `JsonRpcMessage` subtypes in the controller
      except the `instanceof JsonRpcResponse` check for 202.
- [ ] `DefaultMcpSessionStream` is deleted.
- [ ] `McpSessionStream` interface is deleted.
- [ ] `SSE_EMITTER_KEY` pattern is eliminated from `McpToolMethods`.
- [ ] Always-SSE: every POST with session ID returns SSE.
- [ ] Initialize (no session ID) returns JSON.
- [ ] Client responses return 202 Accepted.
- [ ] Controller tests rewritten to verify thin-adapter behavior.
- [ ] `mvn verify` passes across the full reactor.
- [ ] MCP conformance suite: 39/39 passing.

## Implementation notes

- **This is the "swap" commit** — the biggest risk spec in the
  sequence. The old controller logic is deleted and replaced by
  protocol delegation. If something breaks, the conformance suite
  catches it.
- **`McpSessionService` still depends on Odyssey** for now. The
  Odyssey dependency in `mocapi-protocol` persists until a future
  spec extracts the stream management concern. Not in scope here.
- **The `McpStreamContext` implementation** changes from holding
  a `McpSessionStream` to holding an `McpTransport`. Its public
  API (`sendProgress`, `log`, `elicit`, `sample`, `sendResult`)
  is unchanged — only the internal plumbing changes.
- **Thread model**: for non-streaming tools, `handle()` runs
  synchronously. For streaming tools, `handle()` starts a virtual
  thread and returns. The controller has already returned the
  SseEmitter in both cases.
