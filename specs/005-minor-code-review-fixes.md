# Fix minor issues and suggestions from code review

## What to build

Fix all minor issues and applicable suggestions from `CODE_REVIEW.md`. These are code
quality, consistency, and correctness improvements.

### MINOR-1: Default protocol version lags behind

**File:** `McpStreamingController.java`

Change `private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26"` to reference
`McpServer.PROTOCOL_VERSION` instead of a hardcoded string.

### MINOR-4: Error messages leak internal details

**File:** `McpStreamingController.java`

Change `"Internal error: " + e.getMessage()` to just `"Internal error"` in the catch-all
error response. The full exception is already logged.

### MINOR-5: Missing @ConditionalOnMissingBean on framework beans

**File:** `MocapiToolsAutoConfiguration.java`

Add `@ConditionalOnMissingBean` to the `methodSchemaGenerator` and
`annotationMcpToolProviderFactory` bean methods so users can provide custom
implementations without excluding the entire auto-configuration.

### MINOR-8: Prompts listed in indeterminate order

**File:** `McpPromptsCapability.java`

Add `.sorted(Comparator.comparing(McpPromptDescriptor::name))` to `listPrompts()` to
match the alphabetical sorting already used in `listTools()`.

### MINOR-9: @Inherited on method-level annotations has no effect

**Files:** `Tool.java`, `Prompt.java`

Remove `@Inherited` from both `@Tool` and `@Prompt`. Per the Java spec, `@Inherited` only
affects class-level annotations. Its presence on method-level annotations is misleading.
(`@ToolService` and `@PromptService` are class-level — `@Inherited` is correct there.)

### MINOR-12: Test uses literal protocol version string

**File:** `McpServerTest.java`

Replace any literal `"2025-11-25"` assertion with `McpServer.PROTOCOL_VERSION` so the
test doesn't silently diverge from the production constant.

### MINOR-11 + SUGGESTION-6: Empty MocapiPromptsProperties

**Files:** `MocapiPromptsProperties.java`, `mocapi-prompts-defaults.properties`, `MocapiPromptsAutoConfiguration.java`

Delete `MocapiPromptsProperties` (empty class with no fields). Delete
`mocapi-prompts-defaults.properties` (empty file). Remove `@EnableConfigurationProperties`,
`@PropertySource`, and the `props` field from `MocapiPromptsAutoConfiguration`.

### SUGGESTION-1: NON_NULL Jackson customizer is global

**File:** `MocapiAutoConfiguration.java`

The `Jackson2ObjectMapperBuilderCustomizer` (now `JsonMapperBuilderCustomizer` after
Spring Boot 4 migration) applies `NON_NULL` to the entire application's ObjectMapper.
Replace with a dedicated `mcpObjectMapper` bean that copies the application ObjectMapper
and sets `NON_NULL` only on the copy. Update `McpStreamingController` to use the
MCP-specific ObjectMapper via `@Qualifier("mcpObjectMapper")`.

## Acceptance criteria

- [ ] `DEFAULT_PROTOCOL_VERSION` references `McpServer.PROTOCOL_VERSION`, not a string literal
- [ ] No `e.getMessage()` in any client-facing JSON-RPC error response
- [ ] `methodSchemaGenerator` and `annotationMcpToolProviderFactory` beans have `@ConditionalOnMissingBean`
- [ ] `listPrompts()` returns prompts sorted alphabetically by name
- [ ] `@Inherited` is not present on `@Tool` or `@Prompt`
- [ ] `McpServerTest` uses `McpServer.PROTOCOL_VERSION` constant, not a literal string
- [ ] `MocapiPromptsProperties.java` does not exist
- [ ] `mocapi-prompts-defaults.properties` does not exist
- [ ] `MocapiPromptsAutoConfiguration` has no `@EnableConfigurationProperties` or `@PropertySource`
- [ ] A `mcpObjectMapper` bean exists with `NON_NULL` serialization; no global Jackson customizer
- [ ] `McpStreamingController` uses the MCP-specific ObjectMapper
- [ ] `mvn verify` passes

## Implementation notes

- This spec depends on specs 001-004 being complete.
- For SUGGESTION-1, the `mcpObjectMapper` should be `@ConditionalOnMissingBean(name = "mcpObjectMapper")`
  so users can provide their own.
- After Spring Boot 4 migration (spec 003), the Jackson customizer class name will have
  changed — use whatever the current name is, not the 3.x name.
