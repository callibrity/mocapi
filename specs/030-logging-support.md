# Logging support

## What to build

Implement MCP logging so tools can send structured log messages to the client during
execution. The client controls verbosity via `logging/setLevel`. Messages below the
threshold are silently dropped.

### Define `LogLevel` enum

In `mocapi-core`, ordered by severity (RFC 5424):

```java
public enum LogLevel {
    DEBUG, INFO, NOTICE, WARNING, ERROR, CRITICAL, ALERT, EMERGENCY
}
```

Ordinal comparison determines filtering: `message.level >= session.logLevel`.

### Add log level to `McpSession`

`McpSession` is currently an immutable record. Add `logLevel` with a sensible default:

```java
public record McpSession(
    String protocolVersion,
    ClientCapabilities capabilities,
    ClientInfo clientInfo,
    LogLevel logLevel
) {}
```

Default log level when session is created: `WARNING` (or `INFO` — implementation
decision). The level is updated when the client calls `logging/setLevel`.

Since `McpSession` is a record (immutable), updating the log level means saving a
new record to the session store:

```java
McpSession updated = new McpSession(
    session.protocolVersion(),
    session.capabilities(),
    session.clientInfo(),
    newLevel
);
store.save(sessionId, updated, ttl);
```

### Add `@JsonRpc("logging/setLevel")` handler

In the session package (alongside initialize):

```java
@JsonRpc("logging/setLevel")
public void setLevel(String level) {
    // Look up current session from ScopedValue or similar
    // Parse level string to LogLevel enum
    // Save updated session with new log level
}
```

The handler needs access to the current session ID to update the store. This could
come from a `ScopedValue<String>` set by the controller before dispatch, or from a
param resolver that provides the session ID.

Returns empty result (`{}`) per spec.

If the level string is invalid, throw `JsonRpcException(INVALID_PARAMS, ...)`.

### Add logging methods to `McpStreamContext`

```java
void log(LogLevel level, String logger, Object data);

// Convenience methods
void debug(String logger, Object data);
void info(String logger, Object data);
void notice(String logger, Object data);
void warning(String logger, Object data);
void error(String logger, Object data);
void critical(String logger, Object data);
void alert(String logger, Object data);
void emergency(String logger, Object data);
```

Each convenience method delegates to `log()`.

`log()` implementation:
1. Check if the client supports logging (`session.capabilities()` — but logging
   support is server-advertised, not client-advertised, so this check may not be
   needed)
2. Read the session's current log level from the session store
3. If `level.ordinal() < session.logLevel().ordinal()`, return (no-op)
4. Build `notifications/message` JSON-RPC notification with `level`, `logger`, `data`
5. Publish on the SSE stream

### Advertise logging capability

Update `ServerCapabilities` to include logging:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerCapabilities(
    ToolsCapabilityDescriptor tools,
    LoggingCapabilityDescriptor logging
) {}

public record LoggingCapabilityDescriptor() {}
```

Update the `InitializeResponse` bean to include it:

```java
new ServerCapabilities(
    new ToolsCapabilityDescriptor(false),
    new LoggingCapabilityDescriptor()
)
```

### Notification format

Published on SSE stream as:

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/message",
  "params": {
    "level": "error",
    "logger": "my-tool",
    "data": {"message": "Something went wrong", "code": 42}
  }
}
```

`data` is serialized via Jackson from whatever `Object` the tool passes.

## Acceptance criteria

- [ ] `LogLevel` enum exists with 8 RFC 5424 severity levels
- [ ] `McpSession` includes `logLevel` field
- [ ] `logging/setLevel` JSON-RPC handler updates session log level
- [ ] Invalid level string returns JSON-RPC error -32602
- [ ] `McpStreamContext` has `log(LogLevel, String, Object)` method
- [ ] `McpStreamContext` has convenience methods for each level
- [ ] Messages below session threshold are silently dropped
- [ ] Log notifications match spec format (`notifications/message`)
- [ ] `ServerCapabilities` includes `logging: {}`
- [ ] `InitializeResponse` advertises logging capability
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- The log level check requires reading the session from the store on every `log()`
  call. This could be expensive if the store is remote (Redis). Consider caching
  the level in the `DefaultMcpStreamContext` instance (set when created, updated
  if `setLevel` is called during the same request). Since `setLevel` and tool
  execution happen on different requests, the cached level is always current for
  the duration of a single tool call.
- `data` is `Object` — Jackson serializes it. If someone passes a `String`, it
  serializes as a JSON string. If they pass a `Map`, it serializes as a JSON object.
  This matches the spec which says data can be any JSON value.
- The spec says servers SHOULD implement rate limiting on log messages. We can add
  that later — for now, the level filtering is sufficient.
- The spec says servers MUST NOT include credentials, PII, or exploitable system
  internals in log data. This is a documentation/guidance concern, not something
  we enforce programmatically.
- The `logging/setLevel` handler needs the session ID from the current request.
  The controller sets a `ScopedValue<String>` with the session ID before dispatch.
  The handler reads it to look up and update the session.
