# Session validation exceptions and compat test fixes

## What to build

The MCP Streamable HTTP spec mandates specific HTTP status codes for session errors:

- Missing `MCP-Session-Id` header (non-initialize) → **HTTP 400 Bad Request** (SHOULD)
- Unknown/terminated session ID → **HTTP 404 Not Found** (MUST)

These are transport-layer concerns, but the session validation logic lives in the
server. To bridge this cleanly, the server should throw typed exceptions that the
controller catches and maps to HTTP status codes.

### 1. Create exception types in mocapi-server

Both should be unchecked exceptions in `com.callibrity.mocapi.server`:

- **`MissingSessionIdException`** — thrown when a session ID is required but not
  provided. No fields needed, just a message.
- **`UnknownSessionException`** — thrown when the provided session ID does not match
  any active session. Include the session ID in the message.

### 2. Update DefaultMcpServer to throw these exceptions

In `handleCall()`, replace the current JSON-RPC error responses for session issues:

```java
// Before:
if (context.sessionId() == null || context.sessionId().isBlank()) {
    transport.send(call.error(INVALID_REQUEST, "Missing session ID"));
    return;
}
var sessionOpt = sessionService.find(context.sessionId());
if (sessionOpt.isEmpty()) {
    transport.send(call.error(INVALID_REQUEST, "Unknown session"));
    return;
}

// After:
if (context.sessionId() == null || context.sessionId().isBlank()) {
    throw new MissingSessionIdException();
}
var sessionOpt = sessionService.find(context.sessionId());
if (sessionOpt.isEmpty()) {
    throw new UnknownSessionException(context.sessionId());
}
```

Do the same for `handleNotification()`. For `handleResponse()`, throw
`UnknownSessionException` if needed (or silently drop — responses to unknown
sessions may just be orphans).

For `terminate()`, throw `UnknownSessionException` if the session doesn't exist
(so DELETE returns 404 per spec).

### 3. Update StreamableHttpController to catch exceptions

Add `@ExceptionHandler` methods:

```java
@ExceptionHandler(MissingSessionIdException.class)
public ResponseEntity<Object> handleMissingSession(MissingSessionIdException ex) {
    return ResponseEntity.badRequest().build();
}

@ExceptionHandler(UnknownSessionException.class)
public ResponseEntity<Object> handleUnknownSession(UnknownSessionException ex) {
    return ResponseEntity.notFound().build();
}
```

The controller no longer needs any session validation logic — it just delegates to
the server and catches the exceptions.

### 4. Update DefaultMcpServer compliance tests

The compliance tests in mocapi-server that test session validation currently expect
JSON-RPC error responses sent via the transport. Update them to expect the new
exceptions instead:

```java
// Before:
var error = captureError(transport);
assertThat(error.error().message()).isEqualTo("Missing session ID");

// After:
assertThatThrownBy(() -> server.handleCall(noSession(), call, transport))
    .isInstanceOf(MissingSessionIdException.class);
```

### 5. Fix compat integration tests

**McpClient.initialize():**
- Already updated to send `notifications/initialized` after initialize

**FullConversationIT:**
- Sends `notifications/initialized` manually at line 62, but `McpClient.initialize()`
  now sends it too. Use `initializeResult()` instead of `initialize()` in this test,
  or remove the manual notification send.

**SessionManagementIT:**
- `deleteWithUnknownSessionIdReturns404` — should now work (controller catches
  `UnknownSessionException` → 404)
- `deleteWithoutSessionIdReturns400` — Spring MVC handles required header → 400
- `postWithoutSessionIdOnNonInitializeReturns400` — controller catches
  `MissingSessionIdException` → 400
- `postWithUnknownSessionIdReturns404` — controller catches
  `UnknownSessionException` → 404
- `postToDeletedSessionReturns404` — same
- `postWithSessionIdSucceeds` — needs initialized session (use `initialize()`)

**ClientResponseIT:**
- Missing session on notification/response → `MissingSessionIdException` → 400

**Content type tests (Image, Audio, EmbeddedResource, Prompts):**
- These likely fail due to initialization lifecycle. Ensure `initialize()` is
  called (which now completes the handshake).

### 6. Remove controller-level session validation

The controller currently checks for missing session ID on notifications and
responses in `handlePost()`. Remove this — the server handles it now via exceptions.
The controller should be a pure HTTP adapter.

## Acceptance criteria

- [ ] `MissingSessionIdException` exists in mocapi-server
- [ ] `UnknownSessionException` exists in mocapi-server
- [ ] `DefaultMcpServer` throws these instead of sending JSON-RPC errors for session issues
- [ ] `StreamableHttpController` has `@ExceptionHandler` methods mapping exceptions to
      HTTP 400/404
- [ ] No session validation logic remains in the controller (except `@ExceptionHandler`)
- [ ] All mocapi-server compliance tests pass (updated to expect exceptions)
- [ ] All 79 compat integration tests pass
- [ ] `McpClient.initialize()` completes the full initialization handshake
- [ ] No duplicate `notifications/initialized` sends in tests
- [ ] All existing tests across the project still pass

## Implementation notes

- `DefaultMcpServer` is at
  `mocapi-server/src/main/java/com/callibrity/mocapi/server/DefaultMcpServer.java`
- `StreamableHttpController` is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java`
- `McpClient` is at
  `mocapi-compat/src/test/java/com/callibrity/mocapi/compat/McpClient.java`
- Compliance tests are in
  `mocapi-server/src/test/java/com/callibrity/mocapi/server/compliance/`
- The `handleNotification` path currently silently ignores bad sessions — it should
  still throw exceptions even though notifications have no response, because the
  controller needs to return the right HTTP status code.
- For `terminate()`, the server currently calls `sessionService.delete(sessionId)`.
  It needs to first check the session exists and throw `UnknownSessionException` if not.
