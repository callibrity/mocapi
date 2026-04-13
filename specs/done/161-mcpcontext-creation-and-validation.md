# McpContext creation and validation via McpServer.createContext

## What to build

Replace the current `validate`/`requiresSession`/`sessionExists` approach with a
single `McpServer.createContext(String sessionId, String protocolVersion)` method
that returns a sealed `McpContextResult`. The server builds the context, resolves
the session, and validates everything in one call. The transport maps error variants
to its native error format.

### 1. Update McpContext to carry the session

```java
public interface McpContext {
    String sessionId();
    String protocolVersion();
    Optional<McpSession> session();
}
```

Provide an `EmptyMcpContext` for the initialize request (no session):

```java
public record EmptyMcpContext() implements McpContext {
    public String sessionId() { return null; }
    public String protocolVersion() { return null; }
    public Optional<McpSession> session() { return Optional.empty(); }
}
```

This can be a static factory on `McpContext` (e.g., `McpContext.empty()`) or a
standalone record.

### 2. Create McpContextResult sealed hierarchy

In `com.callibrity.mocapi.server`:

```java
public sealed interface McpContextResult {
    record ValidContext(McpContext context) implements McpContextResult {}
    record SessionIdRequired(int code, String message) implements McpContextResult {}
    record SessionNotFound(int code, String message) implements McpContextResult {}
    record ProtocolVersionMismatch(int code, String message) implements McpContextResult {}
}
```

The error variants carry a JSON-RPC error code and message. The transport decides
the HTTP status code (or whatever its native error format is). Error codes follow
the TypeScript SDK conventions:
- `-32000` for SessionIdRequired and ProtocolVersionMismatch
- `-32001` for SessionNotFound

### 3. Add McpServer.createContext

```java
McpContextResult createContext(String sessionId, String protocolVersion);
```

Implementation in `DefaultMcpServer`:
- If session ID is null/blank → `SessionIdRequired(-32000, "...")`
- If session not found → `SessionNotFound(-32001, "Session not found")`
- If session not initialized (and this isn't being called for ping — but the server
  doesn't know the method here, so skip this check; the server's handle methods
  can enforce initialized state internally)
- If protocol version is present and invalid → `ProtocolVersionMismatch(-32000, "...")`
- If protocol version is absent → accept (spec says client MUST send it on subsequent
  requests, but server can be lenient)
- Otherwise → `ValidContext(context)` where context carries the session

The `McpContext` implementation returned in `ValidContext` should include the
resolved `McpSession`.

### 4. Remove old validation methods from McpServer

Remove:
- `validate(McpContext, JsonRpcMessage)`
- `validate(McpContext)`
- `requiresSession(JsonRpcMessage)` (if still present)
- `sessionExists(String)` (if still present)

### 5. Update StreamableHttpController

The controller has one concession: it inspects the JSON-RPC method to detect
`initialize`. This is pragmatic — every transport needs to know that initialize is
the bootstrapping request with no session.

```java
return switch (message) {
    case JsonRpcCall call -> {
        if (INITIALIZE.equals(call.method())) {
            yield handleCall(McpContext.empty(), call);
        }
        yield switch (server.createContext(sessionId, protocolVersion)) {
            case ValidContext(var ctx) -> handleCall(ctx, call);
            case SessionIdRequired(var code, var msg) ->
                jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
            case SessionNotFound(var code, var msg) ->
                jsonRpcError(HttpStatus.NOT_FOUND, code, msg);
            case ProtocolVersionMismatch(var code, var msg) ->
                jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
        };
    }
    case JsonRpcNotification notification -> {
        yield switch (server.createContext(sessionId, protocolVersion)) {
            case ValidContext(var ctx) -> handleNotification(ctx, notification);
            case SessionIdRequired(var code, var msg) ->
                jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
            case SessionNotFound(var code, var msg) ->
                jsonRpcError(HttpStatus.NOT_FOUND, code, msg);
            case ProtocolVersionMismatch(var code, var msg) ->
                jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
        };
    }
    case JsonRpcResponse response -> {
        yield switch (server.createContext(sessionId, protocolVersion)) {
            case ValidContext(var ctx) -> handleResponse(ctx, response);
            case SessionIdRequired(var code, var msg) ->
                jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
            case SessionNotFound(var code, var msg) ->
                jsonRpcError(HttpStatus.NOT_FOUND, code, msg);
            case ProtocolVersionMismatch(var code, var msg) ->
                jsonRpcError(HttpStatus.BAD_REQUEST, code, msg);
        };
    }
};
```

