# Remove McpPromptProvider and McpResourceProvider

## What to build

Both prompts and resources still use a "provider" intermediary that groups
multiple items. Remove the providers — the registries collect individual
beans directly.

### Prompts

`PromptsRegistry` constructor changes from:
```java
public PromptsRegistry(List<McpPromptProvider> providers, int pageSize)
```
to:
```java
public PromptsRegistry(List<McpPrompt> prompts, int pageSize)
```

Delete `McpPromptProvider`. Auto-config injects `List<McpPrompt>` directly.
Each prompt is its own bean.

### Resources

Same change for `ResourcesRegistry` — covered by spec 080, but make sure
`McpResourceProvider` is deleted and the auto-config injects `List<McpResource>`
and `List<McpResourceTemplate>` directly.

### Update conformance tools

Conformance prompts and resources become individual beans instead of
grouped by provider class.

## Acceptance criteria

- [ ] `McpPromptProvider` deleted
- [ ] `PromptsRegistry` takes `List<McpPrompt>` directly
- [ ] Auto-config injects `List<McpPrompt>` 
- [ ] `McpResourceProvider` deleted (if not already by spec 080)
- [ ] Conformance tools updated to individual beans
- [ ] All conformance tests pass
- [ ] `mvn verify` passes
