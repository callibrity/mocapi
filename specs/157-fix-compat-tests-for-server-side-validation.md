# Fix compat tests for session validation and initialization lifecycle

## What to build

The compat integration tests need to be updated for two changes:

1. **Session validation now uses `McpServer.requiresSession()` and `sessionExists()`.**
   The controller calls these before dispatching, returning HTTP 400 (missing session)
   or 404 (unknown session) with JSON-RPC error bodies. This is already implemented
   in `StreamableHttpController`.

2. **Initialization lifecycle enforcement.** `McpClient.initialize()` now sends
   `notifications/initialized` after the `initialize` call. Tests that manually
   send it will double-send.

### 1. Update compat tests to not expect JSON-RPC error bodies on HTTP errors

The controller returns empty 400/404 responses for session validation failures.
This is correct — these are HTTP-level errors, not JSON-RPC errors. There is no
JSON-RPC request to respond to (the transport is rejecting before JSON-RPC
processing). The MCP spec just says "respond with HTTP 400" or "respond with
HTTP 404" — no body format is specified.

Update `SessionManagementIT` tests to check only the HTTP status code:
- `postWithoutSessionIdOnNonInitializeReturns400` — assert `status().isBadRequest()` only
- `postWithUnknownSessionIdReturns404` — assert `status().isNotFound()` only
- `postToDeletedSessionReturns404` — assert `status().isNotFound()` only
- `deleteWithUnknownSessionIdReturns404` — assert `status().isNotFound()` only
- `deleteWithoutSessionIdReturns400` — assert `status().isBadRequest()` only

Update `ClientResponseIT` tests similarly:
- `clientResultWithoutSessionIdReturns400` — assert `status().isBadRequest()` only
- `clientErrorWithoutSessionIdReturns400` — assert `status().isBadRequest()` only

Remove all `jsonPath("$.error.*")` assertions from these tests.

### 2. Fix FullConversationIT

`FullConversationIT` manually sends `notifications/initialized` at line 62, but
`McpClient.initialize()` now sends it automatically. Either:
- Use `initializeResult()` instead of `initialize()` and keep the manual send
- Or use `initialize()` and remove the manual `notifications/initialized` call

### 3. Fix ClientResponseIT

Tests `clientResultWithoutSessionIdReturns400` and `clientErrorWithoutSessionIdReturns400`
expect JSON-RPC error bodies. These send JSON-RPC responses/errors without a session
ID. The controller's `requiresSession` check handles this — just ensure the error
body matches what the test expects.

### 4. Verify session validation logic

The controller's validation should be:

```java
if (server.requiresSession(message)) {
    if (sessionId is missing) {
        return 400 with JSON-RPC error body
    }
    if (!server.sessionExists(sessionId)) {
        return 404 with JSON-RPC error body
    }
}
```

For GET and DELETE (session ID is always required via `@RequestHeader`):
```java
if (!server.sessionExists(sessionId)) {
    return 404 with JSON-RPC error body
}
```

## Acceptance criteria

- [ ] Session validation 400 responses return empty body (HTTP-level error, not JSON-RPC)
- [ ] Session validation 404 responses return empty body (HTTP-level error, not JSON-RPC)
- [ ] Compat tests assert only HTTP status codes, no JSON-RPC error body assertions
- [ ] `FullConversationIT` does not double-send `notifications/initialized`
- [ ] `ClientResponseIT` tests pass
- [ ] `SessionManagementIT` tests pass
- [ ] All mocapi-server tests still pass
- [ ] npx conformance suite still passes (minus completion/subscribe which are removed)

## Implementation notes

- `StreamableHttpController` is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java`
- `McpClient` is at
  `mocapi-compat/src/test/java/com/callibrity/mocapi/compat/McpClient.java`
- `McpClient.initialize()` already sends `notifications/initialized` automatically
- The controller already has `requiresSession` and `sessionExists` checks in
  `handlePost`, `handleGet`, and `handleDelete`
- Use `objectMapper` to build the JSON-RPC error response bodies
- Do NOT change `McpServer`, `DefaultMcpServer`, or the compliance tests — those
  are already correct
- The compat tests that exercise tools/prompts/resources/etc with session-bound calls
  may also fail due to MockMvc not handling async SSE — that is covered separately
  by spec 158. Focus this spec on session validation and initialization only.
