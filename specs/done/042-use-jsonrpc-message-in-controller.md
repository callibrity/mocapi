# Use JsonRpcMessage for POST body triage

## What to build

Use RipCurl 2.0.0-SNAPSHOT's `JsonRpcMessage.parse()` in the controller to triage incoming
POST bodies. Replace manual `JsonNode` field checking with typed pattern matching.

### Update RipCurl version

Bump to 2.0.0-SNAPSHOT in parent `pom.xml` (or wherever the version was last set by spec 041).

Note: RipCurl 2.0.0-SNAPSHOT renamed `JsonRpcRequest` to a sealed interface. Requests with
an id are now `JsonRpcCall`, notifications are `JsonRpcNotification`. Both implement
`JsonRpcRequest`. The dispatcher accepts `JsonRpcRequest` (both types).

### Update POST handler

```java
@PostMapping
public ResponseEntity<?> handlePost(
    @RequestBody JsonNode body,
    @RequestHeader(...) headers) {

    // MCP header validation...

    return switch (JsonRpcMessage.parse(body)) {
        case JsonRpcCall call -> dispatch(call, headers);
        case JsonRpcNotification notification -> handleNotification(notification, headers);
        case JsonRpcResult result -> handleClientResult(result, headers);
        case JsonRpcError error -> handleClientError(error, headers);
    };
}
```

No more `body.has("method")`, `body.get("id")`, `body.has("result")` scattered
through the handler. One `parse()`, one `switch`.

### Update dispatch method

The `dispatch` method should accept `JsonRpcCall` (not `JsonRpcRequest`), since
only calls have an `id` and need a response. Use `call.id()` instead of
`body.get("id")`.

### Update any code that constructs requests

- `new JsonRpcRequest(jsonrpc, method, params, id)` → `new JsonRpcCall(jsonrpc, method, params, id)`
- `JsonRpcRequest.notification(method, params)` → `JsonRpcNotification.of(method, params)`
- `request.response(result)` → `call.result(result)`
- `request.error(code, message)` → `call.error(code, message)`

### Use `JsonRpcNotification` for outbound notifications

`DefaultMcpStreamContext` sends notifications (progress, logging) to the client.
Use `JsonRpcNotification.of(method, params)` instead of manual construction.

## Acceptance criteria

- [ ] RipCurl version is 2.0.0-SNAPSHOT
- [ ] POST handler uses `JsonRpcMessage.parse()` for triage
- [ ] Pattern matching on `JsonRpcCall`, `JsonRpcNotification`, `JsonRpcResult`, `JsonRpcError`
- [ ] No manual `JsonNode` field checking for message type detection
- [ ] All `JsonRpcRequest` constructor calls migrated to `JsonRpcCall`
- [ ] Outbound notifications use `JsonRpcNotification.of()`
- [ ] All tests pass
- [ ] `mvn verify` passes
