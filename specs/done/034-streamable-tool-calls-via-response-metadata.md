# Streamable tool calls via response metadata

## What to build

Use RipCurl's `JsonRpcResponse` metadata to carry `SseEmitter` instances from the
`tools/call` handler to the controller. The handler decides whether to stream based
on `McpTool.isStreamable()`. The controller is a dumb pipe — check for an emitter
in metadata, return it if present, otherwise return JSON.

### Add `isStreamable()` to `McpTool`

```java
public interface McpTool {
    // ... existing methods ...
    boolean isStreamable();
}
```

`AnnotationMcpTool` sets this at construction time by inspecting whether the `@Tool`
method has an `McpStreamContext` parameter. Cached, no runtime reflection.

### Update `McpToolMethods` handler

The `@JsonRpc("tools/call")` handler checks `isStreamable()` and branches:

**Non-streamable (JSON path):**
```java
McpTool tool = registry.lookup(name);
CallToolResponse result = tool.call(arguments);
return result; // RipCurl wraps in JsonRpcResponse, controller returns JSON
```

**Streamable (SSE path):**
```java
McpTool tool = registry.lookup(name);
OdysseyStream stream = odysseyRegistry.ephemeral();
stream.publishJson(Map.of()); // priming event
SseEmitter emitter = stream.subscriber().mapper(sseEventMapper).subscribe();

Thread.ofVirtual().start(() -> {
    ScopedValue.where(CURRENT_SESSION, session)
        .where(CURRENT_STREAM_CONTEXT, streamContext)
        .run(() -> {
            try {
                CallToolResponse result = tool.call(arguments);
                // Publish final JSON-RPC response on stream
                stream.publishJson(new JsonRpcResponse(
                    objectMapper.valueToTree(result), id));
                stream.close();
            } catch (Exception e) {
                // Publish JSON-RPC error on stream
                stream.publishJson(errorResponse(id, e));
                stream.close();
            }
        });
});

// Return empty response with emitter in metadata
return ???; // see below
```

The handler returns the result via RipCurl's normal path. For streaming, the return
value doesn't matter (the real result goes on the SSE stream). But we need to attach
the emitter to the response.

Since `@JsonRpc` methods return domain objects (not `JsonRpcResponse` directly),
the handler can't call `withMetadata()` on the response. Instead, the handler
returns a special marker object, and the `ToolMethodInvoker` (or the `@JsonRpc`
handler itself) wraps it.

**Simplest approach:** The `tools/call` handler returns `Object`. For non-streamable
tools, it returns `CallToolResponse`. For streamable tools, it returns the
`SseEmitter` directly. RipCurl serializes it via `valueToTree()`, which produces
a `JsonNode` representation of the emitter (probably useless), but the controller
intercepts before that matters.

**Better approach:** The handler constructs the `JsonRpcResponse` with metadata
and throws/returns it in a way RipCurl can pass through. This requires RipCurl
to support returning a raw `JsonRpcResponse` from a handler without re-wrapping it.

**Recommended approach:** Have `ToolMethodInvoker` handle this. When it detects a
streamable tool, it sets up the stream, dispatches the virtual thread, and returns
a `JsonRpcResponse` with the emitter in metadata. The `@JsonRpc("tools/call")`
handler delegates to `ToolMethodInvoker` which returns the `JsonRpcResponse`
directly.

For this to work, the `@JsonRpc` method should return `JsonRpcResponse` for
streamable tools and `CallToolResponse` for non-streamable tools. RipCurl's
`JsonMethodInvoker` already handles `JsonNode` return types — if the method returns
a `JsonRpcResponse`, it would get double-wrapped. We may need RipCurl to detect
when a handler returns a `JsonRpcResponse` and pass it through without wrapping.

### Update controller

The controller checks metadata after dispatch:

```java
JsonRpcResponse response = dispatcher.dispatch(request);

if (response == null) {
    return ResponseEntity.accepted().build(); // notification
}

Optional<SseEmitter> emitter = response.getMetadata("emitter", SseEmitter.class);
if (emitter.isPresent()) {
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    if (INITIALIZE.equals(request.method())) {
        builder.header("MCP-Session-Id", sessionId);
    }
    return builder.body(emitter.get());
}

ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
    .contentType(MediaType.APPLICATION_JSON);
if (INITIALIZE.equals(request.method())) {
    builder.header("MCP-Session-Id", sessionId);
}
return builder.body(response);
```

Spring sets `text/event-stream` automatically when the body is an `SseEmitter`.

### Controller sets `McpSession.CURRENT` for all dispatches

The controller wraps every non-initialize dispatch in a `ScopedValue` scope so all
handlers can read the session:

```java
if (!"initialize".equals(request.method())) {
    McpSession session = sessionService.find(sessionId).orElse(null);
    if (session == null) return notFound();
    return ScopedValue.where(McpSession.CURRENT, session).call(() -> {
        JsonRpcResponse response = dispatcher.dispatch(request);
        // ... return JSON or SSE
    });
}
```

For `initialize`, there's no session yet — dispatch without a scope.

The streamable tool handler reads `McpSession.CURRENT.get()` from this scope and
explicitly re-sets it on the spawned virtual thread along with `McpStreamContext.CURRENT`.

### Remove `ThreadLocal` resolvers

Add `ScopedValue` constants directly on the types they carry:

```java
public interface McpStreamContext {
    ScopedValue<McpStreamContext> CURRENT = ScopedValue.newInstance();
    // ... existing methods ...
}

public record McpSession(...) {
    public static final ScopedValue<McpSession> CURRENT = ScopedValue.newInstance();
    // ... existing methods ...
}
```

Set by the virtual thread scope in the streamable tool path:

```java
ScopedValue.where(McpSession.CURRENT, session)
    .where(McpStreamContext.CURRENT, streamContext)
    .run(() -> { ... });
```

Read by `ToolMethodInvoker` when injecting `McpStreamContext` into `@Tool` methods:

```java
McpStreamContext ctx = McpStreamContext.CURRENT.isBound()
    ? McpStreamContext.CURRENT.get() : null;
```

Delete `McpStreamContextParamResolver` and `McpSessionIdParamResolver`.

### `SseEmitter` metadata key constant

Define a constant for the metadata key:

```java
public static final String SSE_EMITTER_KEY = "sseEmitter";
```

In Mocapi, not RipCurl — the key is an MCP transport concern.

## Acceptance criteria

- [ ] `McpTool.isStreamable()` exists
- [ ] `AnnotationMcpTool` sets `isStreamable()` based on `McpStreamContext` parameter
- [ ] Non-streamable tools return JSON via normal RipCurl dispatch
- [ ] Streamable tools attach `SseEmitter` to `JsonRpcResponse` metadata
- [ ] Streamable tools dispatch on virtual thread with `ScopedValue` context
- [ ] Streamable tools publish final JSON-RPC response on Odyssey stream
- [ ] Controller checks `response.getMetadata("sseEmitter", SseEmitter.class)`
- [ ] Controller returns `SseEmitter` for streaming, JSON for everything else
- [ ] `ThreadLocal`-based resolvers are deleted
- [ ] `ScopedValue` used for session and stream context
- [ ] All tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- The `@JsonRpc("tools/call")` handler is the bridge between RipCurl's synchronous
  dispatch and Odyssey's async streaming. It's the only place this complexity exists.
- For non-streamable tools, the flow is completely synchronous — no Odyssey, no
  virtual threads, no `ScopedValue`. Just call the tool and return.
- The virtual thread for streamable tools must handle exceptions and publish a
  JSON-RPC error response on the stream before closing. Otherwise the client
  gets a stream that never completes.
- RipCurl may need a small change to pass through `JsonRpcResponse` return values
  without double-wrapping. Alternatively, the handler can return `JsonNode` and
  the invoker skips serialization.
- The `SseEventMapper` used when subscribing should be the MCP mapper that encrypts
  event IDs via `McpSessionService`.
