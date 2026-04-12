# Fix tool invocation model: three types, tools always return values

## What to build

Ensure the tool invocation model in `mocapi-server` follows the
exact design described below. Ralph may have implemented some of
this already — verify and fix any deviations.

### Three tool invocation types

The framework detects the tool's invocation type by inspecting
its method signature:

#### 1. Direct return (simple tool)

Method returns a value, no `McpToolContext` parameter. Framework
wraps the return value in a `CallToolResult` with
`structuredContent`, sends it as a `JsonRpcResult` via transport.

```java
@ToolMethod(name = "echo", description = "Returns its input")
public EchoResponse echo(String message) {
    return new EchoResponse(message);
}
```

#### 2. Void return (fire-and-forget)

Method returns `void`, no `McpToolContext` parameter. Framework
sends an empty `CallToolResult` (with no content) as a
`JsonRpcResult` via transport.

```java
@ToolMethod(name = "notify", description = "Sends a notification")
public void notifyTarget(String target) {
    externalService.notify(target);
}
```

#### 3. Interactive (uses McpToolContext)

Method declares `McpToolContext` as a parameter. The tool uses it
for mid-execution communication — progress, logging, elicitation,
sampling. The tool STILL returns a value (or void). The return
value is the final result, same as the other types.

```java
@ToolMethod(name = "interview", description = "Interviews the user")
public InterviewResult interview(String topic, McpToolContext ctx) {
    ctx.sendProgress(0, 3);
    var name = ctx.elicit("Your name?",
        schema -> schema.string("name", "Name"));
    ctx.sendProgress(1, 3);
    var opinion = ctx.elicit("Opinion on " + topic + "?",
        schema -> schema.string("opinion", "Opinion"));
    ctx.sendProgress(2, 3);
    return new InterviewResult(name.getString("name"),
        opinion.getString("opinion"));
}
```

Interactive + void is also valid:

```java
@ToolMethod(name = "guided-setup", description = "Walks through setup")
public void guidedSetup(McpToolContext ctx) {
    var config = ctx.elicit("Configure settings",
        schema -> schema.string("host", "Host").integer("port", "Port"));
    settingsService.apply(config);
    // void — framework sends empty result
}
```

### Detection logic

The framework inspects the method at registration time:

- **Has `McpToolContext` parameter?** → interactive. The framework
  must bind the transport to the context before invocation.
- **Returns void?** → after invocation, send empty `CallToolResult`.
- **Returns non-void?** → after invocation, wrap return value in
  `CallToolResult` and send.

These are orthogonal — interactive/non-interactive is independent
of void/non-void.

### Error handling

There are two distinct error levels. The framework must handle
them differently:

#### Protocol-level errors → `JsonRpcError`

If ripcurl's dispatcher throws a `JsonRpcException` (tool not
found, malformed params, etc.), the framework sends a
`JsonRpcError` via the transport. The tool never ran.

```java
try {
    Object result = tool.invoke(ctx, arguments);
    transport.send(new JsonRpcResult(toCallToolResult(result), requestId));
} catch (JsonRpcException e) {
    transport.send(new JsonRpcError(e.getCode(), e.getMessage(), requestId));
} catch (Exception e) {
    transport.send(new JsonRpcResult(toErrorCallToolResult(e), requestId));
}
```

#### Tool execution errors → `CallToolResult` with `isError=true`

If the tool throws any non-`JsonRpcException` exception, the
framework wraps the error message in a `CallToolResult` with
`isError=true` and sends it as a `JsonRpcResult`. The tool ran
but failed.

```java
private CallToolResult toErrorCallToolResult(Exception e) {
    String message = e.getMessage() != null ? e.getMessage() : e.toString();
    return new CallToolResult(List.of(new TextContent(message, null)), true, null);
}
```

**Key distinction**: `JsonRpcError` = the framework rejected the
request (protocol failure). `CallToolResult(isError=true)` = the
tool ran and reported an error (execution failure). MCP clients
distinguish these — protocol errors are fatal, tool errors are
recoverable.

### isStreamable / isInteractive — DELETED

