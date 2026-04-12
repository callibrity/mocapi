# DefaultMcpProtocol implementation

## What to build

Create `DefaultMcpProtocol` in `mocapi-protocol` — the concrete
`McpProtocol` implementation that handles all MCP protocol logic.
This class **coexists** with the existing controller — it's not
wired in yet. Spec 148d wires it in and removes the old code.

### Responsibilities

1. **Session enforcement**: reject non-initialize messages without
   a session ID. Reject messages with an unknown/expired session.
2. **Initialize handling**: create session, emit
   `SessionInitialized`, send the `InitializeResult` response.
3. **JSON-RPC dispatch**: delegate calls and notifications to the
   `JsonRpcDispatcher`.
4. **Client response delivery**: when a `JsonRpcResponse` arrives
   (elicitation/sampling result from client), deliver to the
   Mailbox correlation service. Do NOT call `transport.send()`.
5. **Streaming tool orchestration**: detect streamable tools,
   run on virtual thread, provide `McpStreamContext` wired to
   the transport.

### Constructor dependencies

```java
public class DefaultMcpProtocol implements McpProtocol {
  private final JsonRpcDispatcher dispatcher;
  private final McpSessionService sessionService;
  private final MailboxFactory mailboxFactory;
  private final ObjectMapper objectMapper;
  private final InitializeResult initializeResult;
  private final ToolsRegistry toolsRegistry;
  private final MocapiProperties properties;
}
```

### handle() method

```java
@Override
public void handle(McpContext context, JsonRpcMessage message, McpTransport transport) {
  if (context.sessionId() == null) {
    if (message instanceof JsonRpcCall call && "initialize".equals(call.method())) {
      handleInitialize(context, call, transport);
      return;
    }
    sendError(transport, -32600, "Session required", extractId(message));
    return;
  }

  var session = sessionService.find(context.sessionId());
  if (session.isEmpty()) {
    sendError(transport, -32600, "Session not found or expired", extractId(message));
    return;
  }

  switch (message) {
    case JsonRpcCall call -> handleCall(context, session.get(), call, transport);
    case JsonRpcNotification notification -> handleNotification(context, notification);
    case JsonRpcResponse response -> deliverClientResponse(response);
  }
}
```

### Testing

Tests use a `CapturingTransport`:

```java
class CapturingTransport implements McpTransport {
  private final List<JsonRpcMessage> messages = new ArrayList<>();
  private final List<McpLifecycleEvent> events = new ArrayList<>();

  @Override
  public void send(JsonRpcMessage message) { messages.add(message); }

  @Override
  public void emit(McpLifecycleEvent event) { events.add(event); }

  public List<JsonRpcMessage> messages() { return List.copyOf(messages); }
  public List<McpLifecycleEvent> events() { return List.copyOf(events); }
}
```

All protocol tests are pure unit tests — no HTTP, no Spring
context, no MockMvc.

## Acceptance criteria

- [ ] `DefaultMcpProtocol` implements `McpProtocol`.
- [ ] `CapturingTransport` exists in test sources.
- [ ] Session enforcement tests:
  - No session ID + non-initialize → error response via transport.
  - No session ID + initialize → creates session, emits event,
    sends result.
  - Unknown session ID → error response.
- [ ] Call dispatch tests:
  - Valid session + `tools/list` → dispatches, result sent via
    transport.
  - Valid session + `tools/call` (non-streaming) → dispatches,
    result sent via transport.
  - Valid session + unknown method → error response via transport.
- [ ] Client response tests:
  - `JsonRpcResult` → delivered to Mailbox, nothing sent via
    transport.
  - `JsonRpcError` → delivered to Mailbox, nothing sent via
    transport.
  - Orphan response (no waiting Mailbox) → silently dropped.
- [ ] Streaming tool tests:
  - Streamable tool → runs on virtual thread, progress
    notifications + result go through transport.
- [ ] `mvn verify` passes (new tests added, existing tests
      unchanged).
- [ ] No existing code is modified — `DefaultMcpProtocol` is
      additive. The controller still works the old way.

## Implementation notes

- **This class absorbs logic from**: `StreamableHttpController`
  (session validation, message routing), `McpToolMethods`
  (streaming tool orchestration), and `McpSessionMethods`
  (initialize handling). It delegates to these existing classes
  where possible rather than copying code.
- **`handleCall`** should use `ScopedValue` to bind `McpSession`
  and `McpRequestId`, same as the current controller does.
- **For streaming tools**, the current `McpToolMethods.callTool`
  returns a `JsonRpcResult` with SSE_EMITTER_KEY metadata. In
  `DefaultMcpProtocol`, the streaming path creates a stream
  context wired to the transport, runs the tool on a virtual
  thread, and lets the tool send messages via the transport
  directly. The SSE_EMITTER_KEY hack is not used.
- **Auto-configuration**: register `DefaultMcpProtocol` as a
  `@Bean` in `MocapiAutoConfiguration` (or a new auto-config
  class). The controller doesn't use it yet — that's spec 148d.
