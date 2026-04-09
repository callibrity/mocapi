# Fix GET handler errors and session notification channel stream

## What to build

Two issues to fix:

### 1. GET handler returns 500 on error responses

The GET handler has `@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)`.
When it returns error responses (400, 403, 404), Spring cannot serialize the
response body as `text/event-stream` and throws a 500 instead.

Fix by removing the `produces` constraint or returning empty-body error responses
(e.g., `ResponseEntity.badRequest().build()` with no body).

Four compat tests are currently failing:

- `SseStreamIT.getWithoutSessionReturns400` — expected 400, got 500
- `SseStreamIT.getWithUnknownSessionReturns404` — expected 404, got 500
- `OriginValidationIT.getWithInvalidOriginReturns403` — expected 403, got 500
- `StreamingToolIT.tamperedLastEventIdReturns400` — expected 400, got 200

### 2. Session notification channel as McpSessionStream

`McpSessionService.subscribe(sessionId)` currently returns a raw `SseEmitter`
and publishes the priming event internally. It should return an
`McpSessionStream` instead, consistent with `createStream()`.

Update `subscribe()` to return `McpSessionStream` so the controller can call
`notificationStream.subscribe()` on it. Move the priming event publish into
the controller or into stream creation — not into the subscribe call.

Rename to `notificationStream(sessionId)` for clarity — it returns the
session's notification channel as an `McpSessionStream`.

The controller GET handler then becomes:
```java
if (lastEventId != null) {
    return ResponseEntity.ok().body(sessionService.reconnectStream(sessionId, lastEventId));
}
McpSessionStream channel = sessionService.notificationStream(sessionId);
channel.publishJson(Map.of()); // priming event
return ResponseEntity.ok().body(channel.subscribe());
```

## Acceptance criteria

- [ ] GET with missing session returns 400 (not 500)
- [ ] GET with unknown session returns 404 (not 500)
- [ ] GET with invalid origin returns 403 (not 500)
- [ ] GET with tampered Last-Event-ID returns 400
- [ ] `McpSessionService.notificationStream()` returns `McpSessionStream`
- [ ] Priming event is published by the caller, not inside the service method
- [ ] All 58 compat tests pass
- [ ] `mvn verify` passes
