# Define MCP-native exception hierarchy and method invoker

## What to build

Create the minimal infrastructure in `mocapi-core` needed to replace all RipCurl types used
across the project. This does NOT remove RipCurl from any module — it only creates the
replacements so subsequent specs can swap them in.

### Exception hierarchy

Create in `com.callibrity.mocapi.server.exception`:

1. **`McpException`** — base runtime exception carrying a JSON-RPC error code (MCP uses
   JSON-RPC 2.0 transport). Constructor takes `(int code, String message)` and
   `(int code, String message, Throwable cause)`. Exposes `getCode()`.

2. **`McpInvalidParamsException`** — extends `McpException` with error code `-32602`.
   Thrown when tool/prompt arguments fail validation.

3. **`McpInternalErrorException`** — extends `McpException` with error code `-32603`.
   Thrown when tool/prompt execution fails unexpectedly.

### Method invoker

Create in `com.callibrity.mocapi.server.invoke`:

4. **`JsonMethodInvoker`** — takes an `ObjectMapper`, target object, and `Method`.
   `invoke(ObjectNode arguments)` maps each method parameter by name to its JSON value,
   deserializes via Jackson to the parameter type, invokes the method reflectively, and
   returns the result as a `JsonNode`. Must properly unwrap `InvocationTargetException`
   — if the cause is a `RuntimeException`, re-throw it directly (so `McpInvalidParamsException`
   propagates with its original error code instead of being wrapped as an internal error).

### What NOT to create

- No `@JsonRpcService` or `@JsonRpc` annotations — these were decorative and will be
  removed from `McpToolsCapability` and `McpPromptsCapability` in a later spec.
- No `LazyInitializer` — will be replaced with eager initialization in a later spec.

## Acceptance criteria

- [ ] `McpException`, `McpInvalidParamsException`, `McpInternalErrorException` exist in `com.callibrity.mocapi.server.exception`
- [ ] `JsonMethodInvoker` exists in `com.callibrity.mocapi.server.invoke`
- [ ] Unit tests exist for each class
- [ ] `JsonMethodInvoker` test verifies that `RuntimeException` thrown by target method propagates unwrapped
- [ ] `JsonMethodInvoker` test verifies that checked exception thrown by target method is wrapped in `RuntimeException`
- [ ] `mocapi-core/pom.xml` has `jackson-databind` dependency (needed by `JsonMethodInvoker`)
- [ ] `mvn test -pl mocapi-core` passes
- [ ] No `com.callibrity.ripcurl` imports in any newly created file

## Implementation notes

- `mocapi-core/pom.xml` currently depends on `ripcurl-core` which transitively provides
  Jackson. When ripcurl is removed in a later spec, `jackson-databind` must be declared
  directly. Add it now so the new classes don't depend on the transitive path.
- The existing `com.callibrity.ripcurl.core.invoke.JsonMethodInvoker` is used in
  `AnnotationMcpTool` — study its behavior to ensure the replacement is compatible.
- Compilation requires `-parameters` flag (already configured in `maven-compiler-plugin`)
  so `Parameter.getName()` returns real names for JSON mapping.
