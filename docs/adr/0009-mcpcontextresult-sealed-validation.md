# ADR-0009 — Server returns a sealed `McpContextResult`; transports map variants to native errors

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

Every non-`initialize` MCP message requires three things to be true before
the server can act on it:

1. The caller presented a session id.
2. That session exists and has not expired.
3. The caller's protocol version (when present) matches one the server
   supports.

An earlier mocapi design exposed this as three separate methods on
`McpServer` (`requiresSession`, `sessionExists`, plus a generic
`validate`). Transports orchestrated the calls themselves and built error
responses from scratch. Two problems:

- The error codes and messages drifted between transports — the HTTP
  controller and any compat shim had to reimplement the same error text.
- Transports inspected the JSON-RPC method to decide whether the message
  was `initialize` *and* re-implemented session validation around it,
  reproducing protocol-layer logic in the transport layer.

The server has all the information needed to validate; it should validate
once and tell the transport precisely what to do.

## Decision

`McpServer` exposes a single context-creation method that returns a
sealed result:

```java
McpContextResult createContext(String sessionId, String protocolVersion);

public sealed interface McpContextResult {
    record ValidContext(McpContext context)            implements McpContextResult {}
    record SessionIdRequired(int code, String message) implements McpContextResult {}
    record SessionNotFound(int code, String message)   implements McpContextResult {}
    record ProtocolVersionMismatch(int code, String message) implements McpContextResult {}
}
```

`McpContext` carries the resolved session, the negotiated protocol
version, and the session id:

```java
public interface McpContext {
    String sessionId();
    String protocolVersion();
    Optional<McpSession> session();
    static McpContext empty() { /* used for the initialize call */ }
}
```

**Rules:**

1. Every non-`initialize` message goes through `createContext`. The
   `initialize` call uses `McpContext.empty()` and bypasses validation
   (it has no session yet).
2. Error variants carry JSON-RPC error codes:
   - `SessionIdRequired` → `-32000`
   - `SessionNotFound` → `-32001`
   - `ProtocolVersionMismatch` → `-32000`
   These match the conventions the TypeScript SDK established.
3. The server knows which protocol versions it supports. Transports do
   not validate protocol versions independently. An absent
   `MCP-Protocol-Version` header is accepted (lenient default); a
   present-but-unsupported version produces `ProtocolVersionMismatch`.
4. The server does **not** validate initialization state in
   `createContext` (it does not know the method). The `handleCall` /
   `handleNotification` paths enforce "initialized session required"
   for non-`ping` methods internally.
5. Transports map variants to their native error format. For Streamable
   HTTP:

   | Variant | HTTP status | JSON-RPC code |
   |---|---|---|
   | `ValidContext` | (proceed) | — |
   | `SessionIdRequired` | 400 | -32000 |
   | `SessionNotFound` | 404 | -32001 |
   | `ProtocolVersionMismatch` | 400 | -32000 |

   For stdio, the transport writes a `JsonRpcError` line to stdout with
   the same JSON-RPC code; there is no status code.

6. The HTTP controller's only protocol-aware concession is the
   `initialize` method check — the bootstrapping request that has no
   session. Every transport needs that single carve-out. Everything
   else is delegated to `createContext`.

## Consequences

**Wins:**

- Validation lives in one place. A bug fix in session expiry semantics
  fixes every transport.
- Adding a transport reduces to "construct an `McpContext`, call
  `createContext`, exhaustive-switch on the result." The compiler
  enforces that every variant is handled — adding a new variant
  (e.g., a future "session terminated" state) is a localized refactor
  the compiler walks you through.
- Error codes and messages are consistent between transports. A client
  that reads `-32001` knows the session is gone, regardless of wire
  format.

**Costs:**

- The sealed type is part of the public API of `mocapi-server`. Adding
  a variant is a minor breaking change for any downstream transport
  implementer (none today besides the two we ship). Acceptable
  pre-1.0; needs deliberate handling at 1.0.
- Transports must surface error codes and messages from the variants
  rather than crafting their own. A transport that wants to localize a
  message has to map the code to its preferred text.

**Non-goals:** the server does not return rich validation diagnostics
(why exactly the protocol version is wrong, which sessions exist).
Variants carry just enough to produce a valid JSON-RPC error.

**Code anchors:** `mocapi-server/.../McpContextResult.java` (sealed); transport mappings in `mocapi-streamable-http-transport/.../StreamableHttpController.java` and `mocapi-stdio-transport/.../StdioServer.java`.
