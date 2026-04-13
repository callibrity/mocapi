# Update compat tests to assert JSON-RPC error bodies on HTTP errors

## What to build

The controller now returns JSON-RPC error bodies on all HTTP-level errors, matching
the official MCP TypeScript SDK pattern. Every error response has the format:

```json
{"jsonrpc":"2.0","error":{"code":<code>,"message":"<message>"},"id":null}
```

The compat tests that were just updated (spec 157) to remove JSON body assertions
now need to be updated again to assert the new consistent JSON-RPC error format.

### Error codes and messages

| Scenario | HTTP Status | Code | Message |
|---|---|---|---|
| Missing Accept header (POST) | 406 | -32000 | "Not Acceptable: Client must accept both application/json and text/event-stream" |
| Missing Accept header (GET) | 406 | -32000 | "Not Acceptable: Client must accept text/event-stream" |
| Invalid Origin | 403 | -32000 | "Forbidden: Invalid Origin" |
| Invalid protocol version | 400 | -32000 | "Bad Request: Unsupported protocol version: \<value\>" |
| Missing MCP-Session-Id | 400 | -32000 | "Bad Request: MCP-Session-Id header is required" |
| Unknown session ID | 404 | -32001 | "Session not found" |
| Invalid JSON body | 400 | -32700 | "Parse error: \<details\>" |

All responses have `Content-Type: application/json` and `"id": null`.

### Tests to update

**SessionManagementIT:**
- `postWithoutSessionIdOnNonInitializeReturns400` — assert status 400, jsonPath
  `$.error.code` = -32000, `$.error.message` = "Bad Request: MCP-Session-Id header is required",
  `$.id` is null
- `postWithUnknownSessionIdReturns404` — assert status 404, `$.error.code` = -32001,
  `$.error.message` = "Session not found"
- `postToDeletedSessionReturns404` — same as above
- `deleteWithUnknownSessionIdReturns404` — assert status 404, `$.error.code` = -32001,
  `$.error.message` = "Session not found"
- `deleteWithoutSessionIdReturns400` — this may be handled by Spring MVC for the
  missing required header, not our controller. Check what Spring returns and assert
  accordingly. If Spring returns its own error format, that's fine — do not change it.

**ClientResponseIT:**
- `clientResultWithoutSessionIdReturns400` — assert status 400, `$.error.code` = -32000,
  `$.error.message` = "Bad Request: MCP-Session-Id header is required"
- `clientErrorWithoutSessionIdReturns400` — same

**PostEndpointIT (if it exists):**
- Any tests for Accept header validation — assert `$.error.code` = -32000
- Any tests for Origin validation — assert `$.error.code` = -32000

### Do NOT change

- The controller — it is already correct
- The mocapi-server compliance tests — they test `requiresSession`/`sessionExists`,
  not HTTP error bodies
- Any test that passes currently

## Acceptance criteria

- [ ] All session validation compat tests assert JSON-RPC error body format
- [ ] Assertions check `$.error.code`, `$.error.message`, and `$.id` is null
- [ ] All compat tests that were passing before still pass
- [ ] All mocapi-server tests still pass
- [ ] No changes to the controller

## Implementation notes

- The JSON-RPC error body has `"id": null` (not absent)
- Use `jsonPath("$.id").value(IsNull.nullValue())` or equivalent for null assertion
- The error body is NOT a full JSON-RPC response envelope — it has `jsonrpc`, `error`,
  and `id` fields (matching the TypeScript SDK pattern)
- `deleteWithoutSessionIdReturns400` may return Spring's default error response since
  `@RequestHeader("MCP-Session-Id")` is `required=true` on DELETE — Spring intercepts
  before our controller code runs. Assert whatever Spring returns.
