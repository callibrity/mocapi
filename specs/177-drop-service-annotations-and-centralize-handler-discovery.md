# Drop @ToolService / @PromptService / @ResourceService and centralize handler discovery

## What to build

Delete the class-level `@ToolService` / `@PromptService` /
`@ResourceService` annotations. Replace the per-kind bean scans
(each one filtered by its `@*Service` marker) with a single
discovery pass that walks every bean in the context, groups
(bean, method) pairs by which mocapi method annotation they carry,
and exposes the result as a cache each handler autoconfig consumes.

Each per-kind helper's discovery method narrows to a **per-method
builder**: given a single `(bean, Method)` pair and the usual deps,
it returns one handler. The central discovery pass decides *which*
methods to build from; the helper just constructs.

### Why

The `@*Service` markers exist today purely as an optimization — they
narrow the bean scan from "every bean" to "every bean marked as a
handler service." Discovery is `O(4 × annotated_classes)` instead of
`O(4 × all_beans)`.

Measured cost of an all-bean scan (Apache Commons Lang's
`MethodUtils.getMethodsListWithAnnotation`, four calls per bean,
reflection on already-loaded class metadata) is sub-100 ms even for
Spring apps with thousands of beans. Negligible vs. the rest of
Spring Boot startup.

Dropping the markers removes one layer of ceremony per handler class:
users `@Component` their bean (or `@Bean`-register it manually) and
annotate methods with `@McpTool` / `@McpPrompt` / `@McpResource` /
`@McpResourceTemplate`. No class-level marker. The method-level
annotation is the opt-in.

### Bonus structural cleanup

With discovery centralized, each per-kind helper collapses from
`discover(bean, …)` (walks methods itself) to `build(bean, method, …)`
(constructs one handler from a pre-found pair). Much easier to unit
test — no fake `@ToolService`-annotated fixture class needed; pass
any bean and any `Method` directly.

---

## File-level changes

### Delete the service annotations

- `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/ToolService.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/prompts/PromptService.java`
- `mocapi-api/src/main/java/com/callibrity/mocapi/api/resources/ResourceService.java`

(Spec 172 left these intact; this spec removes them.)

### Introduce the discovery cache

New file
`mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/HandlerMethodsCache.java`:

```java
/**
 * Single-pass scan result: every bean in the context that has one or
 * more @McpTool / @McpPrompt / @McpResource / @McpResourceTemplate
 * methods, grouped by annotation. Each kind's handler autoconfig
 * consumes its slice via {@link #forAnnotation(Class)}.
 */
public record HandlerMethodsCache(
    Map<Class<? extends Annotation>, List<BeanMethod>> methodsByAnnotation) {

  public List<BeanMethod> forAnnotation(Class<? extends Annotation> annotationType) {
    return methodsByAnnotation.getOrDefault(annotationType, List.of());
  }

  public record BeanMethod(Object bean, Method method) {}

  public static HandlerMethodsCache scan(
      ApplicationContext context,
      List<Class<? extends Annotation>> annotationTypes) {
    var map = new EnumMap-ish-structure-or-plain-HashMap<>();
    for (var bean : context.getBeansOfType(Object.class).values()) {
      var target = AopUtils.getTargetClass(bean);
      for (var annotationType : annotationTypes) {
        MethodUtils.getMethodsListWithAnnotation(target, annotationType)
            .forEach(m -> map.computeIfAbsent(annotationType, k -> new ArrayList<>())
                .add(new BeanMethod(bean, m)));
      }
    }
    return new HandlerMethodsCache(Map.copyOf(map));
  }
}
```

