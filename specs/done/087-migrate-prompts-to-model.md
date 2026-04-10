# Migrate prompts to use mocapi-model types

## What to build

Replace the prompt types in `mocapi-core` with the canonical types from
`mocapi-model`. Same approach as spec 086 did for resources.

### Replace types

| mocapi-core (delete) | mocapi-model (use) |
|---|---|
| `PromptMessage` | `com.callibrity.mocapi.model.PromptMessage` |
| `PromptArgument` | `com.callibrity.mocapi.model.PromptArgument` |
| `GetPromptResponse` | `com.callibrity.mocapi.model.GetPromptResult` |
| `ListPromptsResponse` | `com.callibrity.mocapi.model.ListPromptsResult` |
| `McpPrompt.Descriptor` | `com.callibrity.mocapi.model.Prompt` |
| `Icon` | `com.callibrity.mocapi.model.Icon` |
| `TextPromptContent` | `com.callibrity.mocapi.model.TextContent` |
| `ImagePromptContent` | `com.callibrity.mocapi.model.ImageContent` |
| `AudioPromptContent` | `com.callibrity.mocapi.model.AudioContent` |
| `ResourcePromptContent` | `com.callibrity.mocapi.model.EmbeddedResource` |
| `ResourceLinkContent` | `com.callibrity.mocapi.model.ResourceLink` |
| `EmbeddedPromptResource` | `com.callibrity.mocapi.model.TextResourceContents` (or `BlobResourceContents`) |
| `PromptContent` (sealed interface) | `com.callibrity.mocapi.model.ContentBlock` |

### Update McpPrompt interface

```java
public interface McpPrompt {
    Prompt descriptor();  // model.Prompt instead of nested Descriptor
    GetPromptResult get(Map<String, String> arguments);
}
```

### Update PromptsRegistry

- `Map<String, McpPrompt>` keyed by `prompt.descriptor().name()`
- `sortedDescriptors` becomes `List<Prompt>` (from model)
- `listPrompts()` returns `ListPromptsResult`

### Update McpPromptMethods

- `prompts/list` returns `ListPromptsResult`
- `prompts/get` returns `GetPromptResult`

### Update conformance tools and tests

All references to old prompt types → model types.

### Delete replaced types from mocapi-core

All prompt content types, response records, `PromptContent` interface,
`Icon`, etc. The `com.callibrity.mocapi.prompts` package should only
contain:
- `McpPrompt` (interface)
- `PromptsRegistry`
- `McpPromptMethods`
- `MocapiPromptsAutoConfiguration`

## Acceptance criteria

- [ ] All prompt types from model used everywhere
- [ ] `McpPrompt.descriptor()` returns `Prompt` from model
- [ ] Response types from model replace custom records
- [ ] Old types deleted from mocapi-core
- [ ] `PromptContent` sealed interface deleted (using `ContentBlock`)
- [ ] All conformance tests pass (39/39)
- [ ] `mvn verify` passes
