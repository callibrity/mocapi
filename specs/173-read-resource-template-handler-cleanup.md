# Collapse McpResourceTemplate SPI into a concrete ReadResourceTemplateHandler

## What to build

Final spec in the handler-cleanup series. Delete `McpResourceTemplate`
and `McpResourceTemplateProvider`, merge `AnnotationMcpResourceTemplate`
into a concrete `ReadResourceTemplateHandler` built by scanning
`@ResourceTemplateMethod`-annotated methods. Resource templates
differ from fixed resources in that their URI has path variables
(e.g. `file:///logs/{date}`); `read(Map<String, String>)` takes the
resolved variable bindings.

## File-level changes

### Delete

- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/McpResourceTemplate.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/McpResourceTemplateProvider.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/annotation/AnnotationMcpResourceTemplate.java`
- Any dedicated `ResourceTemplateServiceMcp*Provider` autoconfig that
  may exist (verify during implementation — the pattern follows
  specs 170–172).
- Their associated tests.

### Create

`mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ReadResourceTemplateHandler.java`:

```java
public final class ReadResourceTemplateHandler {
  private final ResourceTemplate descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<Map<String, String>> invoker;
  private final List<CompletionCandidate> completionCandidates;

  ReadResourceTemplateHandler(
      ResourceTemplate descriptor,
      Method method,
      Object bean,
      MethodInvoker<Map<String, String>> invoker,
      List<CompletionCandidate> completionCandidates) { ... }

  public ResourceTemplate descriptor() { return descriptor; }
  public String uriTemplate() { return descriptor.uriTemplate(); }
  public Method method() { return method; }
  public Object bean() { return bean; }
  public List<CompletionCandidate> completionCandidates() { return completionCandidates; }

  public ReadResourceResult read(Map<String, String> pathVariables) {
    return (ReadResourceResult) invoker.invoke(pathVariables == null ? Map.of() : pathVariables);
  }
}
```

`mocapi-server/src/main/java/com/callibrity/mocapi/server/resources/ReadResourceTemplateHandlers.java`:
static `discover(...)` factory.

### Modify

- The resource-template side of `McpResourcesService` (or wherever
  template dispatch currently lives — may be a dedicated
  `McpResourceTemplatesService`): constructor takes
  `List<ReadResourceTemplateHandler>`. Registry keyed by URI
  template. Template matching to a concrete URI stays the same.
- `McpCompletionsService` registration for template variables:
  caller iterates `handler.completionCandidates()` — same as the
  prompt cleanup.
- Relevant `MocapiServer*AutoConfiguration` drops any
  `*ResourceTemplateProvider` bean; `List<ReadResourceTemplateHandler>`
  comes from `ReadResourceTemplateHandlers.discover(...)`.

### Tests

- `AnnotationMcpResourceTemplateTest` (if one exists) →
  `ReadResourceTemplateHandlerTest`.
- Resource-templates service test updated.
- Compliance tests using the old types updated.

## Acceptance criteria

- [ ] `McpResourceTemplate` and `McpResourceTemplateProvider`
      removed.
- [ ] `AnnotationMcpResourceTemplate` removed; provider autoconfig
      (if any) removed.
- [ ] `ReadResourceTemplateHandler` + `ReadResourceTemplateHandlers`
      created in `com.callibrity.mocapi.server.resources`.
- [ ] Template dispatch goes through the new handler map.
- [ ] Completion registration (`McpCompletionsService`) still works
      for template variables.
- [ ] All resource-template tests pass under new type names.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] No external behavior change for `resources/templates/list` or
      `resources/read` against templated URIs.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Breaking changes`:
      entry mirroring 170–172.
- [ ] `docs/handlers.md` gains a resource-templates section. After
      this lands, the 170–173 series has fully collapsed every SPI
      interface; the doc can conclude with a "there is no SPI users
      implement — only annotations" summary.
- [ ] If the `annotation/` subpackages now contain only shared
      helper utilities (no kind-specific classes), consider moving
      those helpers to `com.callibrity.mocapi.server.util` or similar
      and deleting the empty subpackages. Call it out in the spec's
      PR description but don't require it as an acceptance criterion.

## Commit

Suggested commit message:

```
Collapse McpResourceTemplate SPI into concrete
ReadResourceTemplateHandler

Closes out the 170–173 handler-cleanup series. McpResourceTemplate +
McpResourceTemplateProvider interfaces removed;
AnnotationMcpResourceTemplate folded into
ReadResourceTemplateHandler + ReadResourceTemplateHandlers#discover.
Template dispatch goes through a Map keyed by URI template.

After this spec lands, mocapi has no public handler-SPI interfaces
at all — tools, prompts, resources, and resource templates are all
annotation-driven; the internal representation for each is a single
concrete class. The `annotation` subpackages are cleanup-ready for
relocation or deletion.

BREAKING: McpResourceTemplate and McpResourceTemplateProvider
interfaces removed.
```
