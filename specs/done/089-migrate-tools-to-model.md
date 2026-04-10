# Migrate tools to use mocapi-model types

## What to build

Replace the tool types in `mocapi-core` with the canonical types from
`mocapi-model`. Same approach as specs 086 (resources) and 087 (prompts).

### Replace types

| mocapi-core (delete) | mocapi-model (use) |
|---|---|
| `com.callibrity.mocapi.content.CallToolResponse` | `com.callibrity.mocapi.model.CallToolResult` |
| `com.callibrity.mocapi.tools.ToolsRegistry.ListToolsResponse` | `com.callibrity.mocapi.model.ListToolsResult` |
| `McpTool.Descriptor` | `com.callibrity.mocapi.model.Tool` |
| Any remaining content types in core | `ContentBlock` and subtypes from model |

### Update McpTool interface

```java
public interface McpTool {
    Tool descriptor();  // model.Tool instead of nested Descriptor
    Object call(JsonNode arguments);
    default boolean isStreamable() { return false; }
}
```

### Update ToolsRegistry

- `Map<String, McpTool>` keyed by `tool.descriptor().name()`
- `sortedDescriptors` becomes `List<Tool>` (from model)
- `listTools()` returns `ListToolsResult` (from model)
- `callTool()` returns `CallToolResult` (from model)
- Delete inner `ListToolsResponse` record

### Update McpToolMethods

- `tools/list` returns `ListToolsResult`
- `tools/call` returns `CallToolResult`
- Check for `CallToolResult` instead of `CallToolResponse` in pass-through logic

### Update AnnotationMcpTool

- `descriptor()` returns `Tool` from model
- Constructor builds the `Tool` record
- Delete any references to the old `McpTool.Descriptor`

### Update DefaultMethodSchemaGenerator (if it returns descriptor)

Whatever it returns should match what the new `Tool` record expects.

### Update conformance tools and tests

All references to old types → model types. `CallToolResponse` → `CallToolResult`.

### Delete the content package

If `com.callibrity.mocapi.content.CallToolResponse` was the only thing in
the `content` package, delete the package entirely. The content types live
in `mocapi-model` now.

### Clean up tools package

After migration, `com.callibrity.mocapi.tools` should only have:
- `McpTool` (interface)
- `McpToolProvider`
- `ToolsRegistry`
- `McpToolMethods`
- `MocapiToolsAutoConfiguration`
- `ToolServiceMcpToolProvider`
- `annotation/` subpackage
- `schema/` subpackage

All data records (descriptors, responses, content types) should come from model.

## Acceptance criteria

- [ ] `McpTool.descriptor()` returns `Tool` from model
- [ ] `ToolsRegistry.listTools()` returns `ListToolsResult` from model
- [ ] `ToolsRegistry.callTool()` returns `CallToolResult` from model
- [ ] `CallToolResponse` deleted from `com.callibrity.mocapi.content`
- [ ] `content` package deleted if empty
- [ ] `McpTool.Descriptor` nested record deleted
- [ ] `ListToolsResponse` inner record in `ToolsRegistry` deleted
- [ ] Conformance tools updated
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes
