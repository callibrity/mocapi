# Use JsonRpcMessage for POST body triage

## What to build

Use RipCurl 1.2.0's `JsonRpcMessage.parse()` in the controller to triage incoming
POST bodies. Replace manual `JsonNode` field checking with typed pattern matching.

### Update RipCurl version

Bump to 1.2.0 in parent `pom.xml` (or wherever the version was last set by spec 041).

### Update POST handler

```java
@PostMapping
public ResponseEntity<?> handlePost(
    @RequestBody JsonNode body,
    @RequestHeader(...) headers) {

    // MCP header validation...

    return switch (JsonRpcMessage.parse(body, objectMapper)) {
        case JsonRpcRequest request -> dispatch(request, headers);
        case JsonRpcNotification notification -> handleNotification(notification, headers);
        case JsonRpcResult result -> handleClientResult(result, headers);
        case JsonRpcError error -> handleClientError(error, headers);
    };
}
```

No more `body.has("method")`, `body.get("id")`, `body.has("result")` scattered
through the handler. One `parse()`, one `switch`.

### Use `JsonRpcNotification` for outbound notifications

`DefaultMcpStreamContext` sends notifications (progress, logging) to the client.
Use `JsonRpcNotification.of(method, params)` instead of manual construction.

## Acceptance criteria

- [ ] RipCurl version is 1.2.0
- [ ] POST handler uses `JsonRpcMessage.parse()` for triage
- [ ] Pattern matching on all four message types
- [ ] No manual `JsonNode` field checking for message type detection
- [ ] Outbound notifications use `JsonRpcNotification.of()`
- [ ] All tests pass
- [ ] `mvn verify` passes
