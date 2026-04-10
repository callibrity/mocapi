# Surface tool execution failures as CallToolResult(isError=true)

## What to build

Today the framework converts every failure during a `tools/call` into a
JSON-RPC error, regardless of whether the failure is a protocol-level problem
or a tool execution problem. Per the MCP spec, these two categories must be
reported differently:

- **Protocol-level failures** (tool not found, schema validation failed,
  invalid JSON-RPC envelope, etc.) → JSON-RPC `error` response. These stay
  exactly as they are.
- **Tool execution failures** (the tool was dispatched and ran, but its body
  threw an unhandled exception) → `CallToolResult` with `isError = true` and
  the exception message placed in `content` as a `TextContent`. The LLM is
  expected to see these and react.

Update both the non-streamable and streamable call paths in
`McpToolMethods` / `ToolsRegistry` so that an unhandled exception thrown by a
tool's method body becomes a successful JSON-RPC `result` carrying a
`CallToolResult(content=[TextContent(<message>)], isError=true,
structuredContent=null)`.

`JsonRpcException` thrown by the framework itself (e.g., `lookup(name)`
raising `INVALID_PARAMS` for an unknown tool, or schema validation raising
`INVALID_PARAMS`) must continue to propagate as JSON-RPC errors — those are
protocol-level, not tool execution failures.

Tool authors who want to emit a handled error can already return a
`CallToolResult` with `isError = true` directly; that path must keep working
untouched.

## Acceptance criteria

- [ ] An unhandled `RuntimeException` thrown from a non-streamable tool's
      method body results in an HTTP 200 JSON-RPC success response whose
      `result` is a `CallToolResult` with `isError = true` and a
      `TextContent` containing the exception message. It does **not** produce
      a JSON-RPC `error` response.
- [ ] An unhandled `RuntimeException` thrown from a streamable tool's method
      body results in a `JsonRpcResult` (not `JsonRpcError`) being published
      on the SSE stream, whose result is a `CallToolResult` with
      `isError = true` and a `TextContent` containing the exception message.
- [ ] `JsonRpcException` thrown from `ToolsRegistry.lookup` for an unknown
      tool still produces a JSON-RPC error with code `INVALID_PARAMS`
      (unchanged behavior).
- [ ] `JsonRpcException` thrown from `ToolsRegistry.validateInput` for a
      schema validation failure still produces a JSON-RPC error with code
      `INVALID_PARAMS` (unchanged behavior).
- [ ] A tool that returns `CallToolResult(isError=true, ...)` directly has
      that value passed through untouched in both the non-streamable and
      streamable paths (unchanged behavior).
- [ ] New unit tests in `mocapi-core` cover each of the above cases.
- [ ] The conformance suite in `mocapi-compat` still passes 39/39.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- The relevant code lives in:
  - `mocapi-core/.../tools/ToolsRegistry.java` — `callTool()` calls
    `tool.call(arguments)` directly; any exception thrown here currently
    bubbles out. Wrap execution of the tool body (but **not** `lookup` or
    `validateInput`) in a try/catch that distinguishes `JsonRpcException`
    (rethrow) from other exceptions (wrap into an `isError=true`
    `CallToolResult`).
  - `mocapi-core/.../tools/McpToolMethods.java` — `executeStreamableTool()`
    currently catches `Exception` and publishes a `JsonRpcError` with
    `INTERNAL_ERROR`. Change the generic catch so that non-`JsonRpcException`
    exceptions produce a `JsonRpcResult` carrying an `isError=true`
    `CallToolResult`. `JsonRpcException` must keep producing a `JsonRpcError`.
- `ToolsRegistry.toCallToolResult(Object, ObjectMapper)` is already the
  single wrapping point for successful results; consider adding a sibling
  helper like `toErrorCallToolResult(Throwable, ObjectMapper)` that builds
  the `isError=true` variant, so both call sites share the same construction
  logic.
- Log the underlying exception at `WARN` or `ERROR` level when wrapping it
  into an `isError=true` result — we don't want silent swallowing of
  unexpected failures; operators still need visibility.
- The existing test
  `StreamableHttpControllerTest.runtimeExceptionShouldReturnJsonInternalError`
  asserts the old behavior (`-32603`) and will need to be rewritten to
  assert the new `CallToolResult(isError=true)` shape. Do not delete it —
  repurpose it.
- The existing test
  `StreamableHttpControllerTest.jsonRpcExceptionShouldReturnJsonErrorResponse`
  asserts protocol-level errors remain JSON-RPC errors (`-32602`) and must
  continue to pass unchanged.
- MCP spec reference: tool results should carry `isError=true` to let the
  LLM see and react to application-level failures; JSON-RPC errors are
  reserved for protocol-level problems.
