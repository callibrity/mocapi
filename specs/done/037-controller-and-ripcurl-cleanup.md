# Controller and RipCurl integration cleanup

## What to build

Clean up the controller and related code to properly leverage RipCurl 0.7.0 features
and eliminate manual JSON-RPC envelope construction.

### Use `@RequestBody JsonRpcRequest` on POST handler

Replace `@RequestBody JsonNode body` with `@RequestBody JsonRpcRequest request`.
Spring deserializes the JSON into RipCurl's typed record. Access `request.method()`,
`request.id()`, `request.params()` directly.

**Exception**: JSON-RPC responses from clients (elicitation answers) have `result`
or `error` fields that `JsonRpcRequest` doesn't model. Detect these BEFORE
attempting `JsonRpcRequest` deserialization, or handle deserialization failure
gracefully. Alternatively, keep `JsonNode` for the initial parse and construct
`JsonRpcRequest` only for actual requests/notifications.

If keeping `JsonNode`, at minimum remove the manual `jsonrpc` version check — let
RipCurl's dispatcher handle it.

### Delete `com.callibrity.mocapi.JsonRpcProtocol`

This is a duplicate of `com.callibrity.ripcurl.core.JsonRpcProtocol`. Delete it and
update all imports to use RipCurl's version.

### Use `JsonRpcResponse` metadata instead of `AtomicReference<SseEmitter>`

The `ToolMethodInvoker.SSE_EMITTER_HOLDER` `ScopedValue<AtomicReference<SseEmitter>>`
pattern is complex. Replace with `JsonRpcResponse.withMetadata("emitter", emitter)` —
the handler returns a `JsonRpcResponse` with the emitter attached, the controller
reads it via `response.getMetadata("emitter", SseEmitter.class)`.

This eliminates:
- `ToolMethodInvoker.SSE_EMITTER_HOLDER` ScopedValue
- `AtomicReference<SseEmitter>` in the controller
- The `emitterRef.get()` check after dispatch

### Use `JsonRpcResponse` metadata instead of `REQUEST_ID` ScopedValue

The `ToolMethodInvoker.REQUEST_ID` ScopedValue passes the request ID to the virtual
thread. Instead, the handler already has access to the request ID from the
`JsonRpcRequest` parameter. Remove the ScopedValue.

### Create `JsonRpcErrorResponse` and `JsonRpcError` records in Mocapi

Create in `mocapi-core` (e.g., `com.callibrity.mocapi.http` or a shared package):

```java
public record JsonRpcError(int code, String message) {}

public record JsonRpcErrorResponse(String jsonrpc, JsonRpcError error, JsonNode id) {
    public JsonRpcErrorResponse(JsonRpcError error, JsonNode id) {
        this(JsonRpcProtocol.VERSION, error, id);
    }
}
```

Replace all manual `errorResponse()` / `buildJsonRpcError()` helpers with:

```java
new JsonRpcErrorResponse(new JsonRpcError(code, message), id)
```

Note: these should eventually move to RipCurl as first-class types.

### Remove manual JSON-RPC version validation in controller

Lines 87-90 check `VERSION.equals(body.path("jsonrpc").asString(null))`. RipCurl's
dispatcher already validates this. Remove the duplicate check.

### Simplify `dispatch()` method

The `dispatch()` method has two code paths for ScopedValue setup (with session and
without). Simplify — the controller should always set `McpSession.CURRENT` for
non-initialize requests, and the dispatch logic shouldn't branch on it.

### Remove `McpSession.CURRENT_ID` ScopedValue

If `McpSession.CURRENT` is set, the session ID can be looked up from the session
service. Or store the session ID as a field on `McpSession` itself. No need for a
separate ScopedValue.

### Fix duplicate `parseResponse()` in `DefaultMcpStreamContext`

Two nearly identical methods for `Class<T>` and `TypeReference<T>` — extract the
common logic into one method.

### Fix duplicate null-check patterns in `DefaultMcpStreamContext`

`currentLogLevel()` and `currentSession()` repeat the same guard clause. Extract
a shared helper.

### Break up `sendElicitationAndWait()` in `DefaultMcpStreamContext`

26 lines mixing request building, publishing, waiting, and error handling. Split into
smaller focused methods.

### Extract `toJsonTree()` to a shared utility

Currently local to `DefaultMcpStreamContext`. The null-stripping pattern
(`node.properties().removeIf(e -> e.getValue().isNull())`) should be reusable.

### Fix `McpLoggingMethods.setLevel()` return value

Returns `new Object() {}` — should return a proper empty result or null.

### Use `JsonRpcRequest.notification()` in `DefaultMcpStreamContext`

Progress, logging, and other notifications should use the RipCurl static factory
instead of manual `JsonRpcRequest` construction.

## Acceptance criteria

- [ ] `com.callibrity.mocapi.JsonRpcProtocol` is deleted
- [ ] All references use `com.callibrity.ripcurl.core.JsonRpcProtocol`
- [ ] `ToolMethodInvoker.SSE_EMITTER_HOLDER` is replaced with response metadata
- [ ] `ToolMethodInvoker.REQUEST_ID` ScopedValue is removed
- [ ] `AtomicReference<SseEmitter>` is removed from controller
- [ ] Controller checks `response.getMetadata("emitter", SseEmitter.class)` after dispatch
- [ ] Manual `errorResponse()` helper is simplified or removed
- [ ] Duplicate JSON-RPC version validation is removed from controller
- [ ] `dispatch()` method is simplified
- [ ] Controller is under 200 lines
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- The JSON-RPC response routing for elicitation answers (lines 101-103, 256-277)
  still needs `JsonNode` parsing since `JsonRpcRequest` doesn't have `result`/`error`
  fields. This is fine — client responses are a special case. Consider adding a
  `JsonRpcClientResponse` record or just keep the `JsonNode` parsing for that path.
- `McpSession.CURRENT_ID` — check if the session ID is needed independently of the
  session object. If it's always available alongside `McpSession.CURRENT`, it's
  redundant. If the session ID is needed in places where the full session isn't
  loaded (e.g., encryption), consider adding `sessionId` as a field on `McpSession`.
- The `DefaultMcpStreamContext` should use `JsonRpcRequest.notification()` for
  building progress/logging notifications instead of manual `ObjectNode` construction.
  Check if spec 035 already addresses this.
