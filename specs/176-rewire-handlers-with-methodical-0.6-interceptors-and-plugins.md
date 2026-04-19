# Rewire handlers with Methodical 0.6 interceptors and plugins

## What to build

Adopt Methodical 0.6's interceptor chain, collapse mocapi's per-kind
dispatch into a unified handler model, and expose two extension tiers
(bean-level interceptors + per-handler plugins) for cross-cutting
concerns. This is the foundation every observability / entitlement
spec in the queue will plug into.

### Premise

Methodical 0.6 provides:
- `MethodInvoker<A>` — stateless functional invoker.
- `MethodInvokerFactory` — stateless; all config via
  `Consumer<MethodInvokerConfig<A>>` per `create(...)` call.
- `MethodInvokerConfig<A>` — `resolver(...)` + `interceptor(...)`.
- `MethodInterceptor<A>` — `Object intercept(MethodInvocation<? extends A>)`.
- `MethodInvocation<A>` — `method()`, `target()`, `argument()`,
  `resolvedParameters()`, `proceed()`.
- First-added-is-outermost ordering.

All cross-cutting behavior (MDC, tracing, metrics, guards, rate
limits, JSON schema validation) becomes `MethodInterceptor` beans or
interceptors attached per handler. No parallel observer SPI, no
Methodical-adjacent machinery in mocapi.

### Scope of this spec

1. Bump Methodical to `0.6.0` in `pom.xml`.
2. Rename the four internal handler interfaces.
3. Introduce sealed `MethodHandler` supertype with `method()` and
   `bean()`.
4. Introduce `FooHandlerConfig` + `FooPlugin` SPI per kind.
5. Introduce `Guard` SPI (mocapi concept, not Methodical).
6. Wire bean-level `MethodInterceptor<? super X>` autowiring into the
   four handler providers.
7. Rewrite `AnnotationMcpTool` / `Prompt` / `Resource` /
   `ResourceTemplate` to build invokers via the new customizer pattern
   + plugin-driven config.
8. Turn the current JSON schema pre-validation step into a
   `MethodInterceptor<JsonNode>` bean.
9. Replace the 168-vintage `McpMdcScope` wiring with an
   `McpMdcInterceptor` bean.
10. Handler services (`McpToolsService`, etc.) filter list-time via
    `handler.guards()` and reject call-time via `GuardInterceptor`.

Out of scope (future specs): metrics impl (171), tracing impl (175),
actuator (173/174). Each of those becomes a thin "add an interceptor"
spec post-176.

---

### 1. Methodical bump

- `pom.xml`: `<methodical.version>0.6.0</methodical.version>`.
- No other transitive version changes expected.

### 2. Rename internal handler interfaces

In `mocapi-api`:

| Old name                     | New name                         |
|------------------------------|----------------------------------|
| `McpTool`                    | `ToolHandler`                    |
| `McpToolProvider`            | `ToolHandlerProvider`            |
| `McpPrompt`                  | `PromptHandler`                  |
| `McpPromptProvider`          | `PromptHandlerProvider`          |
| `McpResource`                | `ResourceHandler`                |
| `McpResourceProvider`        | `ResourceHandlerProvider`        |
| `McpResourceTemplate`        | `ResourceTemplateHandler`        |
| `McpResourceTemplateProvider`| `ResourceTemplateHandlerProvider`|

All usage sites in `mocapi-server`, `examples/`, and tests updated.
Breaking change — but the Javadoc on these (added earlier) already
says "most applications never implement this directly," and the
downstream impact is limited to mocapi-internal code + the example
app.

### 3. Sealed `MethodHandler` supertype

New file `mocapi-api/src/main/java/com/callibrity/mocapi/api/handlers/MethodHandler.java`:

```java
public sealed interface MethodHandler
    permits ToolHandler, PromptHandler, ResourceHandler, ResourceTemplateHandler {

  HandlerKind kind();
  String name();
  Method method();
  Object bean();
  List<Guard> guards();
}
```

Each subtype adds its own descriptor and invoker:

```java
public non-sealed interface ToolHandler extends MethodHandler {
  Tool descriptor();
  MethodInvoker<JsonNode> invoker();
  @Override default HandlerKind kind() { return HandlerKind.TOOL; }
  @Override default String name() { return descriptor().name(); }
}
```

Same shape for `PromptHandler` (`Prompt` descriptor + `MethodInvoker<Map<String, String>>`),
`ResourceHandler` (`Resource` descriptor + `MethodInvoker<Object>`),
`ResourceTemplateHandler` (`ResourceTemplate` descriptor +
`MethodInvoker<Map<String, String>>`).

The existing concrete annotation-backed implementations
(`AnnotationMcpTool` → `AnnotationToolHandler`, etc.) get `method()` /
`bean()` / `guards()` fields populated at construction. They already
hold the `Method` and target internally.

### 4. Handler config + plugin SPI

Four pairs of interfaces in `mocapi-api/src/main/java/com/callibrity/mocapi/api/handlers/`.

**`ToolHandlerConfig.java`:**

```java
public interface ToolHandlerConfig {
  Method method();
  Object bean();
  Tool descriptor();
  ToolHandlerConfig guard(Guard guard);
  ToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor);
}

public interface ToolPlugin {
  void customize(ToolHandlerConfig cfg);
}
```

Symmetric `PromptHandlerConfig` / `PromptPlugin`, `ResourceHandlerConfig` /
`ResourcePlugin`, `ResourceTemplateHandlerConfig` / `ResourceTemplatePlugin`.
Argument type differs per kind; keep them split rather than generic.

Semantics:
- `.guard(g)` stores `g` on the handler (for list-time visibility)
  **and** attaches a `GuardInterceptor` (for call-time enforcement).
- `.interceptor(i)` attaches only to the invoker chain.
- Multiple `.guard(...)` calls accumulate; all guards must pass for
  list visibility and call dispatch (logical AND).
- Multiple `.interceptor(...)` calls accumulate in registration order
  (outermost first, per Methodical convention).

### 5. `Guard` SPI

New file `mocapi-api/src/main/java/com/callibrity/mocapi/api/handlers/Guard.java`:

```java
public interface Guard {
  boolean allows(GuardContext ctx);
  default Map<String, Object> describe() { return Map.of(); }
}

public interface GuardContext {
  McpSession session();
}
```

Pure mocapi concept — Methodical has no visibility model. List-time
filtering reads `handler.guards()`; each guard's `describe()` lands
in future actuator output.

A built-in `GuardInterceptor` (mocapi-internal) wraps a list of
guards and denies the chain at call time if any deny:

```java
final class GuardInterceptor<A> implements MethodInterceptor<A> {
  private final List<Guard> guards;

  @Override
  public Object intercept(MethodInvocation<? extends A> inv) {
    var ctx = new DefaultGuardContext(McpSession.CURRENT.get());
    for (var g : guards) {
      if (!g.allows(ctx)) {
        throw new HandlerAccessDeniedException();
      }
    }
    return inv.proceed();
  }
}
```

`HandlerAccessDeniedException` is caught in the `McpToolsService`
catch block and mapped to JSON-RPC `-32602` "<handler-type> not
found" (leak-free).

### 6. Bean-level interceptor autowiring

`MocapiServerToolsAutoConfiguration` (and the prompt / resource
autoconfigs) autowire `List<MethodInterceptor<? super X>>` for their
respective arg type. Each list is forwarded into the matching handler
provider, which passes them into every invoker the provider creates.

```java
@Bean
ToolHandlerProvider toolHandlerProvider(
    ApplicationContext ctx,
    MethodInvokerFactory factory,
    MethodSchemaGenerator generator,
    @Autowired(required = false) List<ToolPlugin> toolPlugins,
    @Autowired(required = false) List<MethodInterceptor<? super JsonNode>> toolInterceptors,
    List<ParameterResolver<? super JsonNode>> toolResolvers) {
  return new AnnotationToolHandlerProvider(
      ctx, generator, factory, toolResolvers,
      toolPlugins == null ? List.of() : toolPlugins,
      toolInterceptors == null ? List.of() : toolInterceptors);
}
```

Mirror for prompts (`MethodInterceptor<? super Map<String, String>>`),
resources (`MethodInterceptor<? super Object>`), and resource
templates.

### 7. Rewrite annotation handler construction

Replace the old `factory.create(method, target, type, resolvers)`
list-of-resolvers overload with the Consumer-of-config form. Sketch
for tools (`AnnotationToolHandler` replacing `AnnotationMcpTool`):

```java
public static List<ToolHandler> buildTools(
    MethodSchemaGenerator schemaGenerator,
    MethodInvokerFactory factory,
    List<ParameterResolver<? super JsonNode>> resolvers,
    List<ToolPlugin> plugins,
    List<MethodInterceptor<? super JsonNode>> beanInterceptors,
    Object target,
    StringValueResolver valueResolver) {

  return methodsAnnotatedWith(target, ToolMethod.class).stream()
      .map(m -> buildOne(m, target, schemaGenerator, factory, resolvers, plugins, beanInterceptors, valueResolver))
      .toList();
}

private static ToolHandler buildOne(Method method, Object target, ...) {
  var descriptor = descriptorFor(method, target, schemaGenerator, valueResolver);
  var cfg = new DefaultToolHandlerConfig(method, target, descriptor);
  plugins.forEach(p -> p.customize(cfg));

  var allInterceptors = new ArrayList<MethodInterceptor<? super JsonNode>>();
  allInterceptors.addAll(beanInterceptors);   // globals: outermost
  allInterceptors.addAll(cfg.interceptors()); // plugin-added: inner

  if (!cfg.guards().isEmpty()) {
    allInterceptors.add(new GuardInterceptor<>(List.copyOf(cfg.guards())));
  }

  MethodInvoker<JsonNode> invoker = factory.create(method, target, JsonNode.class, ic -> {
    resolvers.forEach(ic::resolver);
    allInterceptors.forEach(ic::interceptor);
  });

  return new DefaultToolHandler(descriptor, method, target, invoker, cfg.guards());
}
```

Apply the same shape to `AnnotationPromptHandler`,
`AnnotationResourceHandler`, `AnnotationResourceTemplateHandler`.

### 8. JSON schema validator → interceptor

The current `validateInput(...)` in `McpToolsService` moves into a
dedicated `MethodInterceptor<JsonNode>`:

```java
public final class JsonSchemaValidatingInterceptor implements MethodInterceptor<JsonNode> {
  private final Schema schema;

  public JsonSchemaValidatingInterceptor(Schema schema) { this.schema = schema; }

  @Override
  public Object intercept(MethodInvocation<? extends JsonNode> inv) {
    var failure = Validator.forSchema(schema).validate(
        new JsonParser(inv.argument().toString()).parse());
    if (failure != null) {
      throw new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, failure.getMessage());
    }
    return inv.proceed();
  }
}
```

Attached per-tool in the handler's invoker (not a global — the schema
is tool-specific). `AnnotationToolHandler.buildOne(...)` adds it as
the **innermost** interceptor so it runs right before the reflective
call:

```java
allInterceptors.add(new JsonSchemaValidatingInterceptor(loadSchema(method, descriptor)));
```

`McpToolsService.invokeTool(...)` drops its own `validateInput` call;
the interceptor handles it.

### 9. MDC as an interceptor

Replace the spec-168 `McpMdcScope` wiring (currently invoked inside
`McpToolsService` / `McpPromptsService` / `McpResourcesService`) with
a single global `MethodInterceptor<Object>` bean. The `McpMdcScope`
utility itself stays — the interceptor calls it in a try-with-
resources:

```java
public final class McpMdcInterceptor implements MethodInterceptor<Object> {
  @Override
  public Object intercept(MethodInvocation<?> inv) {
    var kind = kindOf(inv.method());
    var name = handlerNameOf(inv.method());
    try (var scope = McpMdcScope.push(kind, name, /* requestId */ null)) {
      return inv.proceed();
    }
  }
}
```

Registered as a Spring bean in `MocapiServerAutoConfiguration` (or a
new `MocapiMdcAutoConfiguration`). Typed as
`MethodInterceptor<Object>` so it's picked up by every kind's bean-
level autowiring.

