# Mocapi vs TypeScript SDK: Streamable HTTP Transport Comparison

**Date:** 2026-04-12
**TS SDK commit:** `main` branch, `packages/server/src/server/streamableHttp.ts`
**Mocapi files:** `StreamableHttpController`, `DefaultMcpServer`, `McpSessionService`, `McpRequestValidator`

---

## 1. HTTP Validation

### Accept Header (POST)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Check** | `acceptHeader?.includes('application/json') \|\| !acceptHeader.includes('text/event-stream')` (line 623) | `MediaType.parseMediaTypes(accept)` checks both `application/json` and `text/event-stream` using Spring's `includes()` (lines 275-285) |
| **Status code** | 406 Not Acceptable | 406 Not Acceptable (via `-32000` JSON-RPC error) |
| **Wildcard handling** | Simple string `.includes()` -- does NOT honor `*/*` or `application/*` | Uses `MediaType.includes()` which correctly matches `*/*`, `application/*`, `text/*` wildcards |

**Finding:** Mocapi is more correct here. The TS SDK's naive `string.includes()` means a client sending `Accept: */*` would be rejected, which is technically wrong per HTTP semantics. Mocapi properly handles media type wildcards.

### Accept Header (GET)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Check** | `text/event-stream` required (line 407) | `text/event-stream` required via `acceptsSse()` (line 134) |
| **Status code** | 406 | 406 |

Both equivalent; Mocapi again handles wildcards properly.

### Content-Type (POST)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Validation** | Explicitly checks `Content-Type: application/json` (lines 632-635), returns 415 Unsupported Media Type | **Not explicitly validated** -- relies on Spring's `@RequestBody` with Jackson deserialization; invalid content-type would result in a 415 from Spring's content negotiation or a parse error |
| **Status code** | 415 | Implicit (Spring framework handles) |

**Finding:** The TS SDK explicitly validates Content-Type and returns a clear 415 with a JSON-RPC error body. Mocapi delegates to Spring framework behavior. This is a minor gap -- Spring does reject non-JSON content types, but the error format may not be a JSON-RPC error envelope.

### Origin Header

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Config** | `allowedOrigins` option, raw string match against `Origin` header value (line 333) | `allowedOrigins` config, extracts hostname via `URI.create(origin).getHost()` and matches against list (line 50) |
| **When absent** | Null origin is **accepted** (only validates if header is present) | Null origin is **accepted** (`isValidOrigin` returns true for null) |
| **Scope** | All methods (POST, GET, DELETE) via `validateRequestHeaders` | All methods (POST line 94, GET line 140, DELETE line 164) |
| **Deprecated** | Yes -- marked `@deprecated`, recommends external middleware | No -- first-class feature |

**Finding:** Mocapi's origin validation is more robust: it parses the URI and matches the hostname, so `https://example.com` and `https://example.com:8080` would both match an allowed host of `example.com`. The TS SDK does exact string matching against the full origin value.

### Host Header (DNS Rebinding)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Support** | `allowedHosts` option validates the `Host` header (lines 321-327) | **Not implemented** -- no Host header validation |
| **Default** | Disabled (`enableDnsRebindingProtection: false`) | N/A |

**Finding:** The TS SDK has Host header validation for DNS rebinding protection (though deprecated). Mocapi has no equivalent. This is a gap, though the TS SDK itself recommends using external middleware for this.

### MCP-Protocol-Version Header

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **When validated** | POST (non-init only, line 697), GET (line 419), DELETE (line 833) | POST only (line 98) -- **not validated on GET or DELETE** |
| **When absent** | **Accepted** -- defaults to negotiated version (line 892) | **Accepted** -- defaults to `InitializeResult.PROTOCOL_VERSION` (line 41) |
| **Known versions** | `['2025-11-25', '2025-06-18', '2025-03-26', '2024-11-05', '2024-10-07']` | `['2025-11-25', '2025-06-18', '2025-03-26', '2024-11-05']` |
| **On init request** | **Skipped** -- version negotiation is done via initialize params, not the header | **Validated** -- will reject an init request with an unknown protocol version header |

**Findings:**
1. Mocapi is missing `2024-10-07` from its known versions list.
2. Mocapi does not validate protocol version on GET or DELETE requests.
3. The TS SDK skips header validation on initialization requests (correct per spec -- version negotiation happens in the initialize params). Mocapi validates the header even on init, which could incorrectly reject a client that sends an older version header during initialization.

### MCP-Session-Id Header

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Missing (non-init)** | 400 Bad Request (line 863) | 400 Bad Request (line 106) |
| **Invalid** | 404 Not Found (line 869) | 404 Not Found (line 109) |
| **On init** | Not required | Not required |
| **DELETE** | Validated via `validateSession` (line 829) | Validated (line 161, `required = true` via Spring) |
| **GET** | Validated via `validateSession` (line 415) | Validated (line 129, `required = true` via Spring) |

Both equivalent.

---

## 2. Session Management

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Model** | **Single-session per transport instance** -- one `sessionId` field on the transport object (line 244) | **Multi-session** -- sessions stored in `McpSessionStore` (pluggable SPI), keyed by session ID |
| **ID generation** | `sessionIdGenerator` callback, optional (line 80). Stateless mode if undefined | `UUID.randomUUID()` in `McpSessionService.initialize()` (line 58). Always stateful |
| **Storage** | In-memory on the transport instance | Pluggable `McpSessionStore` interface -- can be in-memory, Redis, etc. |
| **TTL/Expiry** | No built-in expiry | Built-in TTL with `touch()` on access (line 78 of McpSessionService) |
| **Session data** | No client data stored beyond session ID | Stores `protocolVersion`, `ClientCapabilities`, `Implementation` (clientInfo), `LoggingLevel`, `initialized` flag |
| **Multi-node** | Not supported -- in-memory only. Docs note: "if you are handling HTTP requests from multiple nodes you might want to close each transport after a request" | Designed for multi-node via pluggable `McpSessionStore` and Substrate/Odyssey |

**Finding:** This is the biggest architectural difference. The TS SDK is a single-session-per-transport-instance design. To support multiple sessions, you must create multiple transport instances and route requests yourself. Mocapi has a single controller instance that manages all sessions through a pluggable store.

### Re-initialization Protection

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Check** | If `_initialized && sessionId !== undefined`, rejects with 400 "Server already initialized" (line 670-673) | No explicit re-initialization guard -- a second `initialize` call would create a new session |
| **Batch init** | Rejects if batch contains init + other messages: "Only one initialization request is allowed" (line 676) | Does not support batching at all (single message per request) |

**Finding:** The TS SDK explicitly prevents re-initialization on the same transport. Mocapi allows multiple initialize calls, each creating a new session. Both are valid designs given their session models (single vs multi).

---

## 3. POST Handling

### Message Types

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Requests (calls)** | SSE stream or JSON response depending on `enableJsonResponse` flag | SSE stream (via Odyssey) for post-init, synchronous JSON for init |
| **Notifications** | 202 Accepted, no body (line 710) | 202 Accepted, no body (line 195) |
| **Responses** | 202 Accepted, no body (line 710 -- treated same as notifications) | 202 Accepted, no body (line 200) |

Both equivalent for notifications and responses.

### Batching

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Support** | Full JSON-RPC batching -- `Array.isArray(rawMessage)` check (line 655) | **Not supported** -- `@RequestBody JsonRpcMessage` deserializes a single message only |
| **Mixed batches** | Handles mixed requests/notifications in same batch; only opens SSE if batch contains requests | N/A |

**Finding:** Mocapi does not support JSON-RPC batching. The MCP spec says batching SHOULD be supported.

### SSE vs JSON Response Decision

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **JSON mode** | `enableJsonResponse` option -- collects all responses, sends as JSON body when all ready (lines 725-745) | Only for initialize (via `SynchronousTransport`) |
| **SSE mode** | Default -- opens `ReadableStream`, pushes events via controller | Default for post-init calls (via `OdysseyTransport` + Odyssey SSE) |

