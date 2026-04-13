# Fix compat MockMvc tests for async SSE responses

## What to build

The compat integration tests fail because session-bound POST calls now return SSE
streams via async dispatch (virtual thread + OdysseyTransport). MockMvc requires
the `asyncDispatch()` pattern to handle these responses.

### The problem

When a POST call has a session ID, the controller:
1. Creates an OdysseyTransport
2. Starts a virtual thread for `server.handleCall()`
3. Returns an SseEmitter immediately

MockMvc sees the async dispatch but the tests call `.andExpect()` on the initial
result, which has no body. They need to use `asyncDispatch()` to get the completed
SSE response.

### The fix

For session-bound calls that return SSE, the test pattern should be:

```java
MvcResult mvcResult = client.post(sessionId, "tools/call", params, id)
    .andExpect(request().asyncStarted())
    .andReturn();

mockMvc.perform(asyncDispatch(mvcResult))
    .andExpect(status().isOk())
    .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM))
    .andExpect(/* assertions on SSE event data */);
```

### McpClient changes

`McpClient` needs methods that support async SSE responses. Options:

1. Add a `postAsync()` method that returns the `MvcResult` for async dispatch
2. Or update `post()` to detect async and handle it transparently
3. The SSE response body contains JSON-RPC messages as SSE events — the test needs
   to parse the event data to assert on `$.result.*`

### Tests that need updating

Every test that calls `client.post(sessionId, ...)` with a non-null session ID for
a JSON-RPC call (not notifications/responses) needs the async pattern. This includes:

- All `ToolsCall*IT` tests (SimpleText, Image, Audio, EmbeddedResource, MixedContent,
  Error, McpToolParams)
- All `PromptsGet*IT` tests (Simple, WithArgs, EmbeddedResource, WithImage)
- `PromptsListIT`, `ToolsListIT`, `ResourcesListIT`
- `FullConversationIT`
- `SessionManagementIT.postWithSessionIdSucceeds`
- Any test that does a `tools/call`, `tools/list`, `prompts/list`, `prompts/get`,
  `resources/list`, `resources/read`, `ping`, or `logging/setLevel` with a session

### Session management tests

The session validation tests (`postWithoutSessionId`, `postWithUnknownSession`, etc.)
return synchronous error responses (400, 404) — these do NOT need async dispatch.
These are HTTP-level errors, not JSON-RPC errors — there is no JSON-RPC request to
respond to. The controller returns empty bodies with the appropriate HTTP status code.

Update the compat tests to assert only the HTTP status code, removing any
`jsonPath("$.error.*")` assertions:
- `postWithoutSessionIdOnNonInitializeReturns400` — assert `status().isBadRequest()` only
- `postWithUnknownSessionIdReturns404` — assert `status().isNotFound()` only
- `postToDeletedSessionReturns404` — assert `status().isNotFound()` only
- `deleteWithUnknownSessionIdReturns404` — assert `status().isNotFound()` only
- `deleteWithoutSessionIdReturns400` — assert `status().isBadRequest()` only

### ClientResponseIT

Tests that send client responses/notifications without session IDs — these return
HTTP 400 with empty bodies (HTTP-level error). Update to assert only status code,
no JSON-RPC error body assertions.

### FullConversationIT

This test manually sends `notifications/initialized` but `McpClient.initialize()`
now sends it automatically. Either:
- Use `initializeResult()` instead of `initialize()` and send the notification manually
- Or remove the manual notification send (since `initialize()` handles it)

## Acceptance criteria

- [ ] All 79 compat integration tests pass with MockMvc
- [ ] Session-bound calls use `asyncDispatch()` pattern
- [ ] Session error responses (400, 404) include JSON-RPC error bodies
- [ ] No duplicate `notifications/initialized` sends
- [ ] npx conformance suite still passes (minus completion/subscribe)
- [ ] All mocapi-server tests still pass

## Implementation notes

- MockMvc async dispatch docs:
  https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html
- `asyncDispatch()` is a static import from
  `org.springframework.test.web.servlet.request.MockMvcRequestBuilders`
- SSE event data format: `data:{"jsonrpc":"2.0","result":{...},"id":N}\n\n`
- The SSE response may contain multiple events (progress, log, result) — the last
  event contains the JSON-RPC result
- `McpClient` is at `mocapi-compat/src/test/java/com/callibrity/mocapi/compat/McpClient.java`
- Consider adding a helper to `McpClient` that extracts the last SSE event's data
  as a JSON node for assertion
