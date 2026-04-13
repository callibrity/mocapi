# MCP spec compliance fixes (from TypeScript SDK audit)

## What to build

Based on the TypeScript SDK comparison audit (`docs/typescript-sdk-comparison.md`)
and verified against the MCP 2025-11-25 specification, fix spec compliance issues.

Note: The audit found that batching is **prohibited** by the spec ("MUST be a single
JSON-RPC request, notification, or response") and that DELETE success status is
unspecified (204 is fine). These are NOT bugs in our implementation.

### 1. Add `McpServer.validate(McpContext, JsonRpcMessage)` returning `ValidationResult`

The server owns all protocol validation rules. The controller should not assemble
validation logic from multiple query methods. Replace `requiresSession(JsonRpcMessage)`
and `sessionExists(String)` with a single `validate` method.

Create a sealed interface `ValidationResult` in `com.callibrity.mocapi.server`:

```java
public sealed interface ValidationResult {
    record Valid() implements ValidationResult {}
    record MissingSessionId() implements ValidationResult {}
    record UnknownSession(String sessionId) implements ValidationResult {}
    record SessionNotInitialized() implements ValidationResult {}
    record InvalidProtocolVersion(String version) implements ValidationResult {}
}
```

- `Valid` has no fields — the controller does not touch sessions. The server's
  handle methods still do their own session lookup and ScopedValue binding internally.
- The server checks: is a session required? Is the session ID present? Does the
  session exist? Is the session initialized (for non-ping calls)? Is the protocol
  version valid?
- For `initialize`: returns `Valid(null)` — no session needed
- For `ping` with uninitialized session: returns `Valid(session)` — ping is allowed
- Protocol version validation: absent is OK (spec says client MUST send it on
  "all subsequent requests", but server can be lenient). Present but invalid →
  `InvalidProtocolVersion`

### 2. Update `McpServer` interface

Replace:
```java
boolean requiresSession(JsonRpcMessage message);
boolean sessionExists(String sessionId);
```

With:
```java
ValidationResult validate(McpContext context, JsonRpcMessage message);
```

Remove `requiresSession` and `sessionExists` from the interface.

### 3. Implement in `DefaultMcpServer`

Move validation logic into `validate()`. The `handleCall`/`handleNotification`
methods no longer need internal session validation — the transport already called
`validate()`. The `requireSession` private method is removed.

The server's handle methods keep their existing signatures and their internal
session lookup for ScopedValue binding. The controller does NOT touch sessions —
it only checks the `ValidationResult` and maps error variants to HTTP responses.
The session lookup in the handle methods is cheap (touch-on-access already happened
during validate).

### 4. Update `StreamableHttpController`

Replace the current validation block:
```java
if (server.requiresSession(message)) {
    if (Strings.isEmpty(sessionId)) { ... }
    if (!server.sessionExists(sessionId)) { ... }
}
```

With:
```java
return switch (server.validate(context, message)) {
    case Valid() -> dispatch(context, message);
    case MissingSessionId() -> jsonRpcError(HttpStatus.BAD_REQUEST, -32000,
        "Bad Request: MCP-Session-Id header is required");
    case UnknownSession(var id) -> jsonRpcError(HttpStatus.NOT_FOUND, -32001,
        "Session not found");
    case SessionNotInitialized() -> jsonRpcError(HttpStatus.BAD_REQUEST, -32000,
        "Bad Request: Session not initialized");
    case InvalidProtocolVersion(var v) -> jsonRpcError(HttpStatus.BAD_REQUEST, -32000,
        "Bad Request: Unsupported protocol version: " + v);
};
```

Also add validation to `handleGet` and `handleDelete`. For GET/DELETE, the message
is not a `JsonRpcMessage` — create a simple overload or use null:
```java
ValidationResult validate(McpContext context);  // for GET/DELETE (no message)
```

This checks session existence and protocol version but skips the "requires session"
check (GET/DELETE always require a session via the required header).

### 5. Skip protocol version validation on initialize requests

Handled by the `validate` method — initialize doesn't require a session, so
protocol version is not checked (spec says "all subsequent requests").

### 6. Add protocol version `2024-10-07` to known versions

Add to `McpRequestValidator` or wherever protocol versions are validated.

### 7. SSE priming event on streams

When an SSE stream opens (both POST call streams and GET subscription streams),
publish a priming event as the first message through the publisher. The
`encryptingMapper` handles event ID encryption automatically.

The priming event should have empty or minimal data and establishes a `Last-Event-ID`
anchor for client reconnection. This is a SHOULD in the spec.

### 8. SSE retry field before connection close

When closing an SSE connection (not terminating the stream — just closing the HTTP
connection for reconnection), the server SHOULD send an SSE event with a `retry:`
field. This controls client reconnection timing. This is a SHOULD in the spec.

This may be an Odyssey concern — the SSE emitter framework should support setting
retry intervals.

## Acceptance criteria

- [ ] `McpServer.validate(McpContext, JsonRpcMessage)` exists and returns `ValidationResult`
- [ ] `requiresSession` and `sessionExists` removed from `McpServer` interface
- [ ] `ValidationResult` is a sealed interface with `Valid`, `MissingSessionId`,
      `UnknownSession`, `SessionNotInitialized`, `InvalidProtocolVersion` variants
- [ ] Controller maps each `ValidationResult` variant to the correct HTTP response
- [ ] Protocol version header is NOT validated on initialize requests
- [ ] `2024-10-07` is in the known protocol versions list
- [ ] Protocol version validated on GET and DELETE (when header is present)
- [ ] GET SSE streams send a priming event with encrypted event ID as first event
- [ ] All mocapi-server tests pass
- [ ] All mocapi-transport-streamable-http tests pass
- [ ] All mocapi-compat tests pass
- [ ] npx conformance suite still passes (minus completion/subscribe)

## Implementation notes

- `McpServer` is at `mocapi-server/src/main/java/com/callibrity/mocapi/server/McpServer.java`
- `DefaultMcpServer` is at `mocapi-server/src/main/java/com/callibrity/mocapi/server/DefaultMcpServer.java`
- `StreamableHttpController` is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java`
- `McpRequestValidator` is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/McpRequestValidator.java`
- The `ValidationResult` sealed interface should be in `com.callibrity.mocapi.server`
  alongside `McpServer`
- The server's `handleCall`/`handleNotification` can keep their existing session
  lookup for ScopedValue binding — it's cheap and keeps the binding logic in the server
- For GET/DELETE validation, consider an overload `validate(McpContext)` that skips
  the message check (GET/DELETE always require a session)
- Do NOT add batching support — spec prohibits it ("MUST be a single" message)
- DELETE 204 is fine — spec is silent on success status code