**Finding:** The TS SDK has a configurable JSON response mode for simple request/response without streaming. Mocapi only uses JSON response for the initialize handshake; all other calls use SSE. This is fine for spec compliance -- JSON response mode is an optimization, not a requirement.

### Stream Lifecycle (POST SSE)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Stream per request** | Yes -- `crypto.randomUUID()` stream ID per POST (line 715) | Yes -- `UUID.randomUUID()` stream name per call (line 181) |
| **Auto-close** | Closes SSE stream when all responses for the batch are sent (line 1028) | `OdysseyTransport.send()` calls `publisher.complete()` on `JsonRpcResponse` (line 45) |
| **Intermediate messages** | Supported via `send()` with `relatedRequestId` -- notifications can be sent on the request's SSE stream | Supported -- `OdysseyTransport.send()` publishes any `JsonRpcMessage` before the final response |
| **Close callback** | `closeSSEStream` callback passed to message handlers (line 804) for explicit early close | No explicit early-close mechanism |

### Initialize Response

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Session ID delivery** | `mcp-session-id` response header set on the SSE/JSON response (line 769) | `MCP-Session-Id` response header set by `SynchronousTransport.toResponseEntity()` (line 62) |
| **Response format** | SSE stream or JSON (same as any other request) | Always synchronous JSON (via `SynchronousTransport`) |

**Finding:** Both deliver the session ID via response header. Mocapi uses a dedicated synchronous path for initialize which is cleaner -- the client gets a direct JSON response with the header, no SSE needed.

---

## 4. GET Handling (SSE Subscription)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Purpose** | Standalone SSE stream for server-initiated messages (notifications/requests to client) | Same purpose -- subscribes to session's Odyssey topic |
| **Multiple streams** | **One per session** -- rejects with 409 Conflict if a GET stream already exists (line 436) | **Multiple allowed** -- `odyssey.subscribe()` supports multiple subscribers per topic |
| **Session required** | Yes -- validated via `validateSession` | Yes -- `required = true` on `@RequestHeader` |
| **Resumability** | `Last-Event-ID` header triggers replay from `EventStore` (lines 425-429) | `Last-Event-ID` header triggers `reconnect()` with encrypted event ID decryption (line 148) |
| **Response headers** | `Content-Type: text/event-stream`, `Cache-Control: no-cache, no-transform`, `Connection: keep-alive` | Spring's `SseEmitter` defaults (Content-Type set by framework) |
| **Priming event** | Sends an empty-data priming event with event ID for resumability (lines 375-399), only for protocol >= 2025-11-25 | No priming event concept |

**Findings:**
1. The TS SDK enforces one GET SSE stream per session with 409 Conflict. Mocapi allows multiple, which is more flexible for multi-node/multi-subscriber scenarios but differs from the TS SDK's behavior.
2. The TS SDK has a "priming event" concept -- an empty-data SSE event sent at stream open to establish a resumption point. Mocapi does not do this.
3. The TS SDK's priming event includes a `retry` field for controlling client reconnection timing. Mocapi has no equivalent.

---

## 5. DELETE Handling (Session Termination)

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Session validation** | Yes -- `validateSession` + `validateProtocolVersion` (lines 829-836) | Yes -- session existence check (line 168), origin validation (line 164) |
| **Protocol version check** | Yes (line 833) | **No** -- not checked on DELETE |
| **Action** | Calls `onsessionclosed` callback, then `close()` which closes all SSE streams (line 838-839) | Calls `server.terminate(sessionId)` which deletes from session store (line 171) |
| **Response** | 200 OK with null body | 204 No Content |
| **Transport close** | `close()` sets `_closed = true`, closes all stream controllers, calls `onclose` callback (lines 900-915) | Just deletes session from store -- does not close SSE streams |

**Findings:**
1. Response status differs: TS SDK returns 200, Mocapi returns 204 No Content. The spec says "The server MUST respond with HTTP 200 to confirm." -- **Mocapi's 204 may not be spec-compliant.**
2. Mocapi does not validate protocol version on DELETE.
3. The TS SDK closes all SSE streams on DELETE. Mocapi only deletes the session from the store -- existing SSE connections for that session would continue until they naturally close or time out.

