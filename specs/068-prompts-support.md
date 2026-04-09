# MCP Prompts support

## What to build

Add prompt support to Mocapi, following the same patterns as tools and resources.

### Prompt model

```java
public record McpPrompt(String name, String description, List<PromptArgument> arguments) {}
public record PromptArgument(String name, String description, boolean required) {}
public record PromptMessage(String role, PromptContent content) {}
```

`PromptContent` is polymorphic (like tool content):
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextPromptContent.class, name = "text"),
    @JsonSubTypes.Type(value = ImagePromptContent.class, name = "image"),
    @JsonSubTypes.Type(value = ResourcePromptContent.class, name = "resource")
})
public sealed interface PromptContent permits TextPromptContent, ImagePromptContent, ResourcePromptContent {}

public record TextPromptContent(String text) implements PromptContent {}
public record ImagePromptContent(String data, String mimeType) implements PromptContent {}
public record ResourcePromptContent(EmbeddedPromptResource resource) implements PromptContent {}
public record EmbeddedPromptResource(String uri, String mimeType, String text) {}
```

### McpPromptProvider interface

```java
public interface McpPromptProvider {
    List<McpPrompt> getPrompts();
    GetPromptResponse get(String name, Map<String, String> arguments);
}
```

```java
public record GetPromptResponse(String description, List<PromptMessage> messages) {}
```

### McpPromptMethods — JSON-RPC service

A `@JsonRpcService` that handles:

- `prompts/list` — returns all registered prompts with their argument definitions
- `prompts/get` — gets a prompt by name with arguments, params: `{ "name": "...", "arguments": { ... } }`

### PromptsRegistry

Aggregates prompts from all `McpPromptProvider` beans. Handles lookup by name.

### Auto-configuration

- Register `McpPromptMethods` when `McpPromptProvider` beans exist
- Add `prompts` capability to `InitializeResponse` when prompts are available

### Conformance tools (in mocapi-compat)

**test_simple_prompt** — no arguments
```json
{"messages": [{"role": "user", "content": {"type": "text", "text": "This is a simple prompt for testing."}}]}
```

**test_prompt_with_arguments** — args: `arg1` (required), `arg2` (required)
With `{arg1: "hello", arg2: "world"}`:
```json
{"messages": [{"role": "user", "content": {"type": "text", "text": "Prompt with arguments: arg1='hello', arg2='world'"}}]}
```

**test_prompt_with_embedded_resource** — arg: `resourceUri` (required)
```json
{
  "messages": [
    {"role": "user", "content": {"type": "resource", "resource": {"uri": "<resourceUri>", "mimeType": "text/plain", "text": "Embedded resource content for testing."}}},
    {"role": "user", "content": {"type": "text", "text": "Please process the embedded resource above."}}
  ]
}
```

**test_prompt_with_image** — no arguments
```json
{
  "messages": [
    {"role": "user", "content": {"type": "image", "data": "<base64 1x1 red PNG>", "mimeType": "image/png"}},
    {"role": "user", "content": {"type": "text", "text": "Please analyze the image above."}}
  ]
}
```

## Acceptance criteria

- [ ] `prompts/list` returns registered prompts with argument definitions
- [ ] `prompts/get` returns prompt messages for known names
- [ ] `prompts/get` substitutes arguments into prompt content
- [ ] `prompts` capability in initialize response when prompts exist
- [ ] Conformance scenarios pass: `prompts-list`, `prompts-get-simple`,
  `prompts-get-with-args`, `prompts-get-embedded-resource`, `prompts-get-with-image`
- [ ] All tests pass
- [ ] `mvn verify` passes
