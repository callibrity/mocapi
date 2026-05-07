# Session & Context

Every JSON-RPC call (after `initialize`) is bound to a session, and
every dispatch begins by resolving the session into an `McpContext`.
The contract between transports and the server is shaped by a sealed
result type so each transport can map validation failures to its own
error format. See
[ADR-0009](../adr/0009-mcpcontextresult-sealed-validation.md).

## `McpContext` and validation

The transport asks the server to create a context before dispatching:

```java
McpContextResult result = server.createContext(sessionId, protocolVersion);
```

The server returns a sealed type:

```java
sealed interface McpContextResult {
    record ValidContext(McpContext context) {}
    record SessionIdRequired(int code, String message) {}
    record SessionNotFound(int code, String message) {}
    record ProtocolVersionMismatch(int code, String message) {}
}
```

The transport maps each variant to its native error format. For
Streamable HTTP:

| Result | HTTP Status | JSON-RPC Code |
|--------|-------------|---------------|
| `ValidContext` | (proceed) | — |
| `SessionIdRequired` | 400 | -32000 |
| `SessionNotFound` | 404 | -32001 |
| `ProtocolVersionMismatch` | 400 | -32000 |

For stdio, the transport writes a `JsonRpcError` line to stdout with
the same JSON-RPC code; there is no status code.

The `McpContext` carries the resolved session:

```java
interface McpContext {
    String sessionId();
    String protocolVersion();
    Optional<McpSession> session();
}
```

For `initialize` requests (which have no session), the transport uses
`McpContext.empty()`.

## Session lifecycle

1. Client sends `initialize` — server creates a session, returns session ID in response header.
2. Client sends `notifications/initialized` — server marks session as initialized.
3. Between steps 1 and 2, only `ping` is allowed (per MCP spec).
4. Normal operations proceed.
5. Client sends HTTP `DELETE` (or closes stdin on stdio) — server terminates the session.
6. Subsequent requests with that session ID get HTTP 404 / a `SessionNotFound` error.

Sessions are stored in a pluggable `McpSessionStore` backed by
Substrate's Atom SPI (see [Storage & Substrate](storage-and-substrate.md)).
Each session has a configurable TTL that is refreshed on access.

## Session record

The `McpSession` record carries everything needed to dispatch a call
in the context of that session:

- session ID
- protocol version negotiated at initialize
- client capabilities
- log level
- initialized flag

It is bound as a `ScopedValue` (`McpSession.CURRENT`) for the duration
of every call, alongside `McpTransport.CURRENT` and (for tool calls)
`McpToolContext.CURRENT`. See
[Architecture Overview — ScopedValue pattern](architecture-overview.md#scopedvalue-pattern).
