# Spring-decouple CallToolHandlers and GetPromptHandlers

## What to build

Refactor the two handler-discovery helpers shipped by specs 170 and
171 (`CallToolHandlers`, `GetPromptHandlers`) so they carry no Spring
imports. The Spring bean scan moves into each autoconfig; the helpers
become pure Java utilities that take a single `@ToolService` /
`@PromptService` bean at a time.

This cleanup sets the pattern for the remaining handler specs (173
resources, 174 resource templates) — both of those are already
specced to use the Spring-free shape from the start.

## File-level changes

### `CallToolHandlers`

`mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandlers.java`:

- Drop the `ApplicationContext context` parameter from
  `discover(...)`.
- Replace the `StringValueResolver valueResolver` parameter with
  `UnaryOperator<String> valueResolver`.
- Take the target bean directly:

```java
public static List<CallToolHandler> discover(
    Object toolServiceBean,
    MethodSchemaGenerator generator,
    MethodInvokerFactory invokerFactory,
    List<ParameterResolver<? super JsonNode>> resolvers,
    UnaryOperator<String> valueResolver) {
  // Walks @ToolMethod methods on toolServiceBean; returns handlers.
  // No Spring imports.
}
```

Remove these imports from the file:
- `org.springframework.context.ApplicationContext`
- `org.springframework.util.StringValueResolver`

### `GetPromptHandlers`

Symmetric change: drop `ApplicationContext`, swap
`StringValueResolver` for `UnaryOperator<String>`. Signature takes a
`@PromptService` bean directly.

### `MocapiServerToolsAutoConfiguration`

The `List<CallToolHandler>` bean method now does the Spring scan
itself:

```java
@Bean
List<CallToolHandler> callToolHandlers(
    ApplicationContext context,
    MethodSchemaGenerator generator,
    MethodInvokerFactory invokerFactory,
    ObjectMapper objectMapper,
    StringValueResolver mcpAnnotationValueResolver) {
  var resolvers = List.of(
      new McpToolContextResolver(),
      new McpToolParamsResolver(objectMapper));
  return context.getBeansWithAnnotation(ToolService.class)
      .values()
      .stream()
      .flatMap(bean -> CallToolHandlers.discover(
          bean,
          generator,
          invokerFactory,
          resolvers,
          mcpAnnotationValueResolver::resolveStringValue).stream())
      .toList();
}
```

The `StringValueResolver mcpAnnotationValueResolver` bean parameter
stays Spring-typed — the autoconfig is allowed to be Spring-aware.
The `::resolveStringValue` method reference adapts it to
`UnaryOperator<String>` at the boundary.

### `MocapiServerPromptsAutoConfiguration`

Mirror the tools autoconfig shape: own the `@PromptService` bean
scan, adapt `StringValueResolver` to `UnaryOperator<String>` at the
call site.

### Helper utilities

`com.callibrity.mocapi.server.util.AnnotationStrings` currently takes
`StringValueResolver`. Change `resolveOrDefault` / `resolveOrNull` to
take `UnaryOperator<String>` instead. The autoconfig still uses the
Spring-typed resolver and adapts via `::resolveStringValue`; every
other call site already took its resolver from up the stack and just
gets the new type.

### Tests

- Any unit test for `CallToolHandlers` / `GetPromptHandlers` that
  built a mock or fake `ApplicationContext` gets simpler — just pass
  the bean under test.
- Any test using `StringValueResolver` gets a
  `UnaryOperator<String>` lambda instead (e.g. `s -> s` for the
  identity pass-through).

## Acceptance criteria

- [ ] `CallToolHandlers` source file has **zero** `org.springframework.*`
      imports.
- [ ] `GetPromptHandlers` source file has **zero** `org.springframework.*`
      imports.
- [ ] `discover(...)` on both helpers takes a single
      `Object ...ServiceBean` parameter (not `ApplicationContext`).
- [ ] Both helpers use `UnaryOperator<String>` for the value
      resolver.
- [ ] `MocapiServerToolsAutoConfiguration` and
      `MocapiServerPromptsAutoConfiguration` contain the bean scan
      via `context.getBeansWithAnnotation(...)` and adapt the Spring
      `StringValueResolver` via `::resolveStringValue`.
- [ ] `AnnotationStrings.resolveOrDefault` and `resolveOrNull` take
      `UnaryOperator<String>`.
- [ ] All existing tests pass under the new signatures.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] No behavior change: `tools/list`, `tools/call`, `prompts/list`,
      `prompts/get` responses are identical before and after.

## Docs

- [ ] `CHANGELOG.md` under `## [Unreleased]` / `### Changed`: one
      entry noting the helpers are Spring-free now; signature
      change is internal (the helpers aren't public API surface
      for downstream projects).

## Commit

Suggested commit message:

```
Spring-decouple CallToolHandlers and GetPromptHandlers

Handler-discovery helpers take a single @ToolService /
@PromptService bean directly instead of an ApplicationContext.
UnaryOperator<String> replaces the Spring StringValueResolver at
the helper boundary; the autoconfigs adapt their Spring-typed
resolvers via ::resolveStringValue when calling the helpers.

Zero Spring imports in either helper. Pure unit tests (no @SpringBootTest
required) can exercise discovery with a plain `new MyToolBean()`.

Sets the pattern for the remaining handler specs (173, 174) which
already adopt the Spring-free shape from the start.
```

## Implementation notes

- The Spring bean scan stays in the autoconfig — that's where Spring
  belongs. Helpers stay pure Java.
- `StringValueResolver` is a `@FunctionalInterface` with a single
  `resolveStringValue(String)` method, so the adapter is a trivial
  method reference. Don't wrap it in a lambda.
- Don't touch `McpResourcesService` / `McpResourceTemplatesService` or
  their autoconfigs / helpers in this spec — specs 173 and 174 build
  those handlers Spring-free from the start.

## Forward reference

Spec 175 (Methodical 0.6 + interceptors) will introduce
`CallToolHandlerConfig` / `GetPromptHandlerConfig` and a per-handler
`ToolCustomizer` / `PromptCustomizer` SPI. After that lands, the
`discover(...)` helpers will run every registered customizer against
a fresh config per handler (plugins use the config to attach
interceptors), and the autoconfig's job narrows to: scan beans,
pass them plus the customizer list to the helper. The Spring-free
shape this spec establishes is what makes that cleanup easy.
