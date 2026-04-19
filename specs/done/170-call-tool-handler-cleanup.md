# Collapse McpTool SPI into a concrete CallToolHandler

## What to build

Delete the `McpTool` interface and the `McpToolProvider` SPI. Merge
`AnnotationMcpTool` into a single concrete class, `CallToolHandler`,
that is built directly by scanning `@ToolService` beans for
`@ToolMethod`-annotated methods. `McpToolsService` holds a
`Map<String, CallToolHandler>` keyed by tool name and dispatches
directly to it.

No third party implements `McpTool` in practice — the only
implementation is the annotation-backed one. Dropping the interface
removes a dead extension point and collapses two layers into one.

This spec is the framework-bearing one for the four-handler cleanup
(170–173). It establishes:

- Naming convention (`{Action}{Noun}Handler` — `CallToolHandler`,
  `GetPromptHandler`, etc.).
- Package layout (`com.callibrity.mocapi.server.tools.CallToolHandler`,
  no `annotation` subpackage).
- How the annotation scanner produces handlers directly (no provider
  abstraction).
- How services hold handlers (concrete registry, no `List<*Provider>`).

Specs 171–173 mirror this for prompts, resources, and resource
templates with far less ceremony.

---

## File-level changes

### Delete

- `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/McpTool.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/McpToolProvider.java`
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/annotation/AnnotationMcpTool.java`
  (contents absorbed into `CallToolHandler`).
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/ToolServiceMcpToolProvider.java`
  (responsibilities absorbed into a new builder / the service).
- Their associated tests.

### Create

`mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandler.java`:

```java
public final class CallToolHandler {
  private final Tool descriptor;
  private final Method method;
  private final Object bean;
  private final MethodInvoker<JsonNode> invoker;

  CallToolHandler(Tool descriptor, Method method, Object bean, MethodInvoker<JsonNode> invoker) {
    this.descriptor = descriptor;
    this.method = method;
    this.bean = bean;
    this.invoker = invoker;
  }

  public Tool descriptor() { return descriptor; }
  public String name() { return descriptor.name(); }
  public Method method() { return method; }
  public Object bean() { return bean; }

  /** Dispatches the call. Returns the raw method result; conversion
   *  to {@link CallToolResult} stays in {@link McpToolsService}. */
  public Object call(JsonNode arguments) {
    return invoker.invoke(arguments);
  }
}
```

`mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/CallToolHandlers.java`
— the factory / scanner that replaces `AnnotationMcpTool.createTools`
and `ToolServiceMcpToolProvider`:

```java
public final class CallToolHandlers {
  private CallToolHandlers() {}

  public static List<CallToolHandler> discover(
      ApplicationContext context,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      List<ParameterResolver<? super JsonNode>> resolvers,
      StringValueResolver valueResolver) {
    // Iterates every @ToolService bean; for each @ToolMethod method,
    // builds a descriptor (name/title/description/input/output schema)
    // and a MethodInvoker<JsonNode>; returns the assembled handlers.
  }
}
```

The descriptor-building logic that currently lives in
`AnnotationMcpTool`'s constructor moves into a private helper here.

### Modify

- `mocapi-server/src/main/java/com/callibrity/mocapi/server/tools/McpToolsService.java`:
    - Constructor takes `List<CallToolHandler>` instead of
      `List<McpToolProvider>`.
    - Internally keeps a `Map<String, CallToolHandler>` (built from
      the list) plus a sorted `List<Tool>` for `listTools`.
    - `callTool(...)` looks up the handler in the map and invokes it
      via `handler.call(arguments)`.
    - Input validation (the existing `validateInput` JSON schema
      check) stays put in this service for now — it'll move to an
      interceptor in spec 174.
- `mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/MocapiServerToolsAutoConfiguration.java`:
    - Drop the `ToolServiceMcpToolProvider` bean definition.
    - Add a `CallToolHandlers`-based bean that produces `List<CallToolHandler>`
      for the service, or inline the discovery inside the `McpToolsService`
      bean method.

