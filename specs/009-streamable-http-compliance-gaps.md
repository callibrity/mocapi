# Fix Streamable HTTP transport compliance gaps

## What to build

Fix five gaps in the MCP 2025-11-25 Streamable HTTP transport implementation identified
during spec compliance audit.

### Gap 1: Notifications must return 202 Accepted, not SSE

**Spec:** "If the input is a JSON-RPC notification or response, the server MUST return
HTTP status code 202 Accepted with no body."

Currently `notifications/initialized` is routed through the SSE stream flow like any
other method. Instead, the POST handler must detect when the incoming JSON-RPC message is
a notification (no `id` field) or a response (has `result` or `error` field), and return
`202 Accepted` with no body immediately — without creating an SSE stream.

A JSON-RPC notification has a `method` but no `id`. A JSON-RPC response has `result` or
`error` but no `method`. Detect both cases early in the POST handler, before creating the
`McpStreamEmitter`.

For notifications: still invoke the method (e.g., `clientInitialized()`), but return 202.
For responses: accept and return 202 (even if we don't process client responses yet).

### Gap 2: DELETE endpoint for session termination

**Spec:** "Clients SHOULD send HTTP DELETE to the MCP endpoint with MCP-Session-Id to
terminate the session. The server MAY respond with 405 Method Not Allowed."

Add a `@DeleteMapping` to `McpStreamingController` that:
1. Reads `MCP-Session-Id` header
2. If missing, returns 400 Bad Request
3. If session not found, returns 404 Not Found
4. If found, calls `sessionManager.terminateSession(sessionId)` and returns 204 No Content

### Gap 3: Remove Last-Event-ID from POST, resumption is GET-only

**Spec:** "If the client wishes to resume after a disconnection, it SHOULD issue an HTTP
GET to the MCP endpoint, and include the Last-Event-ID header. This mechanism applies
regardless of how the original stream was initiated (via POST or GET). Resumption is
always via HTTP GET with Last-Event-ID."

Remove the `Last-Event-ID` header parameter and replay logic from the POST handler. The
POST handler should not accept or process `Last-Event-ID`. Resumption is exclusively a
GET concern.

### Gap 4: Event IDs must encode stream identity

**Spec:** "Event IDs SHOULD encode sufficient information to identify the originating
stream, enabling the server to correlate a Last-Event-ID to the correct stream."

Current event ID format is `sessionId:counter` which doesn't identify the stream.
Change to `sessionId:streamId:counter` (or similar) so the server can extract the
originating stream ID from a `Last-Event-ID` value.

Update `McpSession.nextEventId()` to accept the stream ID and include it in the event ID.
Update `McpSession.getEventsAfter()` to extract the stream ID from the `Last-Event-ID`
and look up events from that specific stream (not the current/new stream).

Update the GET handler's resumption logic to use the extracted stream ID:
```
// Extract original stream ID from Last-Event-ID
String originalStreamId = extractStreamId(lastEventId);
var replayEvents = session.getEventsAfter(originalStreamId, lastEventId);
```

**Important:** The spec says "The server MUST NOT replay messages that would have been
delivered on a different stream." This means the server must only replay events from the
original stream identified by the event ID.

### Gap 5: Validate client Accept header

**Spec:** "The client MUST include an Accept header, listing both application/json and
text/event-stream as supported content types."

On POST: validate that the `Accept` header includes both `application/json` and
`text/event-stream`. If not, return 406 Not Acceptable.

On GET: validate that the `Accept` header includes `text/event-stream`. If not, return
406 Not Acceptable.

Use `@RequestHeader(value = "Accept", required = false)` and check for the required
media types. Be lenient with parsing — `Accept: */*` should be accepted.

## Acceptance criteria

- [ ] POST with a JSON-RPC notification (method present, no id) returns 202 Accepted with no body
- [ ] POST with a JSON-RPC response (result/error present, no method) returns 202 Accepted with no body
- [ ] `notifications/initialized` still calls `mcpServer.clientInitialized()` before returning 202
- [ ] DELETE endpoint exists on the MCP endpoint
- [ ] DELETE with valid session ID terminates session and returns 204
- [ ] DELETE with missing session ID returns 400
- [ ] DELETE with unknown session ID returns 404
- [ ] POST handler does NOT accept `Last-Event-ID` header
- [ ] Event IDs encode stream identity (format includes stream ID)
- [ ] GET resumption with `Last-Event-ID` replays events from the *original* stream, not the new stream
- [ ] GET resumption does NOT replay events from a different stream
- [ ] POST without proper `Accept` header returns 406
- [ ] GET without `text/event-stream` in `Accept` returns 406
- [ ] `Accept: */*` is accepted on both POST and GET
- [ ] Unit tests exist for all new behaviors
- [ ] `mvn verify` passes

## Implementation notes

- All changes are in `McpStreamingController.java`, `McpSession.java`, and
  `McpStreamEmitter.java`.
- For Gap 1, the notification/response detection must happen BEFORE the session
  lookup logic that creates anonymous sessions. A notification like
  `notifications/initialized` will have a session ID header — use it to find the session,
  invoke the handler, and return 202.
- For Gap 4, consider the event ID format `{streamId}:{counter}` (drop sessionId since
  events are already scoped to a session). The stream ID is a UUID, counter is a long.
  This keeps IDs shorter while still enabling stream identification.
- For Gap 5, Spring's `MediaType.parseMediaTypes()` can help parse the Accept header.
  Remember that `*/*` matches everything.
- The POST handler currently always creates an `McpStreamEmitter`. After this change,
  it should only create one for JSON-RPC *requests* (messages with an `id` field).
