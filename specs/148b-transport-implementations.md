# Transport implementations: SynchronousTransport + OdysseyTransport

## What to build

Create the two `McpTransport` implementations that the
`StreamableHttpController` will use (in spec 148d). These are
**new additive classes** — they don't replace anything yet. The
old controller continues to work unchanged.

### `SynchronousTransport`

Lives in `mocapi-transport-streamable-http`. Used for `initialize`
requests (no session ID). Buffers a single `JsonRpcResponse` and
captures `SessionInitialized` lifecycle events.

```java
public class SynchronousTransport implements McpTransport {
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
          "SynchronousTransport only accepts JsonRpcResponse");
    }
    if (this.response != null) {
      throw new IllegalStateException(
          "SynchronousTransport already has a response");
    }
    this.response = resp;
  }

  public ResponseEntity<Object> toResponseEntity() {
    var builder = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON);
    if (sessionId != null) {
      builder.header("MCP-Session-Id", sessionId);
    }
    return builder.body(response);
  }
}
```

### `OdysseyTransport`

Lives in `mocapi-transport-streamable-http`. Used for all
post-initialize requests. Wraps an
`OdysseyPublisher<JsonRpcMessage>` and auto-completes when a
`JsonRpcResponse` is sent.

```java
public class OdysseyTransport implements McpTransport {
  private final OdysseyPublisher<JsonRpcMessage> publisher;
  private boolean completed = false;

  public OdysseyTransport(OdysseyPublisher<JsonRpcMessage> publisher) {
    this.publisher = publisher;
  }

  @Override
  public void emit(McpLifecycleEvent event) {
    // post-initialize — nothing to do
  }

  @Override
  public void send(JsonRpcMessage message) {
    if (completed) {
      throw new IllegalStateException(
          "OdysseyTransport is completed — cannot send after JsonRpcResponse");
    }
    publisher.publish(message);
    if (message instanceof JsonRpcResponse) {
      publisher.complete();
      completed = true;
    }
  }

  public String streamName() {
    return publisher.name();
  }
}
```

## Acceptance criteria

- [ ] `SynchronousTransport` exists in
      `mocapi-transport-streamable-http`.
- [ ] `SynchronousTransport` unit tests:
  - Buffers a `JsonRpcResult`, exposes via `toResponseEntity()`.
  - Buffers a `JsonRpcError`, exposes via `toResponseEntity()`.
  - Throws on `send(JsonRpcNotification)`.
  - Throws on `send(JsonRpcCall)`.
  - Throws on second `send(JsonRpcResponse)`.
  - Captures `SessionInitialized`, includes session ID in header.
  - `toResponseEntity()` without session ID omits the header.
- [ ] `OdysseyTransport` exists in
      `mocapi-transport-streamable-http`.
- [ ] `OdysseyTransport` unit tests:
  - Publishes a `JsonRpcNotification` via the publisher.
  - Publishes a `JsonRpcCall` (server-to-client request) via
    the publisher.
  - Publishes a `JsonRpcResult` and auto-completes the publisher.
  - Publishes a `JsonRpcError` and auto-completes the publisher.
  - Throws on `send()` after auto-complete.
  - `streamName()` returns the publisher's name.
- [ ] Both classes compile and tests pass.
- [ ] No existing code is modified — purely additive.

## Implementation notes

- The `OdysseyTransport` unit tests should mock
  `OdysseyPublisher<JsonRpcMessage>` with Mockito to verify
  `publish()` and `complete()` calls. No real Odyssey needed.
- The `SynchronousTransport` tests don't need any mocks — it's
  pure in-memory buffering.
- These classes will be wired into the controller in spec 148d.
