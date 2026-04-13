# Initialization lifecycle enforcement and missing notification handlers

## What to build

Implement the MCP initialization lifecycle handshake and register handlers for the
three client-to-server notifications that are currently missing. Today, the server
accepts requests immediately after `initialize` returns without waiting for the
client's `notifications/initialized` confirmation. It also returns "method not found"
for `notifications/initialized`, `notifications/cancelled`, and
`notifications/roots/list_changed`.

### 1. Track initialized state on McpSession

Add a boolean `initialized` field to `McpSession` (default `false`). When the server
receives `notifications/initialized`, set it to `true` via `McpSessionService`.

### 2. Enforce initialization in DefaultMcpServer

In `handleCall` and `handleNotification`, after session validation succeeds, check
whether the session is initialized. If not, and the method is NOT `ping` or
`notifications/initialized`, reject the request:

- For calls: send a `JsonRpcError` with `INVALID_REQUEST` and message
  "Session not initialized"
- For notifications: silently ignore (notifications have no response)

Allowed before initialized:
- `initialize` (no session required — already handled)
- `ping` (session required, initialized check skipped)
- `notifications/initialized` (this is what *sets* initialized to true)

### 3. Handler for notifications/initialized

Create a `@JsonRpcService` class (or add to `McpSessionService`) with a
`@JsonRpcMethod(McpMethods.NOTIFICATIONS_INITIALIZED)` handler that:

- Takes `McpSession` as a parameter (via `McpSessionResolver`)
- Calls `McpSessionService` to mark the session as initialized
- Returns `EmptyResult.INSTANCE`

### 4. Handler for notifications/cancelled

Create a handler for `notifications/cancelled` that:

- Logs the cancellation at INFO level: "Received cancellation for request {requestId}, ignoring"
- Returns `EmptyResult.INSTANCE`
- Does NOT attempt to interrupt any in-flight processing (MAY ignore per spec)

The `CancelledNotificationParams` model class may need to be created in mocapi-model
if it doesn't exist. It should have fields: `requestId` (JsonNode) and `reason` (String, optional).

### 5. Handler for notifications/roots/list_changed

Create a handler for `notifications/roots/list_changed` that:

- Logs at DEBUG level: "Received roots/list_changed notification, ignoring"
- Returns `EmptyResult.INSTANCE`

### 6. Fix RuntimeException wrapping in elicit/sample timeout

In `McpResponseCorrelationService.sendAndAwait()`, when a timeout occurs:

- Send `notifications/cancelled` to the client with the original request ID and
  reason "Server timeout waiting for client response"
- Throw a descriptive unchecked exception (e.g., `McpClientResponseTimeoutException`)
  instead of `TimeoutException`

In `DefaultMcpToolContext.elicit()` and `sample()`, remove the
`catch (TimeoutException) { throw new RuntimeException(e) }` wrapping. The
correlation service should throw the unchecked exception directly.

The framework's catch-all in `McpToolsService.invokeTool()` will catch this and
return it as `CallToolResult(isError=true)` with the timeout message.

## Acceptance criteria

- [ ] `McpSession` has an `initialized` field (default false)
- [ ] `notifications/initialized` handler sets session initialized to true
- [ ] Requests (other than ping and notifications/initialized) to an uninitialized
      session receive `JsonRpcError` with `INVALID_REQUEST`
- [ ] `ping` works on a valid session that is not yet initialized
- [ ] `notifications/cancelled` is accepted and logged, not rejected as method not found
- [ ] `notifications/roots/list_changed` is accepted and logged, not rejected
- [ ] `sendAndAwait` timeout sends `notifications/cancelled` to the client
- [ ] `sendAndAwait` throws `McpClientResponseTimeoutException` (unchecked), not
      `TimeoutException`
- [ ] `DefaultMcpToolContext.elicit()` and `sample()` do not wrap exceptions in
      `RuntimeException`
- [ ] Compliance tests cover:
  - Request rejected before `notifications/initialized` (not ping)
  - Ping succeeds before `notifications/initialized`
  - Request succeeds after `notifications/initialized`
  - `notifications/cancelled` does not return method-not-found error
  - `notifications/roots/list_changed` does not return method-not-found error
  - Tool timeout produces `CallToolResult(isError=true)` with descriptive message
- [ ] All existing tests still pass

## Implementation notes

- `McpSession` currently has: `sessionId`, `protocolVersion`, `capabilities`,
  `clientInfo`, `logLevel`. Add `initialized` (boolean). Provide a
  `withInitialized(boolean)` method following the existing `withLogLevel` pattern.
- `DefaultMcpServer.handleCall()` currently only exempts `initialize` from session
  checks. Add `ping` to the exemption for the initialized check (NOT for the session
  check — ping still requires a valid session).
- `McpMethods` already has constants for all three notification methods.
- The compliance test class `InitializeComplianceTest` or a new
  `InitializationLifecycleComplianceTest` should cover the handshake enforcement.
- `CancelledNotificationParams` may need to be created in mocapi-model.
- `McpClientResponseTimeoutException` should be an unchecked exception in
  mocapi-server.
