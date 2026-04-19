# Collapse McpPrompt SPI into a concrete GetPromptHandler

## What to build

Same cleanup as spec 170, applied to prompts: delete `McpPrompt` and
`McpPromptProvider` interfaces, merge `AnnotationMcpPrompt` into a
single concrete `GetPromptHandler` class built directly from
`@PromptService` / `@PromptMethod` scanning. `McpPromptsService`
holds a `Map<String, GetPromptHandler>` keyed by prompt name.

This spec assumes spec 170 has landed — the naming convention,
package layout, and discovery pattern are already established for
tools. Mirror those choices exactly for prompts.

## File-level changes

### Delete

- `mocapi-api/src/main/java/com/callibrity/mocapi/api/prompts/McpPrompt.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/prompts/McpPromptProvider.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/prompts/annotation/AnnotationMcpPrompt.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/PromptServiceMcpPromptProvider.java`
- Their associated tests.

### Create

`mocapi-server/src/main/java/com/callibrity/mocapi/server/prompts/GetPromptHandler.java`:

```java
public final class GetPromptHandler {
  private final Prompt descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<Map<String, String>> invoker;
  private final List<CompletionCandidate> completionCandidates;

  GetPromptHandler(
      Prompt descriptor,
      Method method,
      Object bean,
      MethodInvoker<Map<String, String>> invoker,
      List<CompletionCandidate> completionCandidates) { ... }

  public Prompt descriptor() { return descriptor; }
  public String name() { return descriptor.name(); }
  public Method method() { return method; }
  public Object bean() { return bean; }
  public List<CompletionCandidate> completionCandidates() { return completionCandidates; }

  public GetPromptResult get(Map<String, String> arguments) {
    return (GetPromptResult) invoker.invoke(arguments == null ? Map.of() : arguments);
  }
}
```

`mocapi-server/src/main/java/com/callibrity/mocapi/server/prompts/GetPromptHandlers.java`:

```java
public final class GetPromptHandlers {
  private GetPromptHandlers() {}

  public static List<GetPromptHandler> discover(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super Map<String, String>>> resolvers,
      StringValueResolver valueResolver) {
    // Iterates every @PromptService bean; for each @PromptMethod method,
    // builds a Prompt descriptor + argument list + completion candidates
    // + MethodInvoker; returns assembled handlers.
  }
}
```

Argument / completion-candidate helpers that currently live in
`AnnotationMcpPrompt` as private static methods move into a
package-private helper class in `com.callibrity.mocapi.server.prompts`.

### Modify

- `McpPromptsService`: constructor takes `List<GetPromptHandler>`;
  internally keeps `Map<String, GetPromptHandler>`. `getPrompt(...)`
  dispatches directly.
- `McpCompletionsService` registration stays wherever it is today —
  it walked the handler's `completionCandidates()` method, which is
  now on `GetPromptHandler`. Update the caller to iterate the new
  list.
- `MocapiServerPromptsAutoConfiguration`: drop the
  `PromptServiceMcpPromptProvider` bean. Inline discovery into
  whatever bean currently constructs `McpPromptsService`, or add a
  `List<GetPromptHandler>` bean method that calls
  `GetPromptHandlers.discover(...)`.

### Tests

- `AnnotationMcpPromptTest` → rename to `GetPromptHandlerTest`.
- `McpPromptsServiceTest` update constructor args to
  `List<GetPromptHandler>`.
- `PromptServiceMcpPromptProviderTest` deleted or repurposed for the
  new autoconfig shape.
- Compliance tests referencing the old types get updated.

## Acceptance criteria

- [ ] `McpPrompt` and `McpPromptProvider` interfaces removed.
- [ ] `AnnotationMcpPrompt` and `PromptServiceMcpPromptProvider`
      removed.
- [ ] `GetPromptHandler` + `GetPromptHandlers` created in
      `com.callibrity.mocapi.server.prompts`.
- [ ] `McpPromptsService` dispatches directly to the handler map.
- [ ] Completion registration (`McpCompletionsService`) still works
      end-to-end — integration test verifies prompt argument
      completions register.
- [ ] All prompt-related tests pass under new type names.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] SonarCloud: no new issues.
- [ ] No external behavior change for `prompts/list`, `prompts/get`,
      or `completion/complete` responses.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Breaking changes`:
      entry for `McpPrompt` / `McpPromptProvider` removal, mirroring
      spec 170's language.
- [ ] `docs/handlers.md` gains a prompts section.

## Commit

Suggested commit message:

```
Collapse McpPrompt SPI into concrete GetPromptHandler

Mirrors spec 170's tool cleanup for prompts. McpPrompt +
McpPromptProvider interfaces removed; AnnotationMcpPrompt and
PromptServiceMcpPromptProvider folded into GetPromptHandler +
GetPromptHandlers#discover. McpPromptsService dispatches through a
Map<String, GetPromptHandler> keyed by prompt name.

BREAKING: McpPrompt and McpPromptProvider interfaces removed from
mocapi-api. Like the tool cleanup, no user code implemented these.
```

## Implementation notes

- Spec 170 establishes the pattern. If 170 moves a helper (e.g. a
  shared `Names` utility) out of the `annotation` subpackage, follow
  that decision here. Otherwise leave helpers where they are.
- `McpCompletionsService` registration currently iterates a
  `List<CompletionCandidate>` from each `AnnotationMcpPrompt`. That
  method moves onto `GetPromptHandler` with the same shape — the
  registration call site is a one-line rename.
