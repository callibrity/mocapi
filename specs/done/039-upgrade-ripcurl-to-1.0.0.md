# Upgrade RipCurl to 1.0.0

## What to build

Upgrade Mocapi from RipCurl 0.7.0 to 1.0.0. This is a breaking upgrade — the
annotation was renamed, the response type is now a sealed hierarchy, error codes
moved, and the dispatcher no longer throws.

### Bump version

In parent `pom.xml`, change:

```xml
<ripcurl.version>1.0.0</ripcurl.version>
```

### Rename `@JsonRpc` to `@JsonRpcMethod`

Replace all occurrences across the codebase:

- `@JsonRpc("initialize")` → `@JsonRpcMethod("initialize")`
- `@JsonRpc("ping")` → `@JsonRpcMethod("ping")`
- `@JsonRpc("notifications/initialized")` → `@JsonRpcMethod("notifications/initialized")`
- `@JsonRpc("tools/list")` → `@JsonRpcMethod("tools/list")`
- `@JsonRpc("tools/call")` → `@JsonRpcMethod("tools/call")`
- `@JsonRpc("logging/setLevel")` → `@JsonRpcMethod("logging/setLevel")`
- All imports: `import ...annotation.JsonRpc` → `import ...annotation.JsonRpcMethod`

### Replace `JsonRpcResponse` with sealed types

`JsonRpcResponse` is now a sealed interface with `JsonRpcResult` and `JsonRpcError`.

In `ToolMethodInvoker`:
- `new JsonRpcResponse(NullNode.getInstance(), requestId)` → `new JsonRpcResult(NullNode.getInstance(), requestId)`
- `new JsonRpcResponse(VERSION, objectMapper.valueToTree(result), requestId)` → `new JsonRpcResult(objectMapper.valueToTree(result), requestId)`
- `.withMetadata(...)` stays the same (it's on `JsonRpcResult`)
- Import `JsonRpcResult` instead of `JsonRpcResponse` where constructing success responses

In `StreamableHttpController`:
- The dispatcher returns `JsonRpcResponse` (the sealed interface). Pattern match:
  ```java
  return switch (dispatcher.dispatch(request)) {
      case null -> ResponseEntity.accepted().build();
      case JsonRpcResult result -> handleResult(result, request);
      case JsonRpcError error -> ResponseEntity.ok()
          .contentType(APPLICATION_JSON).body(error);
  };
  ```
- Remove try/catch for `JsonRpcException` — the dispatcher catches everything and
  returns `JsonRpcError`. The controller never needs exception handling for dispatch.
- Remove manual `errorResponse()` helper for JSON-RPC errors — replaced by
  `JsonRpcError` type.
- Keep `errorResponse()` only for MCP transport errors (missing session, bad headers)
  that happen BEFORE dispatch.

### Replace `JsonRpcException.CONSTANT` with `JsonRpcProtocol.CONSTANT`

Wait — error codes are still on `JsonRpcException` in 1.0.0. They move to
`JsonRpcProtocol` in 1.1.0. For 1.0.0, keep using `JsonRpcException.INVALID_PARAMS`
etc. No change needed here.

### Delete `com.callibrity.mocapi.JsonRpcProtocol`

Mocapi has its own `JsonRpcProtocol` class that duplicates RipCurl's. Delete it and
use `com.callibrity.ripcurl.core.JsonRpcProtocol` everywhere.

### Simplify controller dispatch

The dispatcher never throws in 1.0.0. Remove the try/catch around `dispatch()`.
The response is always `JsonRpcResponse` (sealed) — pattern match on `JsonRpcResult`
vs `JsonRpcError`:

- `JsonRpcResult` → check metadata for SSE emitter, return JSON or SSE
- `JsonRpcError` → return as JSON with 200

### Remove manual `buildJsonRpcError()` / `buildJsonRpcResponse()` helpers

These were for constructing JSON-RPC envelopes manually. Now:
- Success: `request.response(result)` returns `JsonRpcResult`
- Error: `request.error(code, message)` returns `JsonRpcError`
- In `ToolMethodInvoker.executeStreamableTool()`, use `request.response()` and
  `request.error()` instead of manual `ObjectNode` construction

### Update `DefaultMcpStreamContext` notifications

If `DefaultMcpStreamContext` constructs JSON-RPC notifications manually using
`ObjectNode`, replace with `JsonRpcRequest.notification(method, params)`.

### Update tests

All tests referencing `@JsonRpc`, `JsonRpcResponse` (as a concrete type),
`JsonRpcException.CONSTANT`, or manual error response construction need updating.

## Acceptance criteria

- [ ] `ripcurl.version` is `1.0.0`
- [ ] All `@JsonRpc` annotations replaced with `@JsonRpcMethod`
- [ ] All `JsonRpcResponse` constructor calls replaced with `JsonRpcResult`
- [ ] Controller pattern-matches on `JsonRpcResult` / `JsonRpcError`
- [ ] No try/catch for `JsonRpcException` around dispatch
- [ ] `com.callibrity.mocapi.JsonRpcProtocol` is deleted
- [ ] Manual `buildJsonRpcError()` / `buildJsonRpcResponse()` helpers deleted
- [ ] `ToolMethodInvoker` uses `JsonRpcResult` and `request.response()` / `request.error()`
- [ ] `DefaultMcpStreamContext` uses `JsonRpcRequest.notification()` for notifications
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- RipCurl 1.0.0 Maven coordinates: `com.callibrity.ripcurl:ripcurl-core:1.0.0`,
  `com.callibrity.ripcurl:ripcurl-autoconfigure:1.0.0`
- The Methodical dependency comes transitively through RipCurl 1.0.0 (Methodical 0.1.0).
  That's fine — we don't need 0.2.0 features directly.
- Error codes are still on `JsonRpcException` in 1.0.0 (they move to `JsonRpcProtocol`
  in 1.1.0). Use `JsonRpcException.INVALID_PARAMS` etc. for now.
- The MCP transport error responses (400, 403, 404, 406) are NOT JSON-RPC errors —
  they're HTTP errors. Those still need manual construction or a simple helper. Don't
  confuse them with `JsonRpcError`.
- `JsonRpcResult.withMetadata()` returns a new `JsonRpcResult` (immutable). The
  `getMetadata()` method returns `Optional<T>`.