Registered as a `@Bean` in `MocapiServerAutoConfiguration` (the
top-level one — it's cross-cutting, not kind-specific):

```java
@Bean
HandlerMethodsCache handlerMethodsCache(ApplicationContext context) {
  return HandlerMethodsCache.scan(
      context,
      List.of(McpTool.class, McpPrompt.class, McpResource.class, McpResourceTemplate.class));
}
```

### Per-kind helper contracts

Each of the four helpers (after spec 172 shape) exposes a `build(…)`
method that constructs **one** handler from a single
`(Object bean, Method method)` pair plus deps:

`CallToolHandlers.java`:

```java
public static CallToolHandler build(
    Object bean,
    Method method,
    MethodSchemaGenerator generator,
    MethodInvokerFactory invokerFactory,
    List<ParameterResolver<? super JsonNode>> resolvers,
    UnaryOperator<String> valueResolver) { ... }
```

Symmetric `build(...)` on `GetPromptHandlers`, `ReadResourceHandlers`,
`ReadResourceTemplateHandlers`. Each helper's old `discover(bean, …)`
method — which walked the bean's methods itself — is **removed**.
Discovery is no longer any helper's responsibility.

### Per-kind autoconfig bean methods

Each kind's handler autoconfig consumes the cache:

```java
// MocapiServerToolsAutoConfiguration
@Bean
List<CallToolHandler> callToolHandlers(
    HandlerMethodsCache cache,
    MethodSchemaGenerator generator,
    MethodInvokerFactory invokerFactory,
    ObjectMapper objectMapper,
    StringValueResolver mcpAnnotationValueResolver) {
  var resolvers = List.of(
      new McpToolContextResolver(),
      new McpToolParamsResolver(objectMapper));
  return cache.forAnnotation(McpTool.class).stream()
      .map(bm -> CallToolHandlers.build(
          bm.bean(),
          bm.method(),
          generator,
          invokerFactory,
          resolvers,
          mcpAnnotationValueResolver::resolveStringValue))
      .toList();
}
```

Symmetric shape for `MocapiServerPromptsAutoConfiguration` (reads
`McpPrompt.class` from the cache, passes the prompt-specific
resolver list) and `MocapiServerResourcesAutoConfiguration` (two
beans: one for `McpResource.class`, one for `McpResourceTemplate.class`).

### Update all `@*Service`-annotated classes

Every class in the codebase, in examples, and in tests that carries
`@ToolService` / `@PromptService` / `@ResourceService`:

- Remove the annotation.
- Ensure the class still becomes a Spring bean some other way:
    - If it was `@Component`-meta-annotated via the `@*Service`, it
      needs an explicit `@Component` now (or the user's app wires it
      as a `@Bean`).
    - Verify spec 172's cleanup already removed the meta-annotation —
      if it didn't, document the migration in this spec's CHANGELOG
      entry.

Global grep for:
```
grep -rn "@ToolService\|@PromptService\|@ResourceService" --include="*.java"
```
Every hit either gets the annotation stripped (and possibly
`@Component` added) or, for tests that relied on the marker for
bean-hood, declared via `@Bean` in a test config.

### Docs + examples

- `README.md` — update every sample that shows `@ToolService` on a
  class.
- `docs/handlers.md` — same.
- `examples/**` — update every example app.

---

## Acceptance criteria

- [ ] `ToolService`, `PromptService`, `ResourceService` files deleted
      from `mocapi-api`.
- [ ] `grep -rn "@ToolService\|@PromptService\|@ResourceService"
      --include="*.java"` returns no hits.
- [ ] `HandlerMethodsCache` exists in
      `com.callibrity.mocapi.server.autoconfigure` with a single
      `scan(...)` static method and a `forAnnotation(...)` accessor.
- [ ] `HandlerMethodsCache` bean is registered in
      `MocapiServerAutoConfiguration`.
- [ ] `CallToolHandlers.build(...)`, `GetPromptHandlers.build(...)`,
      `ReadResourceHandlers.build(...)`, and
      `ReadResourceTemplateHandlers.build(...)` take an
      `(Object bean, Method method)` pair; their prior
      `discover(bean, ...)` methods are removed.
- [ ] The four handler autoconfigs consume the cache via
      `cache.forAnnotation(McpX.class)` and map each entry through
      the kind's `build(...)`.
- [ ] Discovery happens in a single pass (one `for (bean : beans)`
      loop inside `HandlerMethodsCache.scan`). Verified by reading
      the code.
- [ ] All existing tests pass after the service-annotation removal
      and the shape change. Tests that previously had a
      `@ToolService`-annotated fixture keep working (either the
      fixture becomes plain `@Component`, or the test wires it as a
      `@Bean`).
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] No behavior change: `tools/list`, `tools/call`, `prompts/*`,
      `resources/*` responses identical before and after.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Breaking changes`:
      one entry explaining the three service annotations are gone.
      Include a migration snippet:
      ```
      -@ToolService
      +@Component
       public class MyTools { ... }
      ```
      Note that users who registered their handler class as a `@Bean`
      (not `@Component`) don't need to change anything.
- [ ] `docs/handlers.md` no longer mentions `@ToolService` etc.
      Replace with "annotate the method, register the bean however
      your app normally does."

## Commit

Suggested commit message:

```
Drop @ToolService / @PromptService / @ResourceService; one-pass
handler discovery

Three fewer annotations on a mocapi user's surface area. Handler
discovery no longer short-circuits through class-level markers —
instead, a single pass over every bean in the context groups
(bean, method) pairs by annotation into a HandlerMethodsCache.
Each kind's handler autoconfig consumes its slice and maps through
a per-kind build(bean, method, ...) helper.

Measured startup cost of the all-bean scan is sub-100 ms even for
context sizes in the thousands — negligible vs. Spring Boot's own
startup work. The marker annotations were an optimization that
didn't buy much.

Per-kind helpers simplify: the old discover(bean, ...) (which walked
the bean's methods itself) becomes build(bean, method, ...) — one
handler from one pair, no bean iteration inside. Easier to unit
test; no fixture has to carry a class-level marker.

BREAKING: @ToolService / @PromptService / @ResourceService removed
from mocapi-api. Users swap them for @Component (or any other
bean-hood mechanism). The migration is a three-annotation rename.
```

## Implementation notes

- `AopUtils.getTargetClass(bean)` unwraps CGLIB / JDK-dynamic proxies
  so `MethodUtils.getMethodsListWithAnnotation` sees the user's
  declared class, not the proxy. Spring-aop is already a transitive
  dep via spring-context; no new imports needed.
- `context.getBeansOfType(Object.class)` returns every non-abstract
  singleton bean. If some MCP test relies on prototype-scoped beans
  hosting handlers, that's a gotcha — document and fail explicitly
  in the scan if a prototype bean carries a mocapi method
  annotation.
- The `HandlerMethodsCache` bean is immutable post-construction. No
  hot-reload story; if the user adds a new `@McpTool` method at
  runtime (via a late-added bean), they restart. Same behavior as
  today.
- Do *not* add a "scan only beans in a given package" option. The
  whole point is simplicity; configurable scopes are premature.
