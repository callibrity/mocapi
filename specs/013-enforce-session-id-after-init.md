# Enforce session ID on post-initialize requests

## What to build

The MCP spec says: "Servers that require a session ID SHOULD respond to requests without
an `MCP-Session-Id` header (other than initialization) with HTTP 400 Bad Request."

Currently, `McpStreamingController.resolveSession()` silently creates an anonymous
session when a non-initialize request arrives without an `MCP-Session-Id` header (line
240). This hides bugs in client implementations and doesn't match production MCP server
behavior.

### Remove anonymous session creation

Change `resolveSession()` so that:

1. `initialize` — creates a new session (no change)
2. Any other method WITH `MCP-Session-Id` — looks up the session, returns 404 if not
   found (no change)
3. Any other method WITHOUT `MCP-Session-Id` — returns 400 Bad Request with error
   message "MCP-Session-Id header is required"

### Update notification/response path

The `handleNotificationOrResponse()` method should also require a session ID when
processing `notifications/initialized` (it currently checks `sessionId != null` before
looking up the session, but doesn't reject the request when missing). For non-initialized
notifications, the session ID should still be required.

## Acceptance criteria

- [ ] POST without `MCP-Session-Id` for any method other than `initialize` returns
      400 Bad Request
- [ ] POST with `initialize` still creates a new session (no session ID required)
- [ ] POST with valid `MCP-Session-Id` continues to work as before
- [ ] POST with unknown/expired `MCP-Session-Id` returns 404
- [ ] Notifications without `MCP-Session-Id` return 400 (except during initialize flow)
- [ ] JSON-RPC responses without `MCP-Session-Id` return 400
- [ ] Error response body includes a descriptive message
- [ ] No anonymous sessions are created anywhere in the codebase
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- The change is primarily in `resolveSession()` — remove the fallthrough case that
  creates an anonymous session and return null (or a 400-specific signal) instead.
- Consider whether `handleNotificationOrResponse` needs the same session validation.
  The spec says "all subsequent HTTP requests" must include the session ID, which
  includes notifications and responses.
- Some existing tests may rely on anonymous session creation. Update them to include
  an `MCP-Session-Id` header.