The service-side `McpMdcScope.push(...)` calls in the four service
classes are removed.

### 10. Handler services: list-time filter + call-time enforcement

`McpToolsService` (and the prompt / resource counterparts):

- `listTools(...)`: existing pagination stays. Just add an upstream
  filter that retains only tools whose guards all allow a
  `GuardContext(currentSession)`.
- `callTool(...)`: existing lookup stays. Drop `validateInput(...)` —
  moved into the schema interceptor. `invokeTool(...)` body becomes:
  ```java
  try {
    return toCallToolResult(handler.invoker().invoke(args));
  } catch (HandlerAccessDeniedException denied) {
    throw new JsonRpcException(INVALID_PARAMS, "Tool " + name + " not found.");
  } catch (ConstraintViolationException cve) {
    return CallToolResult.error(cve.getMessage());  // existing path
  } catch (Exception e) {
    log.warn("Tool {} threw", name, e);
    return toErrorCallToolResult(e);
  }
  ```

The `DefaultMcpToolContext` construction moves into the invoke-time
context plumbing that's already there — no structural change.

Symmetric treatment for prompts (filter `listPrompts`, let
`HandlerAccessDeniedException` map to JSON-RPC `-32602` "prompt not
found") and resources / resource templates.

---

## Acceptance criteria

- [ ] `pom.xml` at `methodical.version=0.6.0`; whole reactor builds
      green on a clean checkout.
- [ ] `McpTool*` / `McpPrompt*` / `McpResource*` / `McpResourceTemplate*`
      renamed throughout mocapi-api, mocapi-server, examples, and all
      tests.
- [ ] `MethodHandler` sealed supertype in `com.callibrity.mocapi.api.handlers`
      with `method()`, `bean()`, `guards()`.
- [ ] Four `*HandlerConfig` + `*Plugin` pairs committed in the same
      package. Config semantics match the spec (guard attaches at
      handler level AND as an interceptor; interceptor is
      invoker-only).
- [ ] `Guard` + `GuardContext` + `HandlerAccessDeniedException` committed.
- [ ] `GuardInterceptor<A>` built-in; constructed automatically by the
      handler provider when the handler has one or more guards.
- [ ] Bean-level `MethodInterceptor<? super X>` autowiring wired into
      each autoconfig. Missing-bean case doesn't crash.
- [ ] `AnnotationToolHandler` / `AnnotationPromptHandler` /
      `AnnotationResourceHandler` / `AnnotationResourceTemplateHandler`
      rewritten against the new factory API. Old stateful-factory
      constructions removed.
- [ ] `JsonSchemaValidatingInterceptor` takes over input validation for
      tools. `McpToolsService.validateInput(...)` removed.
- [ ] `McpMdcInterceptor` replaces the direct `McpMdcScope.push(...)`
      calls in the four services. Spec-168 MDC keys (`mcp.session`,
      `mcp.handler.kind`, `mcp.handler.name`, `mcp.request`)
      unchanged.
- [ ] `McpToolsService#listTools` / `McpPromptsService#listPrompts` /
      `McpResourcesService#listResources` /
      `McpResourceTemplatesService#listResourceTemplates` filter out
      handlers whose guards deny access for the current session.
- [ ] `HandlerAccessDeniedException` mapped to JSON-RPC `-32602`
      `<kind> not found` in each service's call path.
- [ ] Unit tests added: `DefaultToolHandlerConfig` (guard accumulates,
      interceptor accumulates, order preserved); `GuardInterceptor`
      (denies short-circuit the chain; allows call `proceed()`);
      `JsonSchemaValidatingInterceptor` (passes valid, throws
      `JsonRpcException` with `INVALID_PARAMS` on invalid).
- [ ] Integration test: a tool with `cfg.guard(g)` where `g` always
      denies is excluded from `tools/list` and returns "not found" on
      `tools/call`; another tool without a guard works normally.
- [ ] Integration test: a bean-level `MethodInterceptor<JsonNode>`
      wraps every tool invocation (asserted via a counter
      interceptor); plugin-attached interceptors nest inside.
- [ ] Ralph's spec-168 `McpMdcScope.push(...)` call sites removed from
      the service classes; only the interceptor retains them.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] SonarCloud: no new issues beyond existing.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` `### Breaking changes`:
    - Renames (`McpTool` → `ToolHandler`, etc.).
    - `MethodHandler` sealed supertype.
    - Handler discovery now runs every `ToolPlugin` / `PromptPlugin` /
      `ResourcePlugin` / `ResourceTemplatePlugin` bean; bean-level
      `MethodInterceptor<? super X>` beans apply to every matching
      handler.
    - `GuardInterceptor` enforces call-time access; list-time
      filtering hides denied handlers.
    - Methodical 0.6.0 required.
