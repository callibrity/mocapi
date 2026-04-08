# Simplify controller to use JsonRpcRequest directly

## What to build

`McpStreamingController` is still parsing `JsonNode` manually despite RipCurl being
integrated. The POST handler should accept `@RequestBody JsonRpcRequest` and let
Spring + RipCurl handle deserialization and validation. This eliminates most of the
manual JSON-RPC handling code.

### Change POST body to `JsonRpcRequest`

Replace `@RequestBody JsonNode body` with `@RequestBody JsonRpcRequest request`.
Spring deserializes the JSON into RipCurl's typed record. The controller accesses
`request.method()`, `request.id()`, `request.params()` directly — no more
`body.get("method").asString()`.

### Remove manual envelope validation

Delete the `validator.validateJsonRpcEnvelope(body)` call. RipCurl's
`JsonRpcDispatcher.dispatch()` validates `jsonrpc: "2.0"`, method name, and ID type
already. It throws `JsonRpcException(INVALID_REQUEST, ...)` on failure.

### Remove `McpRequestValidator.validateJsonRpcEnvelope()`

This method is now dead code. Delete it and its `ValidationResult` record.
`McpRequestValidator` keeps only the MCP-specific methods: `isValidProtocolVersion()`,
`isValidOrigin()`, and the Accept header helpers (which could also move to the
controller since they're HTTP concerns).

### Simplify notification/response detection

Instead of `McpRequestValidator.isNotificationOrResponse(body)` parsing `JsonNode`,
use `JsonRpcRequest` fields:

```java
boolean isNotification = request.method() != null && request.id() == null;
boolean isResponse = request.method() == null
    && (request.params() != null); // or check for result/error fields
```

Actually — JSON-RPC responses have `result` or `error`, not `method`. A `JsonRpcRequest`
record has `method` and `params`. If the client sends a response (with `result`/`error`),
Spring will deserialize it into a `JsonRpcRequest` with null `method` and null `params`.
The `result`/`error` fields will be lost since they're not in `JsonRpcRequest`.

This means we may need to keep `JsonNode` for the notification/response detection
case, OR add response handling at the `JsonNode` level before attempting `JsonRpcRequest`
deserialization. The simplest approach: attempt `JsonRpcRequest` deserialization. If
`method` is null, treat as a response/notification and handle accordingly.

For notifications: `method != null && id == null` → dispatch via RipCurl (which returns
null for notifications) → return 202.

For responses (elicitation answers): `method == null` → this is a client response to
a server-initiated request. Route by `id` to a mailbox. This case can use the raw
`id` field from the request.

### Remove manual response formatting

`successResponse(JsonRpcResponse)` and `errorResponse()` manually build `ObjectNode`
responses. RipCurl's `JsonRpcResponse` already IS the JSON-RPC response — just return
it directly. Spring serializes it via Jackson.

For error cases (MCP header failures that happen before dispatch), we still need to
build error responses manually since RipCurl isn't involved. Keep a minimal error
helper for those cases.

### Handle `JsonRpcException` via `@ExceptionHandler`

Instead of try/catch in `dispatch()`, add an `@ExceptionHandler(JsonRpcException.class)`
method that maps the exception to a JSON-RPC error response. This removes the try/catch
from the happy path.

### Resulting controller structure

```java
@PostMapping
public ResponseEntity<?> handlePost(
    @RequestBody JsonRpcRequest request,
    @RequestHeader("MCP-Session-Id", required = false) String sessionId,
    @RequestHeader("MCP-Protocol-Version", required = false) String protocolVersion,
    @RequestHeader("Accept", required = false) String accept,
    @RequestHeader("Origin", required = false) String origin) {

    // MCP header validation
    validateAcceptHeader(accept);
    validateProtocolVersion(protocolVersion);
    validateOrigin(origin);

    // Notification → dispatch and return 202
    if (request.method() != null && request.id() == null) {
        validateSession(sessionId);
        dispatcher.dispatch(request);
        return ResponseEntity.accepted().build();
    }

    // Session validation (skip for initialize)
    if (!INITIALIZE.equals(request.method())) {
        validateSession(sessionId);
    }

    // Dispatch — RipCurl handles validation, routing, invocation
    JsonRpcResponse response = dispatcher.dispatch(request);

    // Initialize → store session
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON);
    if (INITIALIZE.equals(request.method())) {
        builder.header("MCP-Session-Id", createSession(request.params()));
    }
    return builder.body(response);
}
```

That's the core of it. ~30 lines of actual logic.

### Cache `InitializeResponse` at startup

`McpServer.initialize()` currently computes the response per-request, dynamically
checking client capabilities to decide what server capabilities to advertise. This is
wrong — the server advertises what IT supports, not what the client supports. The
client's capabilities are stored in the session for gating later (when the server
wants to use elicitation/sampling).

Build the `InitializeResponse` once at startup and cache it. The `initialize` handler
just returns the cached instance:

```java
@JsonRpc("initialize")
public McpServer.InitializeResponse initialize() {
    return cachedInitializeResponse;
}
```

Remove the `protocolVersion`, `capabilities`, `clientInfo` parameters from the
`initialize` handler method. The controller still extracts those from `request.params()`
to create the `McpSession` and store it, but the handler itself doesn't need them.

Remove the dynamic capability negotiation logic from `McpServer.initialize()` — the
`McpServer` builds the `InitializeResponse` in its constructor and exposes it as a
getter.

## Acceptance criteria

- [ ] POST handler accepts `@RequestBody JsonRpcRequest`
- [ ] No manual `JsonNode` envelope validation in the controller
- [ ] `McpRequestValidator.validateJsonRpcEnvelope()` and `ValidationResult` are deleted
- [ ] `McpRequestValidator.isNotificationOrResponse()` is deleted
- [ ] Notifications are detected via `request.method() != null && request.id() == null`
- [ ] `JsonRpcResponse` is returned directly, not manually rebuilt as `ObjectNode`
- [ ] `JsonRpcException` is handled via `@ExceptionHandler` or minimal catch
- [ ] Manual `successResponse()` and `errorResponse()` methods are removed (except
      for pre-dispatch MCP header errors)
- [ ] Controller is under 150 lines
- [ ] All existing MCP protocol behaviors preserved
- [ ] `InitializeResponse` is built once at startup and cached
- [ ] `initialize` handler takes no parameters, returns cached response
- [ ] Server capabilities are static, not computed per-request from client capabilities
- [ ] Client capabilities are still stored in `McpSession` for runtime gating
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- `JsonRpcRequest` is a record: `(String jsonrpc, String method, JsonNode params, JsonNode id)`.
  Spring's Jackson integration deserializes it from the POST body.
- Responses from the client (elicitation answers) have no `method` field. They have
  `id`, `result`, and/or `error`. Since `JsonRpcRequest` doesn't have `result`/`error`
  fields, we may need a separate `@PostMapping` or conditional deserialization for
  this case. Alternatively, use `JsonNode` only for the response routing path and
  `JsonRpcRequest` for everything else. This is a design decision for the implementer.
- The `@ExceptionHandler` approach is cleaner than try/catch but requires the request
  ID to be available for the error response. Store it in a request attribute (like
  the old RipCurl controller did) or capture it in the exception handler via the
  `JsonRpcRequest`.
- The Accept header helpers (`acceptsJsonAndSse`, `acceptsSse`) can stay as private
  methods on the controller — they're HTTP concerns.
