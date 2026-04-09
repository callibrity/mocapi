# Session-scoped streams with encrypted event IDs

## What to build

Move all Odyssey stream interactions behind `McpSessionService`. The service
creates session-scoped streams that transparently encrypt event IDs on the way
out and decrypt `Last-Event-ID` on the way in. The controller and
`McpToolMethods` never touch Odyssey or encryption directly.

### Add stream methods to McpSessionService

```java
/** Creates a new SSE stream bound to this session. Event IDs are encrypted. */
public OdysseyStream createStream(String sessionId)

/** Reconnects to an existing SSE stream. Decrypts lastEventId to find the stream. */
public SseEmitter reconnectStream(String sessionId, String lastEventId)

/** Opens a new subscription on the session's notification channel. */
public SseEmitter subscribe(String sessionId)
```

Internally, `createStream` uses the `OdysseyStreamRegistry` and wraps the
stream's subscriber builder with an encrypting `SseEventMapper` that calls
`encrypt(sessionId, event.id())`. The encrypted ID should encode both the
stream key and the event ID so that `reconnectStream` can decrypt it and
call `stream.subscriber().mapper(encryptingMapper).resumeAfter(decryptedId)`.

When `Last-Event-ID` decryption fails (tampered, wrong session), throw
`IllegalArgumentException` — the controller catches it and returns 400.

### Update StreamableHttpController

Remove all direct references to `OdysseyStreamRegistry`. Replace with
`McpSessionService` calls:

- GET handler: `sessionService.subscribe(sessionId)` or
  `sessionService.reconnectStream(sessionId, lastEventId)`
- Remove `OdysseyStreamRegistry` from the constructor

### Update McpToolMethods

Replace `odysseyRegistry.ephemeral()` with `sessionService.createStream(sessionId)`.
The stream automatically encrypts event IDs. Remove `OdysseyStreamRegistry`
from the constructor.

### Delete handler

Session deletion should also clean up streams. Add cleanup logic to
`McpSessionService.delete()` or let the controller call it explicitly.

## Acceptance criteria

- [ ] `McpSessionService` has `createStream`, `reconnectStream`, and `subscribe` methods
- [ ] SSE event IDs are encrypted with session-bound keys
- [ ] `Last-Event-ID` is decrypted and validated; tampered IDs return 400
- [ ] Controller no longer references `OdysseyStreamRegistry`
- [ ] `McpToolMethods` no longer references `OdysseyStreamRegistry`
- [ ] Stream cleanup on session delete
- [ ] All existing tests pass
- [ ] `mvn verify` passes

## Implementation notes

- The encrypted event ID should contain enough info to find the right stream
  and resume from the right position. Consider encoding `streamKey:eventId`
  as the plaintext before encryption.
- `McpSessionService` already has `encrypt()`/`decrypt()` — reuse them.
- The `SseEventMapper` is a functional interface — the encrypting mapper
  can be a lambda created per-stream.
