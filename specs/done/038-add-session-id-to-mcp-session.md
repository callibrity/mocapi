# Add session ID to McpSession record

## What to build

Add the session ID as a field on `McpSession` itself, generated using UUID v7
(time-ordered). This eliminates the need for `McpSession.CURRENT_ID` ScopedValue —
the ID is always available via `McpSession.CURRENT.get().sessionId()`.

### Update `McpSession`

```java
public record McpSession(
    String sessionId,
    String protocolVersion,
    ClientCapabilities capabilities,
    ClientInfo clientInfo,
    LogLevel logLevel
) {
    public static final ScopedValue<McpSession> CURRENT = ScopedValue.newInstance();
    // ... capability check methods ...
}
```

The `sessionId` is a UUID v7 string, generated at creation time.

### Generate UUID v7 in `McpSessionService.create()`

Use `java.util.UUID.randomUUID()` or a UUID v7 generator (like the
`java-uuid-generator` library that Substrate already uses). UUID v7 is time-ordered,
which is useful for session sorting/debugging.

If `java-uuid-generator` is not already a dependency, `UUID.randomUUID()` (v4) is
fine — the spec just requires the ID to be cryptographically secure and visible ASCII.

### Delete `McpSession.CURRENT_ID` ScopedValue

The session ID is now on the record. Remove `McpSession.CURRENT_ID` and all
references. Anywhere that reads `McpSession.CURRENT_ID.get()` should read
`McpSession.CURRENT.get().sessionId()` instead.

### Update `McpSessionStore`

`save()` no longer generates the ID — it receives an `McpSession` that already has
one:

```java
void save(McpSession session);
Optional<McpSession> find(String sessionId);
void touch(String sessionId, Duration ttl);
void delete(String sessionId);
```

### Update `McpSessionService.create()`

Generates the UUID v7, constructs the `McpSession` with it, saves, and returns the
session ID.

### Update `InMemoryMcpSessionStore`

No longer responsible for ID generation — just stores and retrieves by the
session's ID.

### Update controller and all consumers

Replace `McpSession.CURRENT_ID.get()` with `McpSession.CURRENT.get().sessionId()`
everywhere.

## Acceptance criteria

- [ ] `McpSession` has a `sessionId` field
- [ ] Session ID is generated as UUID (v7 preferred, v4 acceptable)
- [ ] `McpSession.CURRENT_ID` ScopedValue is deleted
- [ ] All code reads session ID from `McpSession.CURRENT.get().sessionId()`
- [ ] `McpSessionStore.save()` accepts a complete `McpSession` with ID
- [ ] `McpSessionService.create()` generates the ID
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- Substrate uses `java-uuid-generator` (com.fasterxml.uuid) for UUID v7. If it's
  already on the classpath transitively, use it. Otherwise, `UUID.randomUUID()` is
  fine.
- The session ID must contain only visible ASCII (0x21-0x7E) per MCP spec. UUID
  string format satisfies this.
