# Compat: Initialization

## What to build

Compliance tests for the MCP initialize handshake.

### InitializationTest

- Server MUST respond to `initialize` with protocol version, capabilities, and server info
- Server MUST NOT require `MCP-Session-Id` for initialize requests
- Response `protocolVersion` MUST be a recognized MCP version string
- Response `capabilities` MUST be present
- Response `serverInfo` MUST contain `name`
- Client sends `notifications/initialized` after receiving initialize response
- Server MUST return 202 for `notifications/initialized`

## Acceptance criteria

- [ ] Initialize response shape verified (protocolVersion, capabilities, serverInfo.name)
- [ ] Initialize works without session ID
- [ ] notifications/initialized returns 202
- [ ] All tests use `McpClient` from spec 043
- [ ] `mvn verify` passes
