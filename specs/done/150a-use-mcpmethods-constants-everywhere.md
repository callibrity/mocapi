# Use McpMethods constants for all method name strings

## What to build

Replace every hard-coded JSON-RPC method name string in
`mocapi-server` with the corresponding constant from
`com.callibrity.mocapi.model.McpMethods`. This class already
exists in `mocapi-model` and has constants for every method in
the MCP 2025-11-25 specification.

### Where to change

Grep for `@JsonRpcMethod("` in `mocapi-server/src/main/java` —
every annotation value should use a `McpMethods` constant:

**Before**:
```java
@JsonRpcMethod("tools/list")
```

**After**:
```java
@JsonRpcMethod(McpMethods.TOOLS_LIST)
```

Also grep for any remaining string literals matching MCP method
names (e.g., `"notifications/progress"`, `"elicitation/create"`,
`"sampling/createMessage"`) in the server module and replace
with the corresponding constant.

### Files expected to change

- `McpToolsService.java` — `TOOLS_LIST`, `TOOLS_CALL`
- `McpResourcesService.java` — `RESOURCES_LIST`, `RESOURCES_READ`,
  `RESOURCES_TEMPLATES_LIST`, `RESOURCES_SUBSCRIBE`,
  `RESOURCES_UNSUBSCRIBE`
- `McpPromptsService.java` — `PROMPTS_LIST`, `PROMPTS_GET`
- `McpCompletionsService.java` — `COMPLETION_COMPLETE`
- `McpLoggingService.java` — `LOGGING_SET_LEVEL`
- `McpPingService.java` — `PING`
- `DefaultMcpToolContext.java` (or equivalent) —
  `NOTIFICATIONS_PROGRESS`, `NOTIFICATIONS_MESSAGE`,
  `ELICITATION_CREATE`, `SAMPLING_CREATE_MESSAGE`
- Any other file with hard-coded MCP method name strings.

`DefaultMcpServer.java` already uses `McpMethods.INITIALIZE` —
do NOT change it.

### What NOT to do

- Do NOT modify `McpMethods.java` in `mocapi-model` — it's
  already complete.
- Do NOT modify test files in this spec — tests can reference
  constants in a follow-up.
- Do NOT modify `mocapi-transport-streamable-http`.

## Acceptance criteria

- [ ] Every `@JsonRpcMethod` annotation in `mocapi-server` uses
      a `McpMethods` constant.
- [ ] Every hard-coded MCP method name string in
      `mocapi-server/src/main/java` is replaced with a
      `McpMethods` constant.
- [ ] `grep -rn '"tools/' mocapi-server/src/main/java` returns
      zero results.
- [ ] `grep -rn '"resources/' mocapi-server/src/main/java`
      returns zero results.
- [ ] `grep -rn '"prompts/' mocapi-server/src/main/java` returns
      zero results.
- [ ] `grep -rn '"logging/' mocapi-server/src/main/java` returns
      zero results.
- [ ] `grep -rn '"completion/' mocapi-server/src/main/java`
      returns zero results.
- [ ] `grep -rn '"notifications/' mocapi-server/src/main/java`
      returns zero results.
- [ ] `grep -rn '"elicitation/' mocapi-server/src/main/java`
      returns zero results.
- [ ] `grep -rn '"sampling/' mocapi-server/src/main/java`
      returns zero results.
- [ ] `grep -rn '"ping"' mocapi-server/src/main/java` returns
      zero results.
- [ ] `mvn verify` passes.
