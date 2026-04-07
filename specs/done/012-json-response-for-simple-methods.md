# Support JSON responses for simple request/response methods

## What to build

The MCP spec says: "If the input is a JSON-RPC request, the server MUST either return
`Content-Type: text/event-stream`, to initiate an SSE stream, or
`Content-Type: application/json`, to return one JSON object."

Currently Mocapi always returns SSE for every request. For simple request/response
methods that don't need to stream intermediate messages (like `initialize`, `ping`,
`tools/list`), the server should return a plain JSON response instead. This is more
efficient and avoids the overhead of creating an Odyssey ephemeral stream, virtual
threads, and SSE framing for a single response.

### Introduce `McpStreamContext` parameter convention

Define a new type `McpStreamContext` in the `sse` package. This is a handle that MCP
handler methods can declare as a parameter to opt into SSE streaming. It wraps an
`OdysseyStream` and provides methods for sending intermediate messages to the client
during a long-running operation:

```java
public class McpStreamContext {
    void sendProgress(long progress, long total);
    void sendNotification(String method, Object params);
    // Future: elicitation, sampling via Substrate Mailbox
}
```

### Controller dispatch logic

The controller's method invocation logic should inspect the handler method signature:

- **Without `McpStreamContext` parameter**: Invoke the method synchronously, wrap the
  result in a JSON-RPC response, return `Content-Type: application/json`.
- **With `McpStreamContext` parameter**: Create an ephemeral Odyssey stream, subscribe,
  inject the context, dispatch on a virtual thread, publish the response via SSE when
  done. Return `Content-Type: text/event-stream`.

This means `initialize`, `ping`, `tools/list`, and `tools/call` (in its current simple
form) all return plain JSON. A future enhanced `tools/call` that sends progress would
declare `McpStreamContext` and return SSE.

### Method resolution

The `invokeMethod` switch in `McpStreamingController` currently returns `Object`. It
needs to be refactored so the controller can determine the response strategy BEFORE
invoking the method:

1. Look up the handler for the method name
2. Check if the handler needs `McpStreamContext`
3. Choose JSON or SSE response path
4. Invoke the method with appropriate arguments

This likely means replacing the inline switch with a registry of method handlers that
can be introspected. The exact mechanism (functional interfaces, a map of handlers,
reflection) is an implementation decision.

## Acceptance criteria

- [ ] `McpStreamContext` class exists with `sendProgress()` and `sendNotification()`
      methods
- [ ] POST requests to methods without `McpStreamContext` return
      `Content-Type: application/json` with a JSON-RPC response body
- [ ] POST requests to methods with `McpStreamContext` return
      `Content-Type: text/event-stream` with SSE
- [ ] `initialize` returns JSON
- [ ] `ping` returns JSON
- [ ] `tools/list` returns JSON
- [ ] `tools/call` returns JSON (current simple implementation has no stream context)
- [ ] The priming event and Odyssey stream are only created for SSE responses
- [ ] Existing Accept header validation still applies (client must accept both
      `application/json` and `text/event-stream` on POST per MCP spec)
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- `McpStreamContext` is a Mocapi concept, not an Odyssey concept. It wraps Odyssey's
  `OdysseyStream` but adds MCP-specific methods (progress notifications formatted as
  JSON-RPC).
- The `invokeMethod` switch statement will need to be restructured. Consider a
  `Map<String, McpMethodHandler>` where each handler declares whether it needs a
  stream context. This also makes method registration extensible for future capabilities
  beyond tools.
- For the JSON path, the response is straightforward:
  `ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(jsonRpcResponse)`
- The Accept header validation (`isValidPostAcceptHeader`) should remain unchanged —
  the client must always accept both JSON and SSE, even if the server chooses JSON for
  a particular response. This is per spec.
- This spec depends on 011 (Odyssey migration) for the SSE path. The JSON path can be
  implemented independently.