---

## 6. Initialization Lifecycle

### Initialize/Initialized Handshake

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Initialize handling** | Transport-level: sets `_initialized = true`, generates session ID (line 678-679). Protocol layer dispatches to server. | `McpSessionService.initialize()` creates session, emits `SessionInitialized` event. `DefaultMcpServer.handleCall()` routes to dispatcher. |
| **Initialized notification** | Not handled at transport level -- dispatched to server via `onmessage` | `DefaultMcpServer.handleNotification()` silently drops `initialize` notifications (line 91). `notifications/initialized` is handled by dispatcher (likely `McpSessionService.markInitialized()`). |
| **Enforcement** | `_initialized` flag gates session validation -- non-init requests before init get 400 "Server not initialized" (line 855) | `DefaultMcpServer.handleCall()` checks `session.initialized()` -- non-ping calls before `notifications/initialized` get `INVALID_REQUEST` error (line 69) |
| **Pre-init allowed** | Only `initialize` request | `initialize` request + `ping` (line 68) |

**Finding:** Both enforce the initialization handshake. Mocapi has a two-phase model:
1. `initialize` call creates the session (but `initialized=false`)
2. `notifications/initialized` marks the session as `initialized=true`
3. Between these, only `ping` is allowed

The TS SDK considers the session initialized after the `initialize` call itself (sets `_initialized = true` at line 679). It does not track a separate "initialized notification received" state at the transport level -- that's a server-level concern.

---

## 7. Error Handling

### Error Response Format

Both use the same JSON-RPC error envelope:
```json
{
  "jsonrpc": "2.0",
  "error": { "code": -32000, "message": "..." },
  "id": null
}
```

The TS SDK optionally includes a `data` field in the error object (line 291-293). Mocapi does not include `data`.

### Error Codes Used

| Error | TS SDK Code | Mocapi Code |
|-------|------------|-------------|
| Accept header invalid | `-32000` | `-32000` |
| Content-Type invalid | `-32000` | N/A (framework) |
| DNS rebinding / Origin | `-32000` | `-32000` |
| Session missing | `-32000` | `-32000` |
| Session not found | `-32001` | `-32001` |
| Server not initialized | `-32000` | N/A (transport-level) |
| Already initialized | `-32600` | N/A (not checked) |
| Batch init violation | `-32600` | N/A (no batching) |
| Parse error | `-32700` | `-32700` |
| Invalid JSON-RPC | `-32700` | `-32700` (via Spring) |
| Method not allowed | `-32000` | N/A (405 from Spring) |
| Protocol version bad | `-32000` | `-32000` |
| Session not initialized | N/A | `-32600` (INVALID_REQUEST) |
| Conflict (duplicate GET) | `-32000` | N/A (multiple allowed) |

### HTTP Status Codes

| Scenario | TS SDK | Mocapi |
|----------|--------|--------|
| Accept invalid | 406 | 406 |
| Content-Type invalid | 415 | 415 (Spring) |
| Parse error | 400 | 400 |
| Session missing | 400 | 400 |
| Session not found | 404 | 404 |
| Origin/Host invalid | 403 | 403 |
| Protocol version bad | 400 | 400 |
| Method not allowed | 405 | 405 (Spring) |
| Duplicate GET stream | 409 | N/A |
| Already initialized | 400 | N/A |
| Successful DELETE | 200 | 204 |
| Notification/Response | 202 | 202 |

---

## 8. DNS Rebinding Protection

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Host validation** | `allowedHosts` list, exact string match against `Host` header | Not implemented |
| **Origin validation** | `allowedOrigins` list, exact string match against `Origin` header | `allowedOrigins` list, hostname extraction via URI parsing |
| **Toggle** | `enableDnsRebindingProtection` flag (default `false`) | Origin validation always active if configured |
| **Status** | All three options deprecated -- "Use external middleware" | First-class feature |

