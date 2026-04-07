# Validate Origin header on GET and DELETE endpoints

## What to build

The MCP spec says: "Servers MUST validate the Origin header on all incoming connections
to prevent DNS rebinding attacks. If the Origin header is present and invalid, servers
MUST respond with HTTP 403 Forbidden."

Currently, origin validation only happens in the POST handler. The GET and DELETE
handlers do not check the `Origin` header. A malicious page could use DNS rebinding to
issue GET requests (opening notification streams) or DELETE requests (terminating
sessions) against a local MCP server.

### Add Origin header to GET handler

Add `@RequestHeader(value = "Origin", required = false) String origin` to `handleGet()`.
If the origin is present and invalid per `isValidOrigin()`, return 403 Forbidden.

### Add Origin header to DELETE handler

Add `@RequestHeader(value = "Origin", required = false) String origin` to
`handleDelete()`. If the origin is present and invalid per `isValidOrigin()`, return
403 Forbidden.

### Extract shared validation

The origin validation logic is identical across all three handlers. Consider extracting
it into a shared method or a Spring `HandlerInterceptor` to avoid duplication. The
simplest approach is a private helper called early in each handler. A
`HandlerInterceptor` is cleaner but may be over-engineering for three call sites.

## Acceptance criteria

- [ ] GET with a present but invalid `Origin` header returns 403 Forbidden
- [ ] GET with a valid `Origin` header succeeds
- [ ] GET with no `Origin` header succeeds (origin validation only applies when present)
- [ ] DELETE with a present but invalid `Origin` header returns 403 Forbidden
- [ ] DELETE with a valid `Origin` header succeeds
- [ ] DELETE with no `Origin` header succeeds
- [ ] POST origin validation is unchanged
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- `isValidOrigin()` and the `allowedOrigins` list already exist in the controller.
  The GET and DELETE handlers just need to call the same validation.
- The error response format for 403 on GET/DELETE should match the POST handler's
  pattern. POST returns a JSON-RPC error object; GET/DELETE could return a simple
  JSON body like `{"error": "Invalid origin"}` since there's no JSON-RPC request ID
  to reference.
- The spec says the HTTP response body "MAY comprise a JSON-RPC error response that
  has no id" — so a JSON-RPC error without an id is also acceptable.
