# Compat: SSE streaming content and replay

## What to build

Compliance tests for actual SSE stream behavior — priming events, event IDs,
streaming tool responses, and Last-Event-ID replay. These go beyond the guard
rail tests in SseStreamIT which only verify status codes.

### Test fixture

Add a streaming tool to the compat test application. This tool should:
- Accept a message parameter
- Use `McpStreamContext` to send a progress notification
- Return a structured content result

This exercises the full streaming path: SSE emitter returned in metadata,
progress notification sent mid-execution, final result sent, stream closed.

### StreamingToolIT

**Priming event:**
- GET SSE stream returns a priming event (empty JSON `{}`) as the first event
- Priming event has an `id` field (encrypted event ID)

**Streaming tool call:**
- POST `tools/call` for streaming tool with `Accept: application/json, text/event-stream`
  returns SSE stream (not JSON)
- Stream contains the JSON-RPC result with structured content
- Stream is properly terminated

**Last-Event-ID replay:**
- GET with valid `Last-Event-ID` replays events after that ID
- GET with invalid/tampered `Last-Event-ID` returns 400

### Implementation notes

- MockMvc handles SSE via `asyncDispatch()`. Use `MvcResult.getAsyncResult()` to
  get the `SseEmitter`, or assert on the response content directly.
- The streaming tool needs `McpStreamContext` injected — verify the
  `McpStreamContextScopedValueResolver` works in the compat context.
- For Last-Event-ID replay, we need to capture an event ID from a prior stream
  and use it in a subsequent GET request.
- Event IDs are encrypted — we don't need to decode them, just verify they're
  present and that replay with a captured ID works.

## Acceptance criteria

- [ ] Streaming test tool added to compat application
- [ ] Priming event verified on GET SSE stream
- [ ] Streaming tool call returns SSE (not JSON)
- [ ] Last-Event-ID replay returns events
- [ ] Invalid Last-Event-ID returns 400
- [ ] `mvn verify` passes