### Tests

- `mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/annotation/AnnotationMcpToolTest.java` →
  rename to `CallToolHandlerTest.java`, retarget at the new class.
  Same assertions, new types.
- `mocapi-server/src/test/java/com/callibrity/mocapi/server/tools/McpToolsServiceTest.java`:
  update to construct `McpToolsService` with `List<CallToolHandler>`.
  `createProvider(Object target)` helper becomes
  `createHandlers(Object target)` returning
  `List<CallToolHandler>`. No more `McpToolProvider`.
- `mocapi-server/src/test/java/com/callibrity/mocapi/server/autoconfigure/ToolServiceMcpToolProviderTest.java`:
  delete or rename to test the new bean wiring in
  `MocapiServerToolsAutoConfiguration`.
- All compliance tests that reference `McpTool` / `McpToolProvider`
  get updated to the concrete class.

---

## Acceptance criteria

- [ ] `McpTool` and `McpToolProvider` interfaces removed from
      `mocapi-api`.
- [ ] `AnnotationMcpTool` removed; replaced by `CallToolHandler` +
      `CallToolHandlers` in `com.callibrity.mocapi.server.tools`.
- [ ] `ToolServiceMcpToolProvider` removed; its discovery logic
      lives in `CallToolHandlers.discover(...)`.
- [ ] `McpToolsService` takes `List<CallToolHandler>` in its
      constructor; `callTool(...)` dispatches directly.
- [ ] `MocapiServerToolsAutoConfiguration` wires the new bean shape.
- [ ] All tool-related tests pass under the new type names.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] SonarCloud: no new issues.
- [ ] No external behavior change — `tools/list` and `tools/call`
      produce identical responses as before.

## Docs

- [ ] `CHANGELOG.md` under `## [Unreleased]` / `### Breaking changes`:
      entry explaining that `McpTool` and `McpToolProvider` are gone
      and that tool discovery is purely annotation-driven now. Note
      that no public API survived with the old names — users never
      needed to touch these types.
- [ ] `docs/handlers.md` (new): one-paragraph overview of the
      handler model for each kind, showing how `@ToolMethod` produces
      a `CallToolHandler` internally.

## Commit

Suggested commit message:

```
Collapse McpTool SPI into concrete CallToolHandler

Tool dispatch no longer goes through an interface + provider + annotation
impl stack — the only caller of those was the annotation scanner itself.
New CallToolHandler is a plain class built directly from @ToolMethod
methods by CallToolHandlers#discover. McpToolsService holds a
Map<String, CallToolHandler> keyed by tool name and dispatches without
the indirection.

BREAKING: McpTool and McpToolProvider interfaces removed from
mocapi-api. No user code implemented these in practice; the rename in
mocapi-server is internal.

This is the framework-bearing spec for the 170–173 handler cleanup
series. Prompts, resources, and resource templates follow in dedicated
specs and inherit the naming + layout conventions introduced here.
```

## Implementation notes

- Keep this spec focused on the *structural* collapse. No Methodical
  0.6, no interceptors, no guards — those are spec 174 and beyond.
- Avoid relocating unrelated code while you're here. If the current
  `annotation/` subpackage has helper classes that aren't tool-specific
  (e.g. `Names`, `AnnotationStrings`), leave them where they are for
  this spec. Spec 171 may surface opportunities to relocate shared
  helpers once the prompt cleanup lands.
- The `CallToolHandlers` helper is a package-private / static utility,
  not a bean. It runs during bean construction (inside the autoconfig
  bean method) and hands a `List<CallToolHandler>` to the service.
- Descriptor-building helpers (`Names.identifier`, `Names.humanReadableName`,
  `Parameters.descriptionOf`) are currently in `com.callibrity.mocapi.server.tools.annotation`.
  Keep the package path for now; follow-up spec can rename / move.
