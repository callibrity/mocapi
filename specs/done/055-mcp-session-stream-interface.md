# Extract McpSessionStream interface

## What to build

`McpToolMethods` currently calls `sessionService.encryptingMapper(sessionId)`
directly and wires the mapper into Odyssey's subscriber builder. The mapper
and Odyssey's subscriber API should be hidden behind an `McpSessionStream`
interface so callers only see publish and subscribe operations.

### Create McpSessionStream interface

```java
public interface McpSessionStream {
    void publishJson(Object payload);
    void close();
    SseEmitter subscribe();
    SseEmitter resumeAfter(String lastEventId);
}
```

### Create DefaultMcpSessionStream implementation

Package-private class that wraps an `OdysseyStream` and the encrypting mapper:

- `publishJson` and `close` delegate to the `OdysseyStream`
- `subscribe` calls `stream.subscriber().mapper(mapper).subscribe()`
- `resumeAfter` calls `stream.subscriber().mapper(mapper).resumeAfter(lastEventId)`

### Update McpSessionService

- `createStream(String sessionId)` returns `McpSessionStream` instead of `OdysseyStream`
- `notificationStream(String sessionId)` returns `McpSessionStream` (or add if missing)
- Remove `encryptingMapper` from the public API — it becomes a private method
- `reconnectStream` can stay as-is (it already returns `SseEmitter`)

### Update McpToolMethods

- Replace `OdysseyStream` usage with `McpSessionStream`
- Remove calls to `sessionService.encryptingMapper()` — just call
  `mcpSessionStream.subscribe()` to get the emitter
- Pass `McpSessionStream` to `DefaultMcpStreamContext` instead of `OdysseyStream`

### Update DefaultMcpStreamContext

- Accept `McpSessionStream` instead of `OdysseyStream` for publishing events
- Use `mcpSessionStream.publishJson()` instead of `stream.publishJson()`

### Fix GET handler error responses

The GET handler has `@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)`.
When it returns error responses (400, 403, 404), Spring can't serialize them as
`text/event-stream` and throws a 500. Remove the `produces` constraint or change
it to also allow `application/json` so error responses serialize correctly.

The GET handler has two paths:
- No `Last-Event-ID` → subscribe to the session's notification channel
- With `Last-Event-ID` → reconnect to any stream (notification or tool) by
  decrypting the ID to find the stream key and position

Both success paths return `SseEmitter`. Error paths return plain error bodies.

## Acceptance criteria

- [ ] `McpSessionStream` interface exists with `publishJson`, `close`, `subscribe`, `resumeAfter`
- [ ] `DefaultMcpSessionStream` is package-private
- [ ] `encryptingMapper` is no longer public on `McpSessionService`
- [ ] `McpToolMethods` uses `McpSessionStream` only — no Odyssey or mapper references
- [ ] `DefaultMcpStreamContext` accepts `McpSessionStream` instead of `OdysseyStream`
- [ ] GET handler returns proper 400/403/404 status codes (not 500)
- [ ] All existing tests pass
- [ ] `mvn verify` passes (including the 4 currently failing compat tests)
