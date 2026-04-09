# Compat: End-to-end scenario tests

## What to build

Full conversation scenario tests that verify the MCP protocol works end-to-end.

### FullConversationTest

Walks through a complete MCP session per the spec lifecycle:

1. Client sends `initialize` → receives session ID and server capabilities
2. Client sends `notifications/initialized` → 202
3. Client sends `tools/list` → receives tool definitions
4. Client sends `tools/call` for echo tool → receives structured content result
5. Client sends `ping` → receives pong
6. Client sends DELETE → 204
7. Client sends `ping` with old session → 404

### ProtocolVersionNegotiationTest

- Server accepts `MCP-Protocol-Version: 2025-11-25`
- Server rejects unrecognized protocol version with error
- Missing `MCP-Protocol-Version` header defaults to current version

## Acceptance criteria

- [ ] FullConversationTest walks the complete session lifecycle
- [ ] ProtocolVersionNegotiationTest covers valid, invalid, and missing versions
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
