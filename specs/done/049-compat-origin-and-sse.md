# Compat: Origin validation and SSE streaming

## What to build

Compliance tests for origin validation (DNS rebinding protection) and GET SSE endpoint.

### OriginValidationTest

- Request with invalid Origin MUST return 403
- Request with no Origin header MUST be accepted
- Origin validation MUST apply to POST, GET, and DELETE

### SseStreamTest

- GET MUST require `Accept: text/event-stream`
- GET without SSE accept MUST return 406
- GET MUST require `MCP-Session-Id`
- GET without session MUST return 400
- GET with unknown session MUST return 404

## Acceptance criteria

- [ ] OriginValidationTest covers bad origin, missing origin, all three endpoints
- [ ] SseStreamTest covers Accept validation and session requirements
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
