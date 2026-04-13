# Fix StreamableHttpControllerTest for current controller API

## What to build

`StreamableHttpControllerTest` is broken — it references `McpSessionService` and
`BufferingTransport` which the controller no longer uses. The controller is now a thin
HTTP adapter that delegates everything to `McpServer`. Session validation moved to the
server layer.

### Changes needed

1. **Remove `McpSessionService` from the test entirely.** The controller constructor
   no longer takes it. Remove the `@Mock`, remove all `when(sessionService.find(...))`
   stubs, and update the constructor call in `setUp()`.

2. **Remove `BufferingTransport` reference.** The test `callWithSessionUsesBufferingTransport`
   references a class that no longer exists. The controller now uses `OdysseyTransport`
   for all session-bound calls. Rename/rewrite this test to verify that session-bound
   calls create an `OdysseyTransport` and dispatch on a virtual thread.

3. **Fix GET and DELETE tests.** These tests previously used `sessionService.find()` to
   simulate session lookup. The controller no longer does session lookup — it just passes
   the session ID through to `McpServer`. Update these tests:
   - GET `subscribesToNotificationChannel` — remove sessionService stub, just verify
     odyssey.subscribe is called with the session ID
   - GET `returns404ForUnknownSession` — this behavior no longer belongs in the controller
     test. The controller doesn't return 404 for unknown sessions (the server does via
     error responses). Remove this test.
   - GET `resumeWithLastEventIdDelegatesToOdyssey` — remove sessionService stub
   - DELETE `delegatesToProtocolTerminate` — remove sessionService stub, just verify
     protocol.terminate is called
   - DELETE `returns404ForUnknownSession` — remove this test (controller doesn't do
     session lookup)

4. **Fix `McpSession` constructor calls.** The tests use the old 4-arg `McpSession`
   constructor. If the constructor has changed (e.g., to include `initialized` field),
   update all usages.

5. **Verify the controller API matches.** The controller's `handleGet` and `handleDelete`
   now use `@RequestHeader("MCP-Session-Id")` with `required=true` (no null check needed).
   But the tests call `handleGet(null, ...)` and `handleDelete(null, ...)` to test missing
   session. These tests should verify the Spring MVC layer rejects the missing header,
   OR if calling the controller directly, the test for null session ID may need adjustment
   since `@RequestHeader` enforcement only works via MockMvc.

### Current controller constructor

```java
public StreamableHttpController(
    McpServer protocol,
    McpRequestValidator validator,
    Odyssey odyssey,
    ObjectMapper objectMapper,
    byte[] masterKey)
```

No `McpSessionService`.

### Current controller behavior

- POST with no session ID → `SynchronousTransport`, inline call
- POST with session ID → `OdysseyTransport`, virtual thread dispatch, SSE response
- POST notification/response without session ID → 400 (controller checks this)
- GET → subscribes to session SSE channel via Odyssey (no session validation)
- DELETE → calls `protocol.terminate(sessionId)` (no session validation)

## Acceptance criteria

- [ ] `StreamableHttpControllerTest` compiles
- [ ] No references to `McpSessionService` in the test
- [ ] No references to `BufferingTransport` in the test
- [ ] All tests pass in `mocapi-transport-streamable-http`
- [ ] Test coverage for: POST initialize (synchronous), POST with session (SSE),
      POST notification, POST response, GET subscribe, GET resume, DELETE terminate,
      validation (accept header, origin, protocol version)
- [ ] All existing tests across the project still pass

## Implementation notes

- The controller source is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/StreamableHttpController.java`
- The test is at
  `mocapi-transport-streamable-http/src/test/java/com/callibrity/mocapi/transport/http/StreamableHttpControllerTest.java`
- The auto-configuration is at
  `mocapi-transport-streamable-http/src/main/java/com/callibrity/mocapi/transport/http/autoconfigure/StreamableHttpAutoConfiguration.java`
- `SynchronousTransport` and `OdysseyTransport` are in the same package as the controller.
- Do NOT add `McpSessionService` back to the controller. Session validation is the
  server's responsibility.
