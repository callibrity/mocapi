# Compat: Ping and tool operations

## What to build

Compliance tests for ping, tools/list, and tools/call.

### PingTest

- Server MUST respond to `ping` with an empty result object
- Ping response MUST echo the request id

### ToolDiscoveryTest

- Server MUST respond to `tools/list` with an array of tool definitions
- Each tool MUST have a `name` field
- Each tool MUST have an `inputSchema` field
- Pagination: if tools exceed page size, response MUST include `nextCursor`
- Final page MUST have null `nextCursor`

### ToolInvocationTest

- Server MUST execute the named tool and return structured content
- Tool result MUST be wrapped in a JSON-RPC result with matching id
- Unknown tool name MUST return JSON-RPC error
- Tool that throws MUST return JSON-RPC error response (not HTTP 500)

## Acceptance criteria

- [ ] PingTest covers happy path and id echo
- [ ] ToolDiscoveryTest verifies tool listing shape
- [ ] ToolInvocationTest covers echo tool, unknown tool, error tool
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