**Finding:** The TS SDK has more complete DNS rebinding protection (both Host and Origin) but has deprecated it in favor of external middleware. Mocapi has Origin validation only but implements it more robustly (hostname extraction vs exact match). Neither validates Host header as a primary defense.

---

## 9. SSE Event Format

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Event type** | `event: message\n` on every event (line 577) | Odyssey's `SseEventMapper` -- event type set from `event.eventType()` if non-null (line 225) |
| **Event ID** | Optional, from `EventStore.storeEvent()` return value (line 579) | Always present -- encrypted `streamName:rawEventId` (line 221) |
| **Data** | `data: ${JSON.stringify(message)}\n\n` | `data: ${objectMapper.writeValueAsString(event.data())}` |
| **Priming event** | Empty data event with ID for resumption anchor (line 394) | No priming events |
| **Retry field** | Optional `retry: ${retryInterval}\n` in priming events (line 396) | Not used |

**Finding:** Mocapi always includes encrypted event IDs (for multi-node resumability). The TS SDK only includes event IDs when an `EventStore` is configured. Mocapi's event ID encryption is a unique security feature -- it prevents clients from guessing or forging event IDs to replay other sessions' events.

---

## 10. Concurrency

| Aspect | TypeScript SDK | Mocapi |
|--------|---------------|--------|
| **Threading model** | Single-threaded Node.js event loop; async/await for I/O | Virtual threads via `Thread.ofVirtual().start()` (line 185) for call handling |
| **Stream safety** | `ReadableStreamDefaultController` is single-writer by design | `OdysseyPublisher` handles thread safety; `SseEmitter` is thread-safe in Spring |
| **Request isolation** | Maps keyed by `RequestId` and `streamId` -- no locking | `ScopedValue` for request-scoped session and transport context -- inherently thread-safe |
| **Batching concurrency** | All messages in a batch dispatched sequentially via `for...of` loop | N/A (no batching) |

**Finding:** Mocapi's use of Java virtual threads and `ScopedValue` is a strength. Each request gets its own virtual thread with scoped session/transport context. The TS SDK is limited by Node.js's single-threaded model but benefits from its simplicity.

---

## 11. Features Mocapi Has That the TS SDK Does Not

### Encrypted Event IDs
Mocapi encrypts SSE event IDs using AES-256-GCM with per-session derived keys (`Ciphers.java`). The event ID contains `streamName:rawEventId` encrypted with the session ID as key context. This prevents:
- Event ID enumeration across sessions
- Forged resumption requests
- Stream name leakage to clients

The TS SDK exposes raw event IDs from the `EventStore`.

### Multi-Node Session Support
Mocapi's `McpSessionStore` SPI allows pluggable storage (Redis, database, etc.) for session data. The `McpSessionService.find()` method calls `store.touch()` to extend TTL on access. The TS SDK is strictly in-memory, single-process.

### Odyssey SSE Infrastructure
Mocapi uses Odyssey for SSE pub/sub, which provides:
- Multiple subscribers per topic (multi-node fan-out)
- Built-in event replay via `odyssey.resume()`
- Stream-name-based routing
- Event persistence (pluggable)

The TS SDK manages streams directly with `ReadableStreamDefaultController`.

### Session TTL with Touch-on-Access
Sessions automatically expire after a configurable TTL. Each access (`find()`) refreshes the TTL. The TS SDK has no session expiry mechanism.

### Initialization Two-Phase Tracking
Mocapi tracks whether `notifications/initialized` has been received via the `initialized` boolean on `McpSession`. Between `initialize` and `notifications/initialized`, only `ping` is allowed. The TS SDK does not enforce this at the transport level.

### Response Correlation Service
`McpResponseCorrelationService` uses Substrate's `Mailbox` primitive for blocking request/response correlation with the client (e.g., for elicitation, sampling). Includes timeout handling and automatic `notifications/cancelled` on timeout. The TS SDK's equivalent is in the server/protocol layer, not the transport.

### Configurable Endpoint Path
`@RequestMapping("${mocapi.endpoint:/mcp}")` allows the endpoint path to be configured. The TS SDK has no built-in path configuration -- that's left to the HTTP framework.

