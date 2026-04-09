# Compat: Session management

## What to build

Compliance tests for MCP session lifecycle requirements.

### SessionManagementTest

- Server MUST return `MCP-Session-Id` header on initialize response
- Client MUST include `MCP-Session-Id` on all subsequent requests
- Server MUST return 400 if `MCP-Session-Id` is missing on non-initialize request
- Server MUST return 404 if `MCP-Session-Id` refers to unknown/expired session
- Server MUST accept DELETE to terminate a session
- Server MUST return 204 on successful session termination
- Server MUST return 400 on DELETE without session ID
- Server MUST return 404 on DELETE with unknown session ID
- Requests to a deleted session MUST return 404

## Acceptance criteria

- [ ] All session lifecycle paths covered (create, require, missing, unknown, delete, post-delete)
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