Or factor out the `createContext` switch into a helper to avoid repetition for
notifications and responses.

For GET and DELETE, use the same `createContext` call (protocol version header
should also be accepted on GET/DELETE per spec):

```java
// handleGet
return switch (server.createContext(sessionId, protocolVersion)) {
    case ValidContext(var ctx) -> { /* subscribe to SSE */ }
    case SessionNotFound(var code, var msg) -> jsonRpcError(NOT_FOUND, code, msg);
    // other error cases...
};
```

### 6. Update DefaultMcpServer handle methods

The handle methods receive a validated `McpContext` that already carries the session.
They can read `context.session()` instead of looking up the session again. However,
they still need to bind `McpSession.CURRENT` and `McpTransport.CURRENT` as
ScopedValues. The session is available from `context.session().orElse(null)`.

Remove the internal `requireSession` private method — the transport already validated.

### 7. Protocol version validation

Move protocol version knowledge into the server. The server should know which
protocol versions it supports. Add `2024-10-07` to the known versions list.

The `McpRequestValidator.isValidProtocolVersion()` method can be removed or
simplified — the server handles version validation in `createContext`.

### 8. Initialization state enforcement

The `createContext` method does NOT check initialization state — it doesn't know
the method. The server's `handleCall` and `handleNotification` still check
`session.initialized()` internally and return appropriate JSON-RPC errors for
non-ping calls to uninitialized sessions. This is a protocol concern, not a
transport concern.

## Acceptance criteria

- [ ] `McpContext` interface has `session()` returning `Optional<McpSession>`
- [ ] `EmptyMcpContext` (or `McpContext.empty()`) exists for initialize
- [ ] `McpContextResult` sealed interface with `ValidContext`, `SessionIdRequired`,
      `SessionNotFound`, `ProtocolVersionMismatch` variants
- [ ] `McpServer.createContext(String, String)` replaces old validate methods
- [ ] Old validate/requiresSession/sessionExists methods removed from McpServer
- [ ] Controller uses `createContext` with exhaustive switch
- [ ] Controller only inspects method for `initialize` detection
- [ ] Protocol version validated in `createContext` (absent OK, invalid → error)
- [ ] `2024-10-07` added to known protocol versions
- [ ] GET and DELETE use `createContext` for session + protocol version validation
- [ ] Server handle methods read session from `context.session()`
- [ ] All mocapi-server tests pass
- [ ] All mocapi-transport-streamable-http tests pass
- [ ] All mocapi-compat tests pass
- [ ] npx conformance suite still passes

## Implementation notes

- `McpContext` is at `mocapi-server/src/main/java/com/callibrity/mocapi/server/McpContext.java`
- `McpServer` is at `mocapi-server/src/main/java/com/callibrity/mocapi/server/McpServer.java`
- `DefaultMcpServer` is at `mocapi-server/src/main/java/com/callibrity/mocapi/server/DefaultMcpServer.java`
- `StreamableHttpController` is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java`
- `McpRequestValidator` is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/McpRequestValidator.java`
- `SimpleContext` record in the controller should be replaced by the server-created context
- The controller's `handleCall` private method takes `McpContext` — update to use
  the validated context from `createContext`
- ComplianceTestSupport and compliance tests need updating for the new McpContext shape
- The `McpMethods.INITIALIZE` constant can be used in the controller for the
  initialize check — it's a model constant, not a server concern
