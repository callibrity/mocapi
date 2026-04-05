# Remove the prompts module entirely

## What to build

Remove all prompts-related code from the project. The prompts capability is not needed —
Mocapi will focus on tools only.

### Remove mocapi-prompts module

- Delete the entire `mocapi-prompts/` directory
- Remove `<module>mocapi-prompts</module>` from the parent `pom.xml`

### Remove prompts auto-configuration

- Delete `mocapi-autoconfigure/src/main/java/com/callibrity/mocapi/autoconfigure/prompts/` directory entirely (includes `MocapiPromptsAutoConfiguration`, `MocapiPromptsProperties`, `PromptServiceMcpPromptProvider`)
- Delete `mocapi-autoconfigure/src/test/java/com/callibrity/mocapi/autoconfigure/prompts/` directory entirely
- Remove `com.callibrity.mocapi.autoconfigure.prompts.MocapiPromptsAutoConfiguration` from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Delete `mocapi-autoconfigure/src/main/resources/mocapi-prompts-defaults.properties` if it still exists
- Remove the optional `mocapi-prompts` dependency from `mocapi-autoconfigure/pom.xml`

### Remove prompts from McpStreamingController

- Remove the `promptsCapability` field and `@Autowired` injection
- Remove the `prompts/list` and `prompts/get` cases from the `invokeMethod` switch
- Return a proper JSON-RPC method-not-found error if a client calls prompts methods

### Remove prompts from example app

- Delete `CodeReviewPrompts.java` from `mocapi-example`
- Delete `CodeReviewPromptsTest.java` from `mocapi-example`
- Delete `CodeReviewPromptsIT.java` from `mocapi-example`
- Remove `mocapi-prompts` dependency from `mocapi-example/pom.xml`

### Remove prompts from coverage module

- Remove `mocapi-prompts` dependency from `mocapi-coverage/pom.xml` if present

### Remove prompts capability from McpServer

- The `McpServer.initialize()` response includes capabilities discovered from
  `McpServerCapability` beans — with the prompts module gone, the `prompts` capability
  will no longer appear automatically. Verify this works correctly.

### Update README

- Remove the "Creating MCP Prompts" section from `README.md`
- Remove any references to `@PromptService`, `@Prompt`, or `mocapi-prompts`

## Acceptance criteria

- [ ] `mocapi-prompts/` directory does not exist
- [ ] No `prompts` package exists under `mocapi-autoconfigure`
- [ ] No `PromptService`, `Prompt`, `McpPrompt`, or `McpPromptsCapability` references in any Java file
- [ ] No `mocapi-prompts` dependency in any `pom.xml`
- [ ] `McpStreamingController` does not reference prompts capability
- [ ] `README.md` has no prompts documentation
- [ ] `mvn verify` passes
- [ ] The `initialize` response does not include a `prompts` capability

## Implementation notes

- This eliminates several CODE_REVIEW issues that no longer apply: CRITICAL-1 (double
  flatMap in PromptServiceMcpPromptProvider), MAJOR-7 (InvocationTargetException in
  AnnotationMcpPrompt), MINOR-8 (prompt sort order), MINOR-9 (@Inherited on @Prompt),
  MINOR-11 (empty MocapiPromptsProperties), SUGGESTION-6 (empty properties file).
- The `mocapi-spring-boot-starter` should NOT change — it bundles core + autoconfigure,
  and prompts were always optional via `@ConditionalOnClass`.
