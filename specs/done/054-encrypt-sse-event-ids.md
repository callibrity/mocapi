# Session-scoped streams with encrypted event IDs

## What to build

Move all Odyssey stream interactions behind `McpSessionService` using an
`McpSessionStream` interface that transparently encrypts event IDs. The
controller and `McpToolMethods` never touch `OdysseyStreamRegistry`,
`SseEventMapper`, or encryption directly.

### Create McpSessionStream interface

```java
public interface McpSessionStream {
    void publishJson(Object payload);
    void close();
    SseEmitter subscribe();
    SseEmitter resumeAfter(String lastEventId);
}
```

Consumers only see publish and subscribe operations. Encryption is an
implementation detail hidden behind this interface.

### Create DefaultMcpSessionStream implementation

Wraps an `OdysseyStream` and an encrypting `SseEventMapper`:

```java
class DefaultMcpSessionStream implements McpSessionStream {
    private final OdysseyStream stream;
    private final SseEventMapper mapper;

    // publishJson and close delegate to stream
    // subscribe and resumeAfter use stream.subscriber().mapper(mapper)
}
```

This class is package-private — consumers only see the interface.

### Add stream methods to McpSessionService

```java
/** Creates an ephemeral stream for a streamable tool call. */
public McpSessionStream createStream(String sessionId)

/** Creates/gets the notification channel stream for this session. */
public McpSessionStream notificationStream(String sessionId)

/** Decrypts a Last-Event-ID and reconnects to the session's notification stream. */
public SseEmitter reconnect(String sessionId, String lastEventId)
```

Internally, each method uses the `OdysseyStreamRegistry` and creates the
encrypting `SseEventMapper` as a lambda that calls
`encrypt(sessionId, event.id())`. The encrypted plaintext should include
enough info to identify the stream and position for replay.

When `Last-Event-ID` decryption fails (tampered, wrong session), throw
`IllegalArgumentException`.

### Update StreamableHttpController

Remove `OdysseyStreamRegistry` from the constructor. Replace all direct
Odyssey calls with `McpSessionService` methods:

- GET with no Last-Event-ID: `sessionService.notificationStream(sessionId).subscribe()`
- GET with Last-Event-ID: `sessionService.reconnect(sessionId, lastEventId)`
- DELETE: session service handles stream cleanup

### Update McpToolMethods

Remove `OdysseyStreamRegistry` from the constructor. Replace
`odysseyRegistry.ephemeral()` with `sessionService.createStream(sessionId)`.
Use `mcpSessionStream.subscribe()` to get the emitter (encryption handled)
and `mcpSessionStream.publishJson()` to send events.

### Stream cleanup on delete

`McpSessionService.delete()` should also clean up the session's notification
stream via the registry.

## Acceptance criteria

- [ ] `McpSessionStream` interface exists with `publishJson`, `close`, `subscribe`, `resumeAfter`
- [ ] `DefaultMcpSessionStream` is package-private, wraps `OdysseyStream` + mapper
- [ ] `McpSessionService` has `createStream`, `notificationStream`, `reconnect`
- [ ] SSE event IDs are encrypted with session-bound keys
- [ ] `Last-Event-ID` is decrypted; tampered IDs cause 400
- [ ] Controller no longer references `OdysseyStreamRegistry`
- [ ] `McpToolMethods` no longer references `OdysseyStreamRegistry`
- [ ] Stream cleanup on session delete
- [ ] All existing tests pass
- [ ] `mvn verify` passes

## Implementation notes

- `McpSessionService` already has `encrypt()`/`decrypt()` — reuse them
- The encrypting mapper is a lambda created per-stream, capturing the sessionId
- For the notification channel, use `registry.channel(sessionId)` (named stream)
- For tool streams, use `registry.ephemeral()` (unnamed stream)
- This is a workaround until Odyssey gets proper `StreamPublisher`/`StreamSubscriber` separation
