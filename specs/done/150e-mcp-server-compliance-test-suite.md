# MCP Server compliance unit test suite

## What to build

A comprehensive unit test suite in `mocapi-server` that verifies
every requirement from the MCP 2025-11-25 specification. All
tests use `CapturingTransport` — zero HTTP, zero Spring context,
zero MockMvc. Pure protocol-level verification.

This test suite replaces the compat IT tests as the primary
specification compliance proof. The compat ITs verify the full
HTTP stack; these tests verify the protocol logic in isolation.
If a test fails here, it's a protocol bug. If a test passes here
but fails in compat, it's a transport bug.

### Test infrastructure

```java
class CapturingTransport implements McpTransport {
  private final List<JsonRpcMessage> messages = new ArrayList<>();
  private final List<McpEvent> events = new ArrayList<>();

  @Override
  public void send(JsonRpcMessage message) { messages.add(message); }

  @Override
  public void emit(McpEvent event) { events.add(event); }

  public List<JsonRpcMessage> messages() { return List.copyOf(messages); }
  public List<McpEvent> events() { return List.copyOf(events); }

  public JsonRpcResult singleResult() {
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst()).isInstanceOf(JsonRpcResult.class);
    return (JsonRpcResult) messages.getFirst();
  }

  public JsonRpcError singleError() {
    assertThat(messages).hasSize(1);
    assertThat(messages.getFirst()).isInstanceOf(JsonRpcError.class);
    return (JsonRpcError) messages.getFirst();
  }
}
```

Each test class creates a `DefaultMcpServer` wired with
in-memory substrate (via `TestAtomSessionStore` pattern or
similar), registers test tools/resources/prompts, and exercises
the server via `handleCall`/`handleNotification`/`handleResponse`.

### Test classes — one per spec section

#### `InitializeComplianceTest`

Reference: MCP spec § Lifecycle / Initialize

- [ ] Initialize returns `protocolVersion`, `capabilities`,
      `serverInfo`.
- [ ] Initialize emits `SessionInitialized` event with session
      ID and protocol version.