There is NO `isStreamable()` or `isInteractive()` flag on tools.
Every tool is invoked the same way regardless of whether it uses
`McpToolContext`. The framework does not inspect whether the tool
is "streaming" or "interactive" — it creates the context for
every invocation, and the parameter resolver injects it only if
the method signature declares it. The transport and threading
model are the same for all tools.

If any `isStreamable()` or `isInteractive()` method exists on
`McpTool` or similar interfaces, **delete it**.

### McpToolContext is a transport wrapper

`McpToolContext` provides a clean tool-author API that delegates
to `McpTransport` internally:

```java
public class McpToolContext {
  private final McpTransport transport;
  private final MailboxFactory mailboxFactory;
  private final ObjectMapper objectMapper;
  private final JsonNode requestId;
  // ... timeouts, session info for elicitation capability check

  public void sendProgress(long progress, long total) {
    transport.send(JsonRpcNotification.of("notifications/progress",
        buildProgressParams(progress, total)));
  }

  public void log(LoggingLevel level, String logger, String message) {
    transport.send(JsonRpcNotification.of("notifications/message",
        buildLogParams(level, logger, message)));
  }

  public ElicitResult elicit(String message,
      Consumer<RequestedSchemaBuilder> schemaBuilder) {
    // 1. Build elicitation params
    // 2. Create Mailbox with UUID correlation ID
    // 3. transport.send(JsonRpcCall.of("elicitation/create", params, correlationId))
    // 4. Block on Mailbox subscription waiting for client response
    // 5. Parse and return ElicitResult
  }

  public CreateMessageResult sample(String prompt, int maxTokens) {
    // Same pattern: send request via transport, block on Mailbox
  }
}
```

**`McpToolContext` does NOT have a `sendResult()` method.** Tools
return values. The framework sends the result. The context is
only for mid-execution communication.

### McpToolContext parameter resolver

A `McpToolContextResolver` (implementing ripcurl/methodical's
`ParameterResolver`) resolves `McpToolContext` parameters by
pulling the transport from a `ScopedValue`:

```java
public class McpToolContextResolver implements ParameterResolver {
  @Override
  public boolean supports(Parameter parameter) {
    return McpToolContext.class.isAssignableFrom(parameter.getType());
  }

  @Override
  public Object resolve(Parameter parameter) {
    McpTransport transport = McpTransport.CURRENT.get();
    return new McpToolContext(transport, mailboxFactory,
        objectMapper, /* ... */);
  }
}
```

The `ScopedValue` is bound by `DefaultMcpServer` before dispatch:

```java
ScopedValue.where(McpTransport.CURRENT, transport)
    .where(McpSession.CURRENT, session)
    .call(() -> {
      JsonRpcResponse response = dispatcher.dispatch(call);
      transport.send(response);
    });
```

### What to verify / fix

1. **`McpToolContext` exists** in `mocapi-server` with the
   methods described above (`sendProgress`, `log`, `elicit`,
   `sample`). No `sendResult()`.
2. **`McpToolContextResolver`** resolves the parameter from
   `ScopedValue`.
3. **Void tool return** → framework sends empty `CallToolResult`.
4. **Non-void tool return** → framework wraps in `CallToolResult`.
5. **Interactive tools** get `McpToolContext` injected, can
   call `sendProgress`/`log`/`elicit`/`sample` freely, then
   return a value.
6. **Tools never see `McpTransport` or `JsonRpcMessage`** —
   they use `McpToolContext` for mid-execution comms and return
   values for results.

## Acceptance criteria

- [ ] Three invocation types work: direct return, void, interactive.
- [ ] `McpToolContext` has `sendProgress`, `log`, `elicit`,
      `sample` — NO `sendResult`.
- [ ] `McpToolContext` delegates to `McpTransport` internally.
- [ ] `McpToolContextResolver` resolves from `ScopedValue`.
- [ ] `DefaultMcpServer` binds transport to `ScopedValue` before
      dispatch.
- [ ] Void tools → empty `CallToolResult` sent via transport.
- [ ] Non-void tools → return value wrapped in `CallToolResult`.
- [ ] Interactive tools → context available, progress/elicit/
      sample work, return value is the final result.
- [ ] Protocol tests with `CapturingTransport` verify all three
      invocation types.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
