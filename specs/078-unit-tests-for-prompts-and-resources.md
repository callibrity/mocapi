# Unit tests for prompts and resources

## What to build

Add unit tests in `mocapi-core` for the prompts and resources implementations.
These should test the registries, JSON-RPC methods, and provider logic in
isolation — no Spring context, no MockMvc.

### Resource tests

**ResourcesRegistryTest:**
- List resources returns all registered direct resources
- List resource templates returns all registered templates
- Read resource by URI returns correct content
- Read resource with template URI substitutes parameters
- Read unknown URI throws or returns error
- Pagination works (nextCursor, final page)

**McpResourceMethodsTest:**
- `resources/list` dispatches to registry and returns correct shape
- `resources/templates/list` dispatches to registry
- `resources/read` with valid URI returns content
- `resources/read` with unknown URI returns error
- `resources/subscribe` accepts URI and returns empty object
- `resources/unsubscribe` returns empty object

### Prompt tests

**PromptsRegistryTest:**
- List prompts returns all registered prompts
- Lookup by name returns correct prompt
- Lookup unknown name throws
- Pagination works

**McpPromptMethodsTest:**
- `prompts/list` dispatches to registry and returns correct shape
- `prompts/get` with valid name and arguments returns messages
- `prompts/get` with unknown name returns error
- Arguments are passed through to the prompt

### Provider tests

**Test that annotation-based providers discover methods correctly:**
- `@Resource` methods are found and registered
- `@Prompt` methods are found and registered
- Method return types are validated

## Acceptance criteria

- [ ] ResourcesRegistryTest with list, read, template, pagination tests
- [ ] McpResourceMethodsTest with all JSON-RPC method tests
- [ ] PromptsRegistryTest with list, lookup, pagination tests
- [ ] McpPromptMethodsTest with all JSON-RPC method tests
- [ ] No Spring context needed — pure unit tests
- [ ] All tests pass
- [ ] `mvn verify` passes
