# Clean up POST body triage

## What to build

The MCP spec requires the POST endpoint to accept three JSON-RPC message types
on the same endpoint. We're stuck with `@RequestBody JsonNode body` because
the shapes differ. Clean up the triage logic so it's readable.

### Extract a triage method

```java
private sealed interface McpMessage {
    record Request(JsonRpcRequest request) implements McpMessage {}
    record Notification(String method) implements McpMessage {}
    record ClientResponse(JsonNode id, JsonNode result, JsonNode error) implements McpMessage {}
    record Invalid(String reason) implements McpMessage {}
}

private McpMessage triage(JsonNode body) {
    String method = body.path("method").asString(null);
    JsonNode id = body.get("id");
    if (method != null && id == null) return new McpMessage.Notification(method);
    if (method != null) return new McpMessage.Request(
        new JsonRpcRequest(body.path("jsonrpc").asString(), method, body.get("params"), id));
    if (body.has("result") || body.has("error")) return new McpMessage.ClientResponse(
        id, body.get("result"), body.get("error"));
    return new McpMessage.Invalid("Unrecognized message");
}
```

Then the handler pattern-matches:

```java
return switch (triage(body)) {
    case McpMessage.Notification n -> handleNotification(n.method(), headers);
    case McpMessage.Request r -> dispatch(r.request(), headers);
    case McpMessage.ClientResponse r -> handleClientResponse(r, headers);
    case McpMessage.Invalid i -> badRequest(i.reason());
};
```

Clean, one place for the shape detection, typed from that point forward.

## Acceptance criteria

- [ ] POST handler uses a sealed `McpMessage` triage type
- [ ] Pattern matching on the triage result
- [ ] No scattered `body.get("method")` / `body.has("result")` checks
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- The sealed interface and triage method are private to the controller.
- Notifications still need the session ID from headers for dispatch.
- The `JsonRpcRequest` is constructed from the body for the request path only.
