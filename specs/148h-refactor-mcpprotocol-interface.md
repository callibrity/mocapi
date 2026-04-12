# Refactor McpProtocol to explicit message-type methods

## What to build

Replace `McpProtocol.handle(McpContext, JsonRpcMessage, McpTransport)`
with three explicit methods — one per JSON-RPC message category.
Only calls need a transport; notifications and responses don't
produce output.

### Before

```java
public interface McpProtocol {
  void handle(McpContext context, JsonRpcMessage message, McpTransport transport);
}
```

### After

```java
public interface McpProtocol {
  void handleCall(McpContext context, JsonRpcCall call, McpTransport transport);
  void handleNotification(McpContext context, JsonRpcNotification notification);
  void handleResponse(McpContext context, JsonRpcResponse response);
}
```

- **`handleCall`** — dispatches the call, sends the result (or
  error) via the transport. The transport is required because
  calls produce responses (and potentially notifications during
  streaming tool execution).
- **`handleNotification`** — processes the notification (e.g.,
  `notifications/initialized`). No transport — nothing to send.
- **`handleResponse`** — delivers the client's response to the
  correlation service (Mailbox). No transport — the server
  doesn't reply to replies.

### Also add

- **`terminate(String sessionId)`** — for the DELETE endpoint.
  The controller calls this to end a session. The protocol cleans
  up the session store and any notification channels.

- **`initialize(McpContext, JsonRpcCall, McpTransport)`** — could
  be a separate method, OR `handleCall` detects initialize
  internally. Either way, the initialize flow (create session,
  emit `SessionInitialized`, send result) stays in the protocol.
  Keeping it inside `handleCall` is simpler — no special-casing
  in the interface.

### Update DefaultMcpProtocol

The current `DefaultMcpProtocol.handle()` already switches on
message type internally. Refactor into three methods. The session
enforcement logic (reject non-initialize without session) stays
in `handleCall` since that's the only method that does dispatch:

```java
@Override
public void handleCall(McpContext context, JsonRpcCall call, McpTransport transport) {
  if (context.sessionId() == null) {
    if ("initialize".equals(call.method())) {
      handleInitialize(context, call, transport);
      return;
    }
    sendError(transport, -32600, "Session required", call.id());
    return;
  }
  // validate session, dispatch...
}

@Override
public void handleNotification(McpContext context, JsonRpcNotification notification) {
  // validate session, process notification
}

@Override
public void handleResponse(McpContext context, JsonRpcResponse response) {
  // deliver to correlation service
}
```

### What NOT to do

- Do NOT modify mocapi-core.
- Do NOT create the transport module yet — that's spec 148i.

## Acceptance criteria

- [ ] `McpProtocol` interface has `handleCall`, `handleNotification`,
      `handleResponse`, and `terminate` methods.
- [ ] Only `handleCall` takes an `McpTransport` parameter.
- [ ] `DefaultMcpProtocol` updated to implement the new interface.
- [ ] All existing protocol tests updated and passing.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
