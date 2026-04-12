# Add sessionId as a constructor field on McpSession

## What to build

Make `sessionId` a regular field on the `McpSession` record,
set at construction time. Remove the `withSessionId()` method
and any copy-on-write pattern for setting the ID after creation.

### Before

```java
public record McpSession(
    String protocolVersion,
    ClientCapabilities capabilities,
    Implementation clientInfo,
    LoggingLevel logLevel,
    String sessionId) {

  public McpSession withSessionId(String sessionId) { ... }
}
```

### After

```java
public record McpSession(
    String sessionId,
    String protocolVersion,
    ClientCapabilities capabilities,
    Implementation clientInfo,
    LoggingLevel logLevel) {}
```

`sessionId` is the first field — it's the identity. No
`withSessionId()` needed. The session service generates the UUID
and passes it at construction time:

```java
public String create(McpSession session) {
  // session already has its ID set
  store.save(session, ttl);
  return session.sessionId();
}
```

Or if the service generates the ID:

```java
public String create(String protocolVersion, ClientCapabilities capabilities,
    Implementation clientInfo) {
  String sessionId = UUID.randomUUID().toString();
  var session = new McpSession(sessionId, protocolVersion, capabilities, clientInfo, null);
  store.save(session, ttl);
  return sessionId;
}
```

### Update all callers

- `McpSessionService.create()` — pass ID at construction.
- `DefaultMcpServer.handleInitialize()` (or wherever initialize
  creates the session) — same.
- Delete `withSessionId()`.
- Update any `withLogLevel()` to use standard record `with`
  pattern or a new constructor call.
- Update tests.

## Acceptance criteria

- [ ] `sessionId` is a constructor parameter on `McpSession`.
- [ ] No `withSessionId()` method.
- [ ] All callers updated.
- [ ] `mvn verify` passes.