- [ ] Initialize creates a session in the session store.
- [ ] Initialize without session ID succeeds (it's the only
      method that doesn't require one).
- [ ] Capabilities reflect registered tools, resources, prompts.
- [ ] Empty tool registry → `tools` capability is null.
- [ ] Empty resource registry → `resources` capability is null.
- [ ] Empty prompt registry → `prompts` capability is null.
- [ ] Second initialize with same transport succeeds (new
      session).
- [ ] Server info includes name, version, title from config.

#### `SessionEnforcementComplianceTest`

Reference: MCP spec § Lifecycle / Session management

- [ ] Non-initialize call without session ID → `JsonRpcError`
      (-32600, "Session required").
- [ ] Notification without session ID → `JsonRpcError` or
      silently rejected.
- [ ] Response without session ID → silently rejected.
- [ ] Call with unknown/expired session ID → `JsonRpcError`
      (-32600, "Session not found").
- [ ] Call with valid session ID → dispatched normally.
- [ ] Terminate deletes session from store.
- [ ] Terminate with unknown session ID → no-op (idempotent).
- [ ] After terminate, subsequent calls with that session ID
      → error.

#### `PingComplianceTest`

Reference: MCP spec § Utilities / Ping

- [ ] `ping` returns empty result.
- [ ] `ping` requires valid session.

#### `ToolsListComplianceTest`

Reference: MCP spec § Server / Tools / Listing

- [ ] `tools/list` returns all registered tools.
- [ ] Each tool has `name`, `description`, `inputSchema`.
- [ ] Pagination: `cursor` parameter returns next page.
- [ ] Empty tool registry → empty list, no `nextCursor`.
- [ ] Tools with `outputSchema` include it in the descriptor.

#### `ToolsCallComplianceTest`

Reference: MCP spec § Server / Tools / Calling

- [ ] Simple tool (returns value) → `CallToolResult` with
      `content` array containing the structured result.
- [ ] Void tool → `CallToolResult` with empty content.
- [ ] Tool returning `CallToolResult` directly → passed through.
- [ ] Tool throwing `JsonRpcException` → `JsonRpcError`
      (protocol failure).
- [ ] Tool throwing other exception → `CallToolResult` with
      `isError=true` and error message in `TextContent`.
- [ ] Unknown tool name → `JsonRpcError` (-32602).
- [ ] Tool with `structuredContent` → included in result.
- [ ] Tool result includes both `content` (array) and
      `structuredContent` (object) when result is an object.

#### `ToolsCallInteractiveComplianceTest`

Reference: MCP spec § Server / Tools + Client / Elicitation +
Client / Sampling

- [ ] Interactive tool (declares `McpToolContext`) receives
      the context.
- [ ] `ctx.sendProgress(progress, total)` → transport receives
      `notifications/progress` with correct params.
- [ ] `ctx.log(level, logger, message)` → transport receives
      `notifications/message` with correct params.
- [ ] `ctx.elicit(message, schema)` → transport receives
      `elicitation/create` call with correct params.
- [ ] Client response delivered via `handleResponse` → elicit
      unblocks and returns `ElicitResult`.
- [ ] Elicit with `action=accept` → `isAccepted()` returns true,
      content accessible.
- [ ] Elicit with `action=decline` → `isAccepted()` returns
      false.
- [ ] Elicit with `action=cancel` → `isAccepted()` returns
      false.
- [ ] Elicit timeout → `McpElicitationTimeoutException`.
- [ ] Elicit when client doesn't support it →
      `McpElicitationNotSupportedException`.
- [ ] `ctx.sample(prompt, maxTokens)` → transport receives
      `sampling/createMessage` call.
- [ ] Client response delivered → sample returns
      `CreateMessageResult`.
- [ ] Sample timeout → `McpSamplingTimeoutException`.
- [ ] Sample when client doesn't support it →
      `McpSamplingNotSupportedException`.
- [ ] Interactive tool returning a value → result sent via
      transport after all notifications.
- [ ] Progress token from request `_meta` → included in
      progress notifications.

#### `ResourcesListComplianceTest`

Reference: MCP spec § Server / Resources / Listing

- [ ] `resources/list` returns all registered resources.
- [ ] Each resource has `uri`, `name`, optional `description`,
      `mimeType`.
- [ ] Pagination works.
- [ ] `resources/templates/list` returns resource templates.

#### `ResourcesReadComplianceTest`

Reference: MCP spec § Server / Resources / Reading

- [ ] `resources/read` with valid URI → returns content.
- [ ] Text resource → `TextResourceContents`.
- [ ] Binary resource → `BlobResourceContents` with base64 data.
- [ ] Unknown URI → `JsonRpcError`.

#### `ResourcesSubscribeComplianceTest`

Reference: MCP spec § Server / Resources / Subscriptions

- [ ] `resources/subscribe` with valid URI → success.
- [ ] `resources/unsubscribe` with valid URI → success.

#### `PromptsListComplianceTest`

Reference: MCP spec § Server / Prompts / Listing

- [ ] `prompts/list` returns all registered prompts.
- [ ] Each prompt has `name`, `description`, optional
      `arguments`.
- [ ] Pagination works.

#### `PromptsGetComplianceTest`

Reference: MCP spec § Server / Prompts / Getting

- [ ] `prompts/get` with valid name → returns messages array.
- [ ] Prompt with arguments → arguments substituted into
      messages.
- [ ] Unknown prompt name → `JsonRpcError`.

#### `CompletionComplianceTest`

Reference: MCP spec § Server / Utilities / Completion

- [ ] `completion/complete` returns completion values.

#### `LoggingComplianceTest`

Reference: MCP spec § Server / Utilities / Logging

- [ ] `logging/setLevel` updates the session's log level.
- [ ] Log level persists across subsequent requests in the
      same session.

#### `ClientResponseComplianceTest`

Reference: MCP spec § Lifecycle / Client responses

- [ ] `handleResponse` with valid correlation ID → delivered
      to waiting Mailbox.
- [ ] `handleResponse` with unknown correlation ID → silently
      dropped (no crash).
- [ ] `handleResponse` with `JsonRpcResult` → delivered.
- [ ] `handleResponse` with `JsonRpcError` → delivered.
- [ ] Response after Mailbox expired → silently dropped.

#### `NotificationComplianceTest`

Reference: MCP spec § Lifecycle / Notifications

- [ ] `notifications/initialized` → processed without error.
- [ ] Unknown notification method → silently ignored (per
      JSON-RPC spec, notifications don't produce errors).

## Acceptance criteria

- [ ] Test suite exists in `mocapi-server/src/test/java`.
- [ ] All tests use `CapturingTransport` — no HTTP, no Spring.
- [ ] Every test method maps to a specific MCP spec requirement.
- [ ] Test class names end in `ComplianceTest`.
- [ ] All tests pass (`mvn -pl mocapi-server test`).
- [ ] Test methods have clear names describing the spec
      requirement they verify.

## Implementation notes

- **Register test tools/resources/prompts** for each test class
  as needed. Use simple inline implementations — don't depend on
  the compat module's tools.
- **For elicitation/sampling tests**, use a separate thread to
  call `handleResponse` after the tool blocks on the Mailbox.
  Or use `CompletableFuture` to coordinate.
- **Don't test transport behavior** — that's the transport
  module's job. These tests verify the server produces the
  correct `JsonRpcMessage` output for each input. How those
  messages reach the client is irrelevant here.
- **Reference the spec section** in each test class javadoc so
  reviewers can cross-reference.