---

## 12. Features the TS SDK Has That Mocapi Does Not

### JSON-RPC Batching
The TS SDK supports full JSON-RPC batching (arrays of messages). Mocapi only processes single messages per request. The MCP spec states batching SHOULD be supported.

### JSON Response Mode
`enableJsonResponse` option allows the server to respond with direct JSON instead of SSE for simple request/response patterns. Useful for stateless/serverless deployments. Mocapi always uses SSE for post-init calls.

### Stateless Mode
When `sessionIdGenerator` is undefined, the TS SDK operates in fully stateless mode -- no session IDs, no session validation. Mocapi always requires sessions.

### Host Header Validation
The TS SDK validates the `Host` header against an allowlist (though deprecated). Mocapi has no Host header validation.

### Priming Events
The TS SDK sends an empty-data SSE event with an event ID when a stream opens (for protocol >= 2025-11-25). This establishes a resumption anchor point before any actual messages are sent. Mocapi does not send priming events.

### SSE Retry Interval
The TS SDK supports a `retryInterval` option that sets the SSE `retry:` field, controlling client reconnection timing. Mocapi does not set retry intervals.

### Explicit Stream Close Callbacks
The TS SDK provides `closeSSEStream` and `closeStandaloneSSEStream` callbacks to message handlers, allowing server logic to close SSE streams early for polling patterns. Mocapi does not expose this capability.

### Duplicate GET Stream Prevention
The TS SDK returns 409 Conflict if a client tries to open a second GET SSE stream on the same session. Mocapi allows multiple GET streams per session.

### Re-initialization Prevention
The TS SDK explicitly rejects re-initialization on an already-initialized transport with 400 "Server already initialized". Mocapi allows multiple initialize calls, each creating a new session.

### Method Not Allowed Response
The TS SDK returns 405 with `Allow: GET, POST, DELETE` header for unsupported HTTP methods (PUT, PATCH, etc.). Mocapi delegates this to Spring framework defaults.

### Protocol Version on DELETE
The TS SDK validates `MCP-Protocol-Version` on DELETE requests. Mocapi does not.

### `2024-10-07` Protocol Version
The TS SDK supports protocol version `2024-10-07`. Mocapi's `McpRequestValidator` does not include this version.

---

## Summary of Action Items

### Spec Compliance Issues (should fix)

1. ~~**DELETE response status**~~ -- Spec is silent on success status. 204 is fine. NOT a bug.
2. ~~**Batching support**~~ -- Spec says body "MUST be a single" message. Batching is PROHIBITED. The TS SDK supports it incorrectly.
3. **Protocol version on init** -- Mocapi validates the header on init requests, but the spec says "all subsequent requests" (init is not subsequent). Should skip header validation for init.

### Recommended Improvements

4. **Protocol version validation on GET/DELETE** -- Add `MCP-Protocol-Version` header validation to GET and DELETE handlers.
5. **Add `2024-10-07`** to known protocol versions in `McpRequestValidator`.
6. **Priming events** -- Consider implementing priming events for resumability anchor points.
7. **SSE retry interval** -- Consider supporting the `retry:` SSE field for client reconnection timing.
8. **Content-Type validation** -- Consider explicit Content-Type validation on POST with a proper JSON-RPC error envelope rather than relying on Spring's default behavior.

### Design Differences (intentional, document and keep)

9. **Multi-session vs single-session** -- Mocapi's multi-session design is a deliberate architectural choice for multi-node support. No change needed.
10. **Multiple GET streams** -- Mocapi allows multiple GET subscribers per session for multi-node fan-out. This is a feature, not a bug.
11. **Encrypted event IDs** -- Unique security feature. Keep.
12. **Session TTL** -- Production feature the TS SDK lacks. Keep.
13. **Re-initialization allowed** -- Follows naturally from multi-session design. Keep.
14. **No stateless mode** -- Mocapi is designed for stateful multi-node deployments. Stateless mode could be added later if needed.
