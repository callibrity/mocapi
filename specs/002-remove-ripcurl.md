# Remove RipCurl dependency from all modules

## What to build

Remove every reference to `com.callibrity.ripcurl` from the entire project, replacing with
the MCP-native types created in spec 001 and simpler alternatives where appropriate.

### POM changes

Remove RipCurl Maven dependencies from all four modules:
- `mocapi-core/pom.xml` — remove `ripcurl-core`
- `mocapi-tools/pom.xml` — remove `ripcurl-core`
- `mocapi-prompts/pom.xml` — remove `ripcurl-core`
- `mocapi-autoconfigure/pom.xml` — remove `ripcurl-autoconfigure`

Add `spring-boot-starter-web` to `mocapi-autoconfigure/pom.xml` (was transitively pulled
via `ripcurl-autoconfigure`, needed for `@RestController` and `SseEmitter`).

### Import replacements

Across all source and test files:
- `com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException` → `com.callibrity.mocapi.server.exception.McpInvalidParamsException`
- `com.callibrity.ripcurl.core.exception.JsonRpcInternalErrorException` → `com.callibrity.mocapi.server.exception.McpInternalErrorException`
- `com.callibrity.ripcurl.core.exception.JsonRpcException` → `com.callibrity.mocapi.server.exception.McpException`
- `com.callibrity.ripcurl.core.invoke.JsonMethodInvoker` → `com.callibrity.mocapi.server.invoke.JsonMethodInvoker`

Update all corresponding class references in code (constructor calls, catch blocks, throws).

### Annotation removal

Remove `@JsonRpcService` and `@JsonRpc` from:
- `McpToolsCapability` — remove class annotation and method annotations
- `McpPromptsCapability` — remove class annotation and method annotations

Remove unused `JsonRpcService` imports from:
- `ToolServiceMcpToolProvider`
- `PromptServiceMcpPromptProvider`

### LazyInitializer replacement

Replace `LazyInitializer<Map<...>>` in both capability classes with a simple `Map` field
populated eagerly. The maps are built from providers injected at construction time —
just call the stream/collect in the constructor directly instead of deferring.

In `McpToolsCapability`:
- Replace `LazyInitializer<Map<String, McpTool>> tools` with `Map<String, McpTool> tools`
- Move the stream/collect logic from `LazyInitializer.of(...)` into the constructor body

In `McpPromptsCapability`:
- Same pattern — replace `LazyInitializer` with direct map construction

### Property rename

- In `mocapi-defaults.properties`: rename `ripcurl.endpoint=/mcp` to `mocapi.endpoint=/mcp`
- In `McpStreamingController`: change `@RequestMapping("${ripcurl.endpoint:/mcp}")` to `@RequestMapping("${mocapi.endpoint:/mcp}")`

### Test cleanup

Remove `RipCurlAutoConfiguration` from `AutoConfigurations.of(...)` in:
- `MocapiAutoConfigurationTest`
- `MocapiToolsAutoConfigurationTest`
- `MocapiPromptsAutoConfigurationTest`

## Acceptance criteria

- [ ] `grep -r "ripcurl" --include="*.java" --include="*.xml" --include="*.properties" .` returns no matches (excluding `docs/`, `specs/`, `CODE_REVIEW.md`)
- [ ] No `LazyInitializer` usage remains anywhere in the project
- [ ] No `@JsonRpcService` or `@JsonRpc` annotations remain anywhere in the project
- [ ] `mocapi.endpoint` property is used instead of `ripcurl.endpoint`
- [ ] `mvn test` passes across all modules
- [ ] All existing integration tests pass (`mvn verify -pl mocapi-example -am`)

## Implementation notes

- Spec 001 must be completed first — the replacement classes must exist before swapping.
- The `McpStreamingController` catches `JsonRpcException` types in some paths — update
  those catch blocks to use `McpException` hierarchy.
- `AnnotationMcpTool` uses `JsonMethodInvoker` from ripcurl — swap to the new one from
  `com.callibrity.mocapi.server.invoke`. The API is the same: `new JsonMethodInvoker(mapper, target, method)` and `invoker.invoke(arguments)`.
- `AnnotationMcpPrompt.getPrompt()` catches `ReflectiveOperationException` which swallows
  `InvocationTargetException`. This will be fixed properly in a later spec (CODE_REVIEW
  MAJOR-7). For now, just update the exception types in the catch/throw.
