# Improve test coverage to 90%+ on all source files

## What to build

Add unit tests to bring all source files to 90%+ line coverage. Current coverage is
75.4% overall. Six files need attention:

| File | Current | Target | Uncovered Lines |
|------|---------|--------|-----------------|
| McpStreamEmitter.java | 45.7% | 90%+ | 51 |
| ToolServiceMcpToolProvider.java | 53.3% | 90%+ | 7 |
| McpStreamingController.java | 63.3% | 90%+ | 69 |
| McpSessionManager.java | 71.2% | 90%+ | 11 |
| McpSession.java | 81.4% | 90%+ | 9 |
| JsonMethodInvoker.java | 88.9% | 90%+ | 3 |

### McpStreamEmitter (45.7% → 90%+)

Test file: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamEmitterTest.java`

Cover:
- `sendPrimingEvent()` — verify event ID generated and stored
- `send(data)` — verify event sent with ID
- `sendAndComplete(data)` — verify event sent then stream completed
- `complete()` — idempotent (call twice, verify only completes once)
- `completeWithError(ex)` — idempotent
- Timeout callback triggers `completeWithError`
- Error callback triggers `completeWithError`
- `onClose` listeners called on completion
- `onClose` listeners called on error
- Close listener exception doesn't prevent other listeners
- `trySendInternal` after completion returns false
- IOException during send triggers `completeWithError`

### ToolServiceMcpToolProvider (53.3% → 90%+)

Test file: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/tools/ToolServiceMcpToolProviderTest.java`

Cover:
- `initialize()` discovers `@ToolService` beans from ApplicationContext
- `getMcpTools()` returns discovered tools
- Multiple `@ToolService` beans each contribute their `@Tool` methods
- Bean with no `@Tool` methods produces no tools

### McpStreamingController (63.3% → 90%+)

Test file: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/sse/McpStreamingControllerTest.java`

Cover all the POST handler paths:
- Valid JSON-RPC request dispatches to `invokeMethod`
- Invalid `jsonrpc` field returns 400
- Missing `method` field returns 400
- Invalid `id` type returns 400
- Invalid protocol version returns 400
- Invalid origin returns 403
- Session not found returns 404
- Initialize creates session and sets MCP-Session-Id header
- Notification returns 202 Accepted
- JSON-RPC response returns 202 Accepted
- Invalid Accept header returns 406
- `tools/list` dispatches to capability
- `tools/call` dispatches to capability
- `ping` returns empty object
- Unknown method returns -32601 error
- McpException propagates error code
- RuntimeException returns -32603

### McpSessionManager (71.2% → 90%+)

Test file: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/sse/McpSessionManagerTest.java`

Cover:
- `createSession()` returns unique sessions
- `getSession()` returns session by ID
- `getSession()` returns empty for unknown ID
- `getSession()` returns empty for expired session
- `terminateSession()` removes session
- `terminateSession()` returns false for unknown ID
- `cleanupInactiveSessions()` removes expired sessions
- `cleanupInactiveSessions()` keeps active sessions
- `shutdown()` stops the cleanup executor
- `getSessionCount()` returns correct count

### McpSession (81.4% → 90%+)

Test file: `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/sse/McpSessionTest.java`

Cover:
- `nextEventId(streamId)` generates sequential IDs containing stream ID
- `storeEvent()` and `getEventsAfter()` round-trip
- `getEventsAfter()` returns empty for unknown stream
- `getEventsAfter()` returns empty for null stream ID extraction
- `clearStream()` removes events
- `isInactive()` returns false for active session
- `isInactive()` returns true for expired session
- `registerNotificationEmitter()` and `sendNotification()` deliver to emitters
- `sendNotification()` removes failed emitters
- `extractStreamId()` correctly parses event ID format

### JsonMethodInvoker (88.9% → 90%+)

Test file: `mocapi-core/src/test/java/com/callibrity/mocapi/server/invoke/JsonMethodInvokerTest.java`

Cover the remaining uncovered lines:
- Method that throws checked exception wraps in McpInternalErrorException
- Method that throws Error re-throws directly
- IllegalAccessException wraps in McpInternalErrorException

## Acceptance criteria

- [ ] `mvn verify` passes
- [ ] JaCoCo aggregate coverage is 90%+ overall
- [ ] Each of the 6 files listed above is at 90%+ line coverage
- [ ] No new SonarCloud issues introduced
- [ ] Tests use meaningful assertions (not just "doesn't throw")

## Implementation notes

- Use `SyncTaskExecutor` in controller tests to avoid async complexity.
- For `McpStreamEmitter` tests, mock the `SseEmitter` to simulate IOException and
  IllegalStateException scenarios.
- For `ToolServiceMcpToolProvider`, use a mock `ApplicationContext` that returns
  test `@ToolService` beans.
- For session timeout tests, construct `McpSessionManager` with a very short timeout
  (1 second) and use `Thread.sleep` or a `Clock` abstraction.
