# Simplify tool invocation with Methodical

## What to build

Replace the hand-built tool invocation infrastructure with Methodical's
`MethodInvokerFactory`. Tools are just methods — Methodical handles parameter
resolution, type conversion, and invocation. No fake `JsonRpcRequest` construction,
no `ToolMethodInvoker` orchestration class.

### How it works today

1. `AnnotationMcpTool` wraps RipCurl's `JsonMethodInvoker`
2. `call()` creates a fake `JsonRpcRequest` to pass to the invoker
3. `ToolMethodInvoker` looks up the tool, checks `isStreamable()`, orchestrates
   Odyssey streams and virtual threads
4. Multiple classes, fake objects, complex wiring

### How it should work

1. `AnnotationMcpTool` holds a Methodical `MethodInvoker<JsonNode>`
2. `call(JsonNode arguments)` just does `invoker.invoke(arguments)` — returns raw result
3. The `@JsonRpcMethod("tools/call")` handler checks `isStreamable()` and builds
   the `JsonRpcResult` (with or without SSE emitter metadata)
4. `McpStreamContext` is injected by a Methodical `ParameterResolver<JsonNode>`

### Typed `McpStreamContext<O>` for streaming tools

`McpStreamContext` becomes generic: `McpStreamContext<O>` where `O` is the output
type. This serves three purposes:

1. **Detection** — if a `@Tool` method has a `McpStreamContext<?>` parameter, it's
   streamable
2. **Output schema** — `O` is used for output schema generation instead of the
   method's return type (streaming methods are void)
3. **Type-safe response** — `stream.sendResponse(O)` publishes the typed result

Three method signatures:

```java
// Non-streaming: output schema from return type
@Tool
public MyResult doThing(String input) { ... }

// Void: no output schema
@Tool
public void doThing(String input) { ... }

// Streaming: output schema from O, void return
@Tool
public void doThing(String input, McpStreamContext<MyResult> stream) {
    stream.sendProgress(1, 10);
    var answer = stream.elicitForm("Need info", MyForm.class);
    stream.sendResponse(new MyResult("done"));
}
```

`sendResponse(O)` is terminal — it publishes the JSON-RPC response on the SSE
stream and closes it. Calling it twice throws.

### Update `McpStreamContext` interface

Add the type parameter and `sendResponse()`:

```java
public interface McpStreamContext<O> {
    ScopedValue<McpStreamContext<?>> CURRENT = ScopedValue.newInstance();

    void sendResponse(O response);
    void sendProgress(long progress, long total);
    void log(LogLevel level, String logger, Object data);
    <T> ElicitationResult<T> elicitForm(String message, Class<T> type);
    <T> ElicitationResult<T> elicitForm(String message, TypeReference<T> type);
    void sendNotification(String method, Object params);
    // convenience log methods...
}
```

### Update `McpTool` interface

Simplify the call signature:

```java
public interface McpTool {
    String name();
    String title();
    String description();
    ObjectNode inputSchema();
    ObjectNode outputSchema();
    boolean isStreamable();
    Object call(JsonNode arguments);
}
```

Returns raw `Object` — the handler wraps it. No `ObjectNode` return type constraint.
Void methods return `null`.

### Update `AnnotationMcpTool`

Use Methodical directly:

```java
public class AnnotationMcpTool implements McpTool {
    private final MethodInvoker<JsonNode> invoker;
    // ... name, title, description, schemas, isStreamable

    AnnotationMcpTool(Method method, Object target, MethodInvokerFactory factory,
                      MethodSchemaGenerator schemaGenerator) {
        this.invoker = factory.create(method, target, JsonNode.class);

        // Detect McpStreamContext<O> parameter for streaming and output schema
        for (Parameter param : method.getParameters()) {
            if (McpStreamContext.class.isAssignableFrom(param.getType())) {
                this.streamable = true;
                // Extract O from McpStreamContext<O> for output schema
                Type genericType = param.getParameterizedType();
                Class<?> outputType = Types.typeParamFromType(
                    genericType, McpStreamContext.class, 0);
                this.outputSchema = schemaGenerator.generate(outputType);
                break;
            }
        }

        // Non-streaming: output schema from return type (null for void)
        if (!this.streamable) {
            this.outputSchema = isVoid(method) ? null
                : schemaGenerator.generate(method.getReturnType());
        }
    }

    @Override
    public Object call(JsonNode arguments) {
        return invoker.invoke(arguments);
    }
}
```

No fake `JsonRpcRequest`. No `ObjectNode` return type check. Methodical handles
parameter resolution (including `McpStreamContext` via resolver), invocation,
void returns, and exception unwrapping.

The output schema comes from `O` in `McpStreamContext<O>` for streaming tools,
or from the return type for non-streaming tools. Void methods have no output schema.

### Create `McpStreamContextResolver`

A Methodical `ParameterResolver<JsonNode>` that injects `McpStreamContext` from
the `ScopedValue`:

```java
public class McpStreamContextResolver implements ParameterResolver<JsonNode> {
    @Override
    public boolean supports(ParameterInfo info) {
        return McpStreamContext.class.isAssignableFrom(info.resolvedType());
    }

    @Override
    public Object resolve(ParameterInfo info, JsonNode params) {
        return McpStreamContext.CURRENT.isBound() ? McpStreamContext.CURRENT.get() : null;
    }
}
```