- [ ] `CHANGELOG.md` `### Added`:
    - `Guard` + `GuardContext` for mocapi-level entitlements.
    - `ToolHandlerConfig` / `ToolPlugin` (etc.) as the per-handler
      extension point.
    - `JsonSchemaValidatingInterceptor` (now the canonical place
      tool-input validation runs).
    - `McpMdcInterceptor` (supersedes the service-side MDC wiring from
      spec 168 — same keys, same semantics, just plugged in via the
      chain).
- [ ] `docs/handlers.md` (new): explains `MethodHandler`, the two
      extension tiers (bean interceptors + plugins), the guard model,
      and a worked example of a `ToolPlugin` that reads a custom
      annotation and attaches a guard.
- [ ] `README.md` "Modules" and "Extending" sections updated to
      reference the new names and the plugin SPI.

## Commit

Suggested commit message:

```
Rewire handlers on Methodical 0.6 interceptors + plugins

Tool / prompt / resource / resource-template dispatch now runs
through Methodical's interceptor chain. The existing McpTool /
McpPrompt / McpResource / McpResourceTemplate interfaces are renamed
to ToolHandler / PromptHandler / ResourceHandler / ResourceTemplateHandler
and share a sealed MethodHandler supertype that exposes method() +
bean() for plugin introspection.

Two extension tiers:

* Bean-level MethodInterceptor<? super T> beans picked up by
  autoconfig and applied to every invoker of the matching kind
  (outermost wrap, run first). Typical globals: MDC, tracing,
  metrics.
* Per-handler ToolPlugin / PromptPlugin / ResourcePlugin /
  ResourceTemplatePlugin beans run against a fresh *HandlerConfig at
  discovery. Plugins attach interceptors and/or a Guard. Guards
  affect both list-time visibility and call-time enforcement
  (automatic GuardInterceptor).

JSON schema validation, MDC, and guard enforcement all move into
interceptors. McpToolsService / PromptsService / ResourcesService
become thin JSON-RPC adapters that filter listings by guards, catch
HandlerAccessDeniedException, and map call-time denials to -32602
"<kind> not found" (leak-free).

BREAKING: renames listed above; Methodical 0.6.0 required.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```

## Implementation notes

- Apply changes in the order in the spec body; it minimizes the
  broken-midway window. Specifically: bump Methodical → update
  annotation-handler factory calls first (they break immediately on
  bump) → then do the renames → then add plugin / config / guard /
  interceptor machinery.
- Keep spec 167 (McpLogger) and the shipped spec 168 MDC code as-is;
  this spec only moves MDC into an interceptor and removes the
  service-side invocation of `McpMdcScope.push(...)`. The scope class
  and MDC keys stay.
- Spec 170 (observer SPI) and spec 175 (tracing module as described)
  are **superseded** by this spec. Delete 170 and rewrite 175 as a
  thin "add a tracing interceptor bean" spec after 176 lands.
- Similarly shrink 171 / 172 (metrics module + starter) to "add a
  metrics interceptor bean" after 176 lands. 173 / 174 (actuator)
  stay scoped as they are; they just read the handler list through
  the renamed types.
- Don't optimize the chain assembly in this spec — allocating a few
  `InvocationChain` objects per call is fine. A follow-up can compile
  the chain into a single `Function<A, Object>` at build time if
  profiling warrants.
