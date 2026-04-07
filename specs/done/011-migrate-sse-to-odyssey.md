# Migrate SSE transport to Odyssey

## What to build

Replace Mocapi's hand-rolled SSE event storage, replay, keep-alive, and emitter lifecycle
management with [Odyssey](https://github.com/jwcarman/odyssey) (`odyssey-core`). Odyssey
uses [Substrate](https://github.com/jwcarman/substrate) under the hood and auto-configures
in-memory Journal, Mailbox, and Notifier backends by default — no additional dependencies
needed.

### Add `odyssey-core` dependency

Add `org.jwcarman.odyssey:odyssey-core:1.0.0-SNAPSHOT` to
`mocapi-autoconfigure/pom.xml`. Odyssey's `OdysseyAutoConfiguration` registers a
`DefaultOdysseyStreamRegistry` bean. Substrate's `SubstrateAutoConfiguration` provides
in-memory `JournalSpi`, `MailboxSpi`, and `Notifier` beans via `@ConditionalOnMissingBean`.

### Delete `SseEvent`

`SseEvent` is fully replaced by `OdysseyEvent`. Delete
`com.callibrity.mocapi.autoconfigure.sse.SseEvent`.

### Delete `McpStreamEmitter`

`McpStreamEmitter` wraps Spring's `SseEmitter` and manages event ID generation, event
storage, keep-alive, idempotent completion, and close listeners. Odyssey handles all of
this via `OdysseyStream.subscribe()` / `resumeAfter()` and its `StreamSubscription`
(virtual-thread-backed writer loop with cursor polling, keep-alive comments, and
idempotent close). Delete `com.callibrity.mocapi.autoconfigure.sse.McpStreamEmitter`.

### Slim down `McpSession`

Remove all SSE plumbing from `McpSession`:

- Remove `eventIdCounter`, `streamEvents` map, `notificationEmitters` list
- Remove `nextEventId()`, `storeEvent()`, `getEventsAfter()`, `clearStream()`,
  `extractStreamId()`
- Remove `registerNotificationEmitter()`, `sendNotification()`,
  `getNotificationEmitterCount()`

What remains is session identity and lifecycle metadata:

- `sessionId`, `createdAt`, `lastActivity`
- `isInactive()`, activity tracking
- A reference to an `OdysseyStream` for server-initiated notifications

Create the notification stream during session creation via
`registry.channel(sessionId)`. When the server needs to push a notification, call
`stream.publishJson(payload)` (no event type — MCP events omit the `event:` field).
When a client opens a GET connection, call `stream.subscribe()` or
`stream.resumeAfter(lastEventId)` and return the resulting `SseEmitter`.

### Refactor `McpStreamingController`

**Constructor**: Add `OdysseyStreamRegistry` as a constructor parameter. Remove
`TaskExecutor` — Odyssey uses virtual threads internally, and method invocation can
use `Thread.ofVirtual().start()`.

**POST handler (`handlePost`)**:

For JSON-RPC requests (messages with `id`), instead of creating an `McpStreamEmitter`:

1. Create an ephemeral stream: `OdysseyStream stream = registry.ephemeral()`
2. Publish a priming event: `stream.publishRaw("")` — this creates a real journal entry
   with an event ID and empty data, satisfying the MCP spec requirement that the server
   "immediately send an SSE event consisting of an event ID and an empty data field."
   Note: Odyssey's built-in `sendComment("connected")` is an SSE comment with no `id:`
   field, so it does NOT satisfy this requirement. The explicit `publishRaw("")` is needed.
3. Subscribe: `SseEmitter emitter = stream.subscribe()`
4. Dispatch method invocation on a virtual thread
5. When the method completes, publish the JSON-RPC response:
   `stream.publishJson(response)` then `stream.close()`
6. Return the `SseEmitter` in the `ResponseEntity`

**GET handler (`handleGet`)**:

1. Look up the session's notification `OdysseyStream`
2. If `Last-Event-ID` is present: `SseEmitter emitter = stream.resumeAfter(lastEventId)`
3. Otherwise: publish a priming event via `stream.publishRaw("")`, then
   `SseEmitter emitter = stream.subscribe()`
4. Return the `SseEmitter`

Odyssey handles keep-alive, replay of missed events, and emitter lifecycle. The manual
priming event, event replay loop, and notification emitter registration are no longer
needed.

**DELETE handler**: Session termination should call `stream.delete()` on the session's
notification stream to disconnect all subscribers and free resources.

### Update `McpSessionManager`

When terminating a session, call `delete()` on the session's Odyssey notification
stream to close all active subscribers and free resources.

### Update `MocapiAutoConfiguration`

- Remove the `mcpTaskExecutor` bean and its `ThreadPoolTaskExecutor` setup.
- Remove the `mcpObjectMapper` bean (already done — response types now use
  `@JsonInclude(NON_NULL)` directly, so the default Spring `ObjectMapper` works).
- Inject `OdysseyStreamRegistry` into `McpStreamingController` (auto-provided by
  Odyssey's auto-configuration).

### Update tests

All tests in the `sse` package reference `McpStreamEmitter`, `SseEvent`, and session
event storage APIs that will be removed. Update tests to verify behavior through the
controller and Odyssey APIs instead:

- `McpStreamEmitterTest` — delete entirely (Odyssey tests its own emitter lifecycle)
- `McpSessionTest` — remove event ID, event storage, and notification emitter tests;
  keep session lifecycle tests (sessionId, createdAt, lastActivity, isInactive)
- `McpSessionNotificationTest` — delete or rewrite to verify notifications go through
  the session's Odyssey channel stream
- `McpStreamingControllerTest` — update to mock `OdysseyStreamRegistry` and
  `OdysseyStream` instead of verifying `McpStreamEmitter` behavior
- `McpStreamingControllerGetTest` — update to verify `resumeAfter()` is called with
  `Last-Event-ID`, and `subscribe()` is called without it
- `McpStreamingControllerComplianceTest` — update event ID and stream identity tests
  to use Odyssey-generated IDs; keep all HTTP-level compliance tests (202 for
  notifications, Accept headers, DELETE endpoint, etc.)

## Acceptance criteria

- [ ] `odyssey-core` is declared as a dependency in `mocapi-autoconfigure/pom.xml`
- [ ] `SseEvent` class is deleted
- [ ] `McpStreamEmitter` class is deleted
- [ ] `McpStreamEmitterTest` is deleted
- [ ] `McpSession` no longer contains event storage, event ID generation, or notification
      emitter management
- [ ] `McpSession` holds a reference to an `OdysseyStream` for notifications
- [ ] POST handler creates ephemeral Odyssey streams for request/response SSE
- [ ] POST handler publishes a priming event (`publishRaw("")`) before subscribing
- [ ] POST handler publishes JSON-RPC response via `publishJson()` without an event type
- [ ] POST handler calls `stream.close()` after publishing the response
- [ ] GET handler uses `OdysseyStream.subscribe()` or `resumeAfter()` for notification
      streams
- [ ] GET handler publishes a priming event when not resuming
- [ ] `Last-Event-ID` replay on GET is handled by Odyssey's `resumeAfter()`
- [ ] DELETE handler calls `stream.delete()` on the session's Odyssey stream
- [ ] `McpSessionManager.terminateSession()` calls `stream.delete()` on the session's
      notification stream
- [ ] No hand-rolled keep-alive, event ID generation, or event storage remains in Mocapi
- [ ] `mcpTaskExecutor` bean is removed; virtual threads used for method dispatch
- [ ] All existing MCP protocol behaviors are preserved (202 for notifications, 406 for
      bad Accept headers, origin validation, protocol version validation, etc.)
- [ ] All existing tests pass or are updated to reflect the new architecture
- [ ] `mvn verify` passes

## Implementation notes

- Odyssey's Maven coordinates: `org.jwcarman.odyssey:odyssey-core:1.0.0-SNAPSHOT`.
  Odyssey transitively brings in `substrate-core`, `codec-jackson`, and
  `spring-webmvc` (provided scope).
- Odyssey auto-configures via `OdysseyAutoConfiguration`. Substrate auto-configures
  in-memory backends. Just inject `OdysseyStreamRegistry`.
- `OdysseyStream.subscribe()` returns a Spring `SseEmitter` directly, so the controller
  can return it in a `ResponseEntity` as before.
- Odyssey uses the default Spring `ObjectMapper` for `publishJson()`. Since we already
  moved `@JsonInclude(NON_NULL)` to the response types, no custom mapper is needed.
- **Priming event**: Odyssey sends a `sendComment("connected")` on subscribe, but this
  is an SSE comment (`:connected\n\n`) — it has no `id:` field and won't set the
  client's `Last-Event-ID`. MCP requires a real event with an ID and empty data. Publish
  `stream.publishRaw("")` before calling `subscribe()` to create the priming event as a
  journal entry.  The subscriber will pick it up immediately.
- **Event type**: Use `publishRaw(payload)` and `publishJson(payload)` (no event type
  parameter) so the SSE output has `id:` and `data:` fields only — no `event:` field.
  This matches MCP's SSE format.
- **Stream types**: Use `registry.ephemeral()` for POST request/response (one-off,
  short-lived). Use `registry.channel(sessionId)` for GET notification streams
  (persistent per session, survives reconnects).
- **Thread model**: Odyssey's `StreamSubscription` runs a virtual thread per subscriber.
  For method invocation dispatch, use `Thread.ofVirtual().start()` instead of the
  `ThreadPoolTaskExecutor`.
- MCP-specific concerns (session ID headers, JSON-RPC envelope validation, protocol
  version checks, origin validation) remain entirely in `McpStreamingController`.
