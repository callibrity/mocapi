# Add progress token and logging support to McpStreamContext

## What to build

The MCP conformance suite tests `tools-call-with-progress` and `tools-call-with-logging`
which require tools to send progress notifications and log messages during execution.
`McpStreamContext` already has `sendProgress()` and `sendNotification()`, but they
need to support the conformance test expectations.

### Progress with token

The conformance test sends a `tools/call` request with `_meta.progressToken` in the
params. The tool must send `notifications/progress` events referencing that token:

```json
{
  "method": "notifications/progress",
  "params": {
    "progressToken": "<from request._meta.progressToken>",
    "progress": 50,
    "total": 100
  }
}
```

`McpStreamContext.sendProgress()` needs access to the progress token from the original
request's `_meta`. The token should be extracted from the request params and made
available to the context.

Update `sendProgress(long progress, long total)` to automatically include the
progress token if one was provided in the request.

### Logging during tool execution

The conformance test `tools-call-with-logging` expects the tool to send at least 3
log notifications at info level:

```json
{
  "method": "notifications/message",
  "params": {
    "level": "info",
    "logger": "<tool name or identifier>",
    "data": "Tool execution started"
  }
}
```

Add logging methods to `McpStreamContext`:

```java
void log(String level, String message);
void log(String level, String logger, Object data);
```

These publish `notifications/message` JSON-RPC notifications on the SSE stream.

## Acceptance criteria

- [ ] `sendProgress()` includes the progress token from the request's `_meta`
- [ ] Progress notifications match the expected JSON-RPC format
- [ ] `log()` methods exist on `McpStreamContext`
- [ ] Log notifications match the expected `notifications/message` format
- [ ] Progress token is extracted from request params and available to the context
- [ ] All existing tests pass or are updated
- [ ] `mvn verify` passes

## Implementation notes

- The progress token comes from `params._meta.progressToken` in the `tools/call`
  request. This needs to be extracted before method invocation and passed to the
  `McpStreamContext` (or its implementation).
- If no progress token is provided, `sendProgress()` should be a no-op or omit the
  token field.
- Logging levels per MCP spec: debug, info, notice, warning, error, critical, alert,
  emergency.
- This spec depends on the `McpStreamContext` interface being in place (spec 016,
  already done).
