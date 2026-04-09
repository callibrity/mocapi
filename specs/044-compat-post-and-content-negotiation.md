# Compat: POST endpoint and content negotiation

## What to build

Compliance tests for the MCP Streamable HTTP POST endpoint requirements and
content negotiation rules. Uses the `McpClient` harness from spec 043.

### PostEndpointTest

- Server MUST accept POST at the MCP endpoint
- Client MUST include `Accept: application/json, text/event-stream`
- Server MUST return 406 if Accept does not include both media types
- Server MUST return 406 if Accept header is missing
- Accept with `*/*` MUST be accepted
- Request body MUST be valid JSON-RPC 2.0 (`jsonrpc` field = `"2.0"`)
- Server MUST return JSON-RPC error for invalid jsonrpc version

### ContentNegotiationTest

- Non-streaming response (initialize, ping) MUST use `Content-Type: application/json`
- Initialize response MUST be JSON, not SSE

## Acceptance criteria

- [ ] PostEndpointTest covers Accept header validation (missing, partial, wildcard)
- [ ] PostEndpointTest covers invalid JSON-RPC version
- [ ] ContentNegotiationTest verifies JSON content type on non-streaming responses
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
