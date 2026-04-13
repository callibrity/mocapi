# Fix SSE priming event causing ResponseBodyEmitter already completed in MockMvc

## What to build

The `sendPrimingEvent` method in `StreamableHttpController` calls `emitter.send()`
on the `SseEmitter` returned by `odyssey.subscribe()`. In MockMvc tests, the
emitter may already be in a completed state, causing
`IllegalStateException: ResponseBodyEmitter has already completed`.

This works in real HTTP (npx conformance passes) but fails in MockMvc tests because
MockMvc's async handling completes the emitter before the priming event can be sent.

### The problem

In `handleGetStream()`:
```java
SseEmitter emitter = odyssey.subscribe(...);
sendPrimingEvent(emitter, sessionId, sessionId);  // fails in MockMvc
return ResponseEntity.ok().body(emitter);
```

The priming event is sent on the emitter *before* it's returned as the response body.
In a real servlet container, this works because the response is streamed. In MockMvc,
the emitter lifecycle is different.

### Fix

The priming event should be sent through Odyssey's publisher, not directly on the
emitter. This way it flows through the normal SSE event pipeline and the
`encryptingMapper` handles the event ID encryption.

For the GET path, the priming event should be published on the session's Odyssey
topic before subscribing, or immediately after subscribing via the publisher.

Actually, for GET streams the session topic already exists (it was created during
initialization or a prior POST). The priming event just needs to be one of the
events in the stream. Consider whether Odyssey's subscribe should automatically
replay from the beginning if there are no events — that would give the client an
event ID naturally.

Alternatively, wrap the `emitter.send()` in the `sendPrimingEvent` method with a
check that the emitter is still active, or catch the `IllegalStateException` more
gracefully (the current code already catches it but logs a warning — the tests may
need to tolerate the warning).

The simplest fix: make the MockMvc tests for GET requests use `asyncDispatch()`
pattern, similar to how POST SSE tests were fixed. The priming event would then
be captured as part of the async response.

## Acceptance criteria

- [ ] SSE GET tests pass in MockMvc (`SseStreamIT`, `DnsRebindingProtectionIT`)
- [ ] Priming event is still sent in production (npx conformance passes)
- [ ] No `ResponseBodyEmitter has already completed` errors in test output
- [ ] All other compat tests still pass

## Implementation notes

- `StreamableHttpController.sendPrimingEvent()` is at line 191
- `SseStreamIT` is at `mocapi-compat/src/test/java/com/callibrity/mocapi/compat/SseStreamIT.java`
- `DnsRebindingProtectionIT` is at
  `mocapi-compat/src/test/java/com/callibrity/mocapi/compat/DnsRebindingProtectionIT.java`
- The tests use `client.get(sessionId).andExpect(status().isOk())` which hits the
  GET endpoint directly through MockMvc
- The `sendPrimingEvent` catch block already handles `IllegalStateException` — the
  issue is that MockMvc treats the exception differently, causing the whole request
  to fail rather than just the priming send
