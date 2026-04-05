# Implement GET SSE endpoint and pagination

## What to build

Two MCP spec compliance gaps: the GET endpoint for server-initiated notifications and
cursor-based pagination for list operations.

### GET SSE endpoint for server-initiated notifications

**File:** `McpStreamingController.java`

The MCP 2025-11-25 Streamable HTTP transport requires the GET endpoint to establish a
persistent SSE connection for server-initiated messages (notifications, progress updates).
Currently it returns 405 Method Not Allowed.

Implement the GET endpoint:
1. Validate `MCP-Session-Id` header — return 400 if missing, 404 if session not found
2. Validate `MCP-Protocol-Version` header
3. Create an `McpStreamEmitter` tied to the session
4. Send a priming event per spec
5. If `Last-Event-ID` header is present, replay any stored events after that ID
6. Keep the SSE connection open for server-initiated messages
7. Register the emitter with the session so other parts of the system can push
   notifications through it

Add a method to `McpSession` to register and manage notification emitters:
- `registerNotificationEmitter(McpStreamEmitter emitter)` — stores the emitter for push
- `sendNotification(Object notification)` — sends to all registered emitters
- Clean up emitters on completion/error/timeout

### Cursor-based pagination for list operations

**Files:** `McpToolsCapability.java`, `McpPromptsCapability.java`

Both `listTools(String cursor)` and `listPrompts(String cursor)` accept a cursor but
ignore it, always returning all items. Implement basic cursor-based pagination:

1. Define a page size (configurable via `mocapi.pagination.page-size`, default 50)
2. When `cursor` is null, return the first page
3. Encode the cursor as a base64-encoded offset (simple and stateless)
4. Return `nextCursor` when there are more items beyond the current page
5. Return `nextCursor = null` when all items have been returned

Since most MCP servers will have a small number of tools/prompts, the pagination should
be lightweight. The cursor encoding should be simple (offset-based) not complex
(keyset-based).

## Acceptance criteria

- [ ] GET endpoint returns SSE stream with priming event for valid sessions
- [ ] GET endpoint returns 400 if `MCP-Session-Id` header is missing
- [ ] GET endpoint returns 404 if session is not found or expired
- [ ] GET endpoint replays events after `Last-Event-ID` if provided
- [ ] SSE connection stays open for server-initiated notifications
- [ ] `listTools` returns paginated results when items exceed page size
- [ ] `listTools` returns all items in one page when count is within page size
- [ ] `listTools` with a valid cursor returns the next page
- [ ] `listPrompts` has the same pagination behavior as `listTools`
- [ ] `nextCursor` is null on the last page
- [ ] Unit tests exist for pagination logic
- [ ] Unit tests exist for GET endpoint behavior
- [ ] `mvn verify` passes

## Implementation notes

- This spec should be placed in `specs/backlog/` until specs 001-006 are complete,
  then promoted to `specs/` when ready.
- The GET endpoint needs to handle client disconnects gracefully — the `McpStreamEmitter`
  already has idempotent completion and error handling for this.
- For pagination, consider extracting a shared pagination utility since both tools and
  prompts use the same logic.
- The page size of 50 is generous — most MCP servers will have fewer tools/prompts than
  that, so pagination will rarely be needed in practice. But spec compliance requires it.