Register as a Spring bean. It has higher priority than the Jackson resolver
(which is `@Order(LOWEST_PRECEDENCE)`), so it claims `McpStreamContext` parameters
before Jackson tries to deserialize them from JSON.

### Create `McpRequestMeta` record

A shared type for the MCP `_meta` field that any request can include:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpRequestMeta(String progressToken) {}
```

Lives in `mocapi-core` — used by any `@JsonRpcMethod` handler that wants to
access request metadata.

### Delete `ToolMethodInvoker`

The orchestration logic (check `isStreamable()`, set up Odyssey stream, dispatch
virtual thread, build `JsonRpcResult` with emitter metadata) moves into the
`@JsonRpcMethod("tools/call")` handler in `McpToolMethods`:

```java
@JsonRpcMethod("tools/call")
public JsonRpcResult callTool(
    String name,
    JsonNode arguments,
    @Named("_meta") McpRequestMeta meta,
    JsonRpcRequest request) {

    McpTool tool = toolsRegistry.lookup(name);

    if (!tool.isStreamable()) {
        Object result = tool.call(arguments);
        return request.response(objectMapper.valueToTree(result));
    }

    // Streaming path
    String progressToken = meta != null ? meta.progressToken() : null;
    McpSession session = McpSession.CURRENT.get();
    OdysseyStream stream = registry.ephemeral();
    stream.publishJson(Map.of()); // priming event
    SseEmitter emitter = stream.subscriber().mapper(sseEventMapper).subscribe();
    DefaultMcpStreamContext<?> ctx = new DefaultMcpStreamContext<>(
        stream, objectMapper, progressToken, ...);

    Thread.ofVirtual().start(() ->
        ScopedValue.where(McpSession.CURRENT, session)
            .where(McpStreamContext.CURRENT, ctx)
            .run(() -> {
                try {
                    Object result = tool.call(arguments);
                    stream.publishJson(request.response(
                        objectMapper.valueToTree(result)));
                    stream.close();
                } catch (Exception e) {
                    stream.publishJson(request.error(
                        JsonRpcException.INTERNAL_ERROR, e.getMessage()));
                    stream.close();
                }
            }));

    return request.response(NullNode.getInstance())
        .withMetadata("emitter", emitter);
}
```

All the Odyssey/streaming/ScopedValue logic lives in one place — the handler.
The `McpTool` is simple: resolve params, invoke, return result.

### Delete `ToolMethodInvoker.SSE_EMITTER_HOLDER` and `REQUEST_ID` ScopedValues

These were workarounds for the old architecture. With the handler owning the
streaming setup, they're unnecessary.

### Update `ToolsRegistry`

Simplify `callTool()` — it just does lookup + call:

```java
public Object callTool(String name, JsonNode arguments) {
    McpTool tool = lookup(name);
    return tool.call(arguments);
}
```

Or remove `callTool()` entirely — the handler calls `tool.call()` directly after
`lookup()`.

### Remove the `outputSchema` type restriction

The current code rejects non-object output schemas. Remove this check —
the spec doesn't require it. A tool can return any JSON type.

### Wire `MethodInvokerFactory` for tools

The `MethodInvokerFactory` bean is already provided by Methodical's autoconfigure.
`AnnotationMcpTool` gets it injected (via the tool provider factory). The
`McpStreamContextResolver` is an additional `ParameterResolver<JsonNode>` bean that
the factory picks up automatically.

## Acceptance criteria

- [ ] `McpTool.call()` takes `JsonNode` and returns `Object`
- [ ] `AnnotationMcpTool` uses Methodical `MethodInvoker<JsonNode>` directly
- [ ] No fake `JsonRpcRequest` construction in tool invocation
- [ ] `McpStreamContextResolver` exists as a `ParameterResolver<JsonNode>`
- [ ] `McpStreamContext` is injected into tool methods via the resolver
- [ ] `ToolMethodInvoker` is deleted
- [ ] `ToolMethodInvoker.SSE_EMITTER_HOLDER` and `REQUEST_ID` ScopedValues deleted
- [ ] Streaming orchestration lives in `McpToolMethods.callTool()` handler
- [ ] Non-streaming tools return JSON via `request.response()`
- [ ] Streaming tools return `JsonRpcResult` with emitter metadata
- [ ] Output schema type restriction removed
- [ ] All tests pass
- [ ] `mvn verify` passes

## Implementation notes

- Methodical's `Jackson3ParameterResolver` handles all JSON parameter resolution.
  `McpStreamContextResolver` only claims `McpStreamContext` parameters.
- The resolver priority order: `McpStreamContextResolver` (default priority) →
  `Jackson3ParameterResolver` (`LOWEST_PRECEDENCE`).
- For void tool methods, `invoker.invoke()` returns `null`. The handler wraps it
  as `request.response(NullNode.getInstance())`.
- For streaming tools, the virtual thread sets `McpStreamContext.CURRENT` via
  `ScopedValue`. The resolver reads it. The tool method receives it as a parameter.
- The `@Named` annotation from Methodical can be used on tool parameters if the
  JSON argument name doesn't match the Java parameter name.
- `MethodInvocationException` from Methodical (reflection failures, checked exceptions)
  propagates as a `RuntimeException`. The dispatcher catches it and returns
  `INTERNAL_ERROR`. `ParameterResolutionException` (bad deserialization) becomes
  `INVALID_PARAMS` if using RipCurl 1.1.0+, or `INTERNAL_ERROR` on 1.0.0.
