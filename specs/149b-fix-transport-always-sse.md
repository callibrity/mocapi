# Fix transport: always-SSE + OdysseyTransport + virtual thread

## What to build

Fix the `StreamableHttpController` to use the always-SSE pattern.
The current controller uses a `BufferingTransport` that dispatches
synchronously, which deadlocks interactive tools (elicitation,
sampling) because the tool blocks waiting for a client response
but the HTTP response hasn't been sent yet.

### Transport type is determined by session ID presence

This is the fundamental rule the controller follows:

- **No `MCP-Session-Id` header** → `SynchronousTransport`.
  The only valid request without a session is `initialize`.
  Synchronous dispatch, JSON response, session ID returned
  in the `MCP-Session-Id` response header.

- **Has `MCP-Session-Id` header** → `OdysseyTransport`.
  Every post-initialize call gets an Odyssey-backed journal
  transport. Dispatched on a virtual thread. SseEmitter
  returned immediately. Always SSE — even for simple one-shot
  responses like `tools/list`.

The controller does NOT inspect the JSON-RPC method name, does
NOT check if the tool is "streamable," does NOT try to decide
JSON vs SSE based on the content. The session ID header is the
ONLY signal. No header → sync JSON. Has header → async SSE.

### The fix

Replace `BufferingTransport` with `OdysseyTransport` for all
post-initialize calls. Dispatch on a virtual thread. Return the
SseEmitter immediately.

#### Controller handleCall (exact implementation)

```java
private ResponseEntity<Object> handleCall(
    McpContext context, JsonRpcCall call, String sessionId) {

  // Initialize — no session, synchronous JSON response
  if (sessionId == null) {
    var transport = new SynchronousTransport();
    mcpServer.handleCall(context, call, transport);
    return transport.toResponseEntity();
  }

  // Everything else — always SSE, always async
  String streamName = UUID.randomUUID().toString();
  var publisher = odyssey.publisher(streamName, JsonRpcMessage.class);
  var transport = new OdysseyTransport(publisher);

  // Dispatch on virtual thread — returns immediately
  Thread.ofVirtual().start(
      () -> mcpServer.handleCall(context, call, transport));

  // Subscribe to the same stream — returns SseEmitter to client
  return ResponseEntity.ok().body(
      odyssey.subscribe(streamName, JsonRpcMessage.class,
          cfg -> cfg.mapper(encryptingMapper(sessionId))));
}
```

**Key**: `Thread.ofVirtual().start(...)` is in the CONTROLLER,
not the server. The server (`McpServer`) is purely synchronous
code. The transport decides the threading model.

#### Controller handleNotification and handleResponse

These are unchanged — no transport needed:

```java
case JsonRpcNotification n -> {
    mcpServer.handleNotification(context, n);
    yield ResponseEntity.accepted().build();
}
case JsonRpcResponse r -> {
    mcpServer.handleResponse(context, r);
    yield ResponseEntity.accepted().build();
}
```

### Delete BufferingTransport

Delete `BufferingTransport.java` entirely. It is replaced by
`OdysseyTransport`.

### OdysseyTransport auto-complete behavior

Verify that `OdysseyTransport.send()` auto-completes the
Odyssey publisher when it receives a `JsonRpcResponse` (result
or error). This is critical — it signals "stream done" to the
SSE subscriber. The implementation must be:

```java
@Override
public void send(JsonRpcMessage message) {
  if (completed) {
    throw new IllegalStateException("Transport completed");
  }
  publisher.publish(message);
  if (message instanceof JsonRpcResponse) {
    publisher.complete();
    completed = true;
  }
}
```

### Why this fixes the deadlock

1. Controller creates OdysseyTransport + SseEmitter, returns
   SseEmitter to client immediately.
2. Virtual thread runs `mcpServer.handleCall(...)`.
3. Tool calls `ctx.elicit(...)` which sends a `JsonRpcCall`
   via `transport.send()` → published to journal → delivered
   to client via SSE.
4. Client sees the elicitation request, POSTs a `JsonRpcResponse`
   back.
5. Controller calls `mcpServer.handleResponse(...)` → delivers
   to Mailbox.
6. Tool's `elicit()` unblocks, continues, returns result.
7. Server wraps result in `JsonRpcResult`, calls
   `transport.send()` → published to journal → delivered via
   SSE → publisher auto-completes → stream done.

No deadlock because the SseEmitter was sent at step 1.

### Always-SSE

Every POST with a session ID returns `text/event-stream`. Even
simple one-shot responses like `tools/list` are a single SSE
event followed by stream completion. The MCP Streamable HTTP spec
allows this — the server MAY return SSE for any response.

The only JSON response is `initialize` (no session ID), via
`SynchronousTransport`.

## Acceptance criteria

- [ ] `BufferingTransport` is deleted.
- [ ] `handleCall` with session ID uses `OdysseyTransport` +
      virtual thread + `odyssey.subscribe()`.
- [ ] All POST responses with session ID return SSE.
- [ ] Initialize (no session ID) remains synchronous JSON.
- [ ] `OdysseyTransport` auto-completes on `JsonRpcResponse`.
- [ ] Elicitation and sampling tests no longer deadlock.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
