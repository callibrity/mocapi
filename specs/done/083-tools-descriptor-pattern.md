# Tools: adopt Descriptor pattern and Cursors utility

## What to build

`McpTool` has been updated to use the nested `Descriptor` record and
`descriptor()` method (same pattern as `McpResource` and `McpPrompt`).
The old accessor methods (`name()`, `title()`, `description()`, `inputSchema()`,
`outputSchema()`) were removed from the interface.

Complete the migration:

### Update AnnotationMcpTool

Replace the individual accessor methods with a single `descriptor()` that
returns `McpTool.Descriptor`. Store the descriptor as a field built in the
constructor.

### Update ToolsRegistry

- Pre-sort descriptors at construction time (like resources/prompts registries)
- Use `Cursors.paginate()` instead of inline pagination
- Delete `encodeCursor`/`decodeCursor` methods
- Use `tool.descriptor().name()` for map keys
- Add `sortedDescriptors` field built in constructor
- Delete `McpToolDescriptor` class — use `McpTool.Descriptor`
- Update `ListToolsResponse` to use `McpTool.Descriptor`

### Update McpToolMethods

Any references to `tool.name()`, `tool.isStreamable()`, etc. need to go
through `tool.descriptor()` for metadata or stay on the interface for
`isStreamable()` (which is behavioral, not metadata).

Note: `isStreamable()` stays on the `McpTool` interface — it's not part
of the descriptor since it's not serialized to clients.

### Update all tests

All test files that create `McpTool` implementations or reference
`McpToolDescriptor` need updating.

### Update compat conformance tools

Any conformance tool that implements `McpTool` needs the `descriptor()` method.

## Acceptance criteria

- [ ] `McpTool` only has `descriptor()`, `call()`, `isStreamable()`
- [ ] `McpTool.Descriptor` record with name, title, description, inputSchema, outputSchema
- [ ] `AnnotationMcpTool` implements `descriptor()`
- [ ] `ToolsRegistry` pre-sorts descriptors, uses `Cursors.paginate()`
- [ ] `McpToolDescriptor` deleted — replaced by `McpTool.Descriptor`
- [ ] `ListToolsResponse` uses `McpTool.Descriptor`
- [ ] All tests updated
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes
