# Compat: Notifications and client responses

## What to build

Compliance tests for notification handling and client response messages.

### NotificationTest

- Notification (request without id) MUST return 202 Accepted
- Server MUST NOT return a JSON-RPC response body for notifications
- Notification to unknown method MUST still return 202 (no error response)

### ClientResponseTest

- Server MUST accept JSON-RPC result messages (client responding to server request)
- Server MUST accept JSON-RPC error messages (client rejecting server request)
- Client responses MUST return 202 Accepted
- Client response without session ID MUST return 400

## Acceptance criteria

- [ ] NotificationTest covers 202 response, no body, unknown method
- [ ] ClientResponseTest covers result and error from client, missing session
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
