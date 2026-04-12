# Protocol tools support

## What to build

Add tool registration and dispatch to the protocol module.
This includes the tool registry, `@ToolMethod`/`@ToolService`
annotation scanning, schema generation, and the `McpToolContext`
for interactive tools.

### What to add

1. **Tool registry** — `McpToolsService` in the protocol module.
   Manages tool descriptors, pagination, lookup, and invocation.
   Clean-room implementation following the same contract as the
   existing one in mocapi-core.

2. **Annotation support** — `@ToolMethod`, `@ToolService`,
   `AnnotationMcpTool`, schema generation. These can be moved
   or reimplemented from mocapi-core.

3. **`McpToolContext`** (replaces `McpStreamContext`) — the
   tool-author-facing API for interactive tools. Provides:
   - `sendProgress(long progress, long total)`
   - `log(LoggingLevel level, String logger, String message)`
   - `sendResult(R result)`
   - `elicit(...)` — placeholder, full implementation in a
     later spec when Mailbox support is added
   - `sample(...)` — placeholder, same

   The `McpToolContext` implementation holds a reference to the
   `McpTransport` and sends notifications/results through it.

4. **Register tool methods** with the `JsonRpcDispatcher`:
   - `tools/list` — returns paginated tool descriptors
   - `tools/call` — invokes the tool

5. **Simple tools** (return a value, no `McpToolContext`) run
   synchronously. The protocol calls the tool, gets the result,
   sends it via the transport.

6. **Interactive tools** (declare `McpToolContext` parameter)
   also run synchronously from the protocol's perspective — the
   transport layer decides threading. The tool sends notifications
   and the final result via the transport through `McpToolContext`.

### Dependencies to add

```xml
<dependency>
  <groupId>com.callibrity.ripcurl</groupId>
  <artifactId>ripcurl-autoconfigure</artifactId>
  <version>${ripcurl.version}</version>
</dependency>
```

Plus schema generation deps (victools jsonschema-generator, etc.)
as needed.

### What NOT to do

- Do NOT implement elicitation or sampling (requires Mailbox).
  The `McpToolContext.elicit()` and `sample()` methods should
  throw `UnsupportedOperationException` for now.
- Do NOT implement threading. The protocol runs tools
  synchronously. The transport layer adds virtual threads later.
- Do NOT modify mocapi-core.

## Acceptance criteria

- [ ] `McpToolsService` exists in `mocapi-protocol`.
- [ ] `@ToolMethod` / `@ToolService` annotations work.
- [ ] `tools/list` returns tool descriptors via transport.
- [ ] `tools/call` for simple tools: invokes, sends result.
- [ ] `tools/call` for interactive tools: `McpToolContext`
      available, `sendProgress` and `sendResult` work.
- [ ] `McpToolContext.elicit()` throws
      `UnsupportedOperationException` (placeholder).
- [ ] Schema generation works for tool input/output.
- [ ] Tests use `CapturingTransport` — no HTTP.
- [ ] `mvn verify` passes.
- [ ] **mocapi-core is not modified.**
