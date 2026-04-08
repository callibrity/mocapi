# Fix priming event format and add SSE retry field

## What to build

The MCP conformance suite (`server-sse-polling` scenario) requires:

1. **Priming event** must be an SSE event with an ID and `data: {}` (empty JSON object),
   not `data: ` (empty string). Currently Mocapi sends `stream.publishRaw("")` which
   produces `data: \n\n`. Change to `stream.publishJson(Map.of())` which produces
   `data: {}\n\n`.

2. **SSE retry field** should be sent to control client reconnection timing. The spec
   says "the server SHOULD send an SSE event with a standard retry field before closing
   the connection." This controls how long the client waits before attempting to
   reconnect.

### Fix priming event

Replace all `stream.publishRaw("")` calls with `stream.publishJson(Map.of())`.

Locations:
- POST handler: priming event before subscribing to ephemeral stream
- GET handler: priming event when opening a fresh notification stream (no Last-Event-ID)

### Add SSE retry field support

Odyssey's `StreamSubscription` currently sends a `sendComment("connected")` on startup.
This could be extended to include a `retry:` field in the SSE output. However, this
may require an Odyssey change — Spring's `SseEmitter.event().reconnectTime(millis)`
sets the `retry:` field.

If Odyssey doesn't support `retry:` natively, Mocapi can work around it by publishing
a special first event or by extending Odyssey. For now, document the gap and address
it when Odyssey exposes retry configuration.

### Verify multiple concurrent POST streams

The conformance test `server-sse-multiple-streams` sends 3 concurrent POST requests.
Mocapi should already handle this since each POST creates an independent ephemeral
Odyssey stream. Verify with a test.

## Acceptance criteria

- [ ] Priming event on POST SSE streams has `data: {}` (empty JSON object), not empty string
- [ ] Priming event on GET streams has `data: {}` (empty JSON object)
- [ ] Priming event has an SSE event ID
- [ ] Multiple concurrent POST requests are handled independently
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- `Map.of()` serialized via `publishJson()` produces `{}`. `new Object()` also works.
- The retry field is a SHOULD, not MUST. We can address it later without failing
  conformance.
- The conformance test checks: "Priming event sent with event ID and empty data" and
  "Retry field present to control reconnection timing."
