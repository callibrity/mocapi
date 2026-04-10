# Refactor prompts to use registry lookup and @Argument

## What to build

Refactor the prompts implementation to match the tools pattern: registry
lookup by name, clean `McpPrompt` interface, and `@Argument` for passing
the raw `Map<String, String>` to prompt methods.

### McpPrompt interface

```java
public interface McpPrompt {
    String name();
    String description();
    List<PromptArgument> arguments();
    GetPromptResponse get(Map<String, String> arguments);
}
```

### PromptsRegistry

Builds a `Map<String, McpPrompt>` at initialization from all `McpPromptProvider`
beans. Lookup by name, throw if not found. No iteration through providers at
call time.

```java
public McpPrompt lookup(String name)  // throws if not found
public ListPromptsResponse listPrompts(String cursor)  // paginated
```

### McpPromptMethods

Simplified JSON-RPC service:

```java
@JsonRpcMethod("prompts/get")
public GetPromptResponse getPrompt(String name, @Named("arguments") @Argument Map<String, String> arguments) {
    return promptsRegistry.lookup(name).get(arguments);
}
```

Wait — `@Argument` passes the entire invoke argument (the `JsonNode` params).
The `arguments` field is nested inside params. We still need Jackson to extract
`arguments` from the params object. So `@Argument` isn't right here.

Actually, for prompts, the JSON-RPC params look like:
```json
{"name": "code_review", "arguments": {"language": "java", "code": "..."}}
```

RipCurl/Methodical resolves `name` and `arguments` from the params `JsonNode`.
The `arguments` param is a `JsonNode` (ObjectNode), not a `Map<String, String>`.

So the prompt method receives `JsonNode arguments` from the dispatcher, then
the registry converts it to `Map<String, String>` before calling `McpPrompt.get()`.

### Annotation-based prompts

For the `@Prompt` annotation approach, prompt methods take `@Argument Map<String, String>`:

```java
@PromptService
public class MyPrompts {
    @Prompt(name = "code_review", description = "Review code")
    public GetPromptResponse review(@Argument Map<String, String> args) {
        return new GetPromptResponse(null, List.of(
            new PromptMessage("user", new TextContent(
                "Review this %s:\n%s".formatted(args.get("language"), args.get("code"))))));
    }
}
```

The annotation-based `McpPrompt` implementation invokes the method via
Methodical with `Map<String, String>` as the argument type (not `JsonNode`).
The `McpPromptMethods` converts the `JsonNode` arguments to `Map<String, String>`
before passing to `McpPrompt.get()`.

### Remove provider iteration

Delete the loop in `McpPromptMethods` that iterates providers. The registry
handles lookup.

### Update Methodical dependency

Bump to 0.3.0 (or SNAPSHOT) for `@Argument` support.

## Acceptance criteria

- [ ] `PromptsRegistry` with `lookup(name)` and `listPrompts(cursor)`
- [ ] `McpPrompt.get()` takes `Map<String, String>`
- [ ] No provider iteration — registry lookup by name
- [ ] `@Prompt` methods use `@Argument Map<String, String>`
- [ ] Conformance tests still pass
- [ ] `mvn verify` passes
