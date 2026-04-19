# Simplify mocapi-o11y: drop the Context + Convention classes

## What to build

`mocapi-o11y` currently ships three classes in the core module —
`McpObservationInterceptor`, `McpObservationContext`,
`McpObservationConvention` — matching Micrometer's idiomatic "custom
context + convention + producer" pattern. Our use case doesn't
need that indirection: we have one observation producer (the
interceptor) and a fixed two-tag set. Collapse to one class.

### What the two doomed classes do today

- **`McpObservationContext`** — subclass of `Observation.Context`
  exposing typed `handlerKind()` / `handlerName()` readers. Allocated
  fresh per invocation in the interceptor's hot path and passed via
  `createNotStarted(..., () -> context, ...)`. **Not read by any
  code we ship.** Its theoretical audience is a user who writes a
  custom `ObservationHandler` and prefers typed access over reading
  the tag strings.
- **`McpObservationConvention`** — an `ObservationConvention` that
  produces the same two tags (`mcp.handler.kind`,
  `mcp.handler.name`) via `getLowCardinalityKeyValues(context)`.
  **Not wired into the interceptor** — the interceptor sets both
  tags inline via `lowCardinalityKeyValue(...)` calls. The
  convention is exported for users who want to apply `@Observed`
  to their own methods with our tag set; it duplicates the
  interceptor's inline tagging without driving it.

Both classes are defensible in abstract (canonical Micrometer
shape, hooks for future customization) but concretely earn
nothing today, and "plausibly useful later" is exactly the
speculative infrastructure we'd call out if it appeared in a PR.

### Target shape

```java
// mocapi-o11y — only class left
public final class McpObservationInterceptor implements MethodInterceptor<Object> {

    public static final String HANDLER_KIND_KEY = "mcp.handler.kind";
    public static final String HANDLER_NAME_KEY = "mcp.handler.name";

    private final ObservationRegistry registry;
    private final String observationName;
    private final String handlerKind;
    private final String handlerName;

    public McpObservationInterceptor(
            ObservationRegistry registry, String handlerKind, String handlerName) {
        this.registry = registry;
        this.observationName = "mcp." + handlerKind;
        this.handlerKind = handlerKind;
        this.handlerName = handlerName;
    }

    @Override
    public Object intercept(MethodInvocation<?> invocation) {
        return Observation.createNotStarted(observationName, registry)
                .lowCardinalityKeyValue(HANDLER_KIND_KEY, handlerKind)
                .lowCardinalityKeyValue(HANDLER_NAME_KEY, handlerName)
                .observe(invocation::proceed);
    }
}
```

Tag-key constants stay public so any user writing a custom
`ObservationHandler` can reference the exact strings we publish
rather than re-typing them. (The tag *keys* are the stable contract
— not a typed Context class.)

`Observation.createNotStarted(name, registry)` (no context
supplier) lets Micrometer allocate its default
`Observation.Context` internally. We allocate nothing extra.
Observation name is derived from handler kind (`"mcp." + kind`),
so the interceptor constructor sheds the `observationName`
parameter.

### Autoconfig changes

`MocapiO11yAutoConfiguration` shrinks: the four `*_OBSERVATION_NAME`
constants disappear (derived in the interceptor), and each
customizer lambda passes one fewer argument:

```java
@Bean
CallToolHandlerCustomizer mcpObservationToolCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(
            new McpObservationInterceptor(registry, "tool", config.descriptor().name()));
}

@Bean
GetPromptHandlerCustomizer mcpObservationPromptCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(
            new McpObservationInterceptor(registry, "prompt", config.descriptor().name()));
}

@Bean
ReadResourceHandlerCustomizer mcpObservationResourceCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(
            new McpObservationInterceptor(registry, "resource", config.descriptor().uri()));
}

@Bean
ReadResourceTemplateHandlerCustomizer mcpObservationResourceTemplateCustomizer(
        ObservationRegistry registry) {
    return config -> config.interceptor(
            new McpObservationInterceptor(
                    registry, "resource_template", config.descriptor().uriTemplate()));
}
```

### Impact

- `mocapi-o11y`: 3 classes / ~153 lines → 1 class / ~55 lines.
- `mocapi-o11y-spring-boot-starter`: autoconfig ~83 → ~65 lines.
- Net: ~115 lines deleted, 2 classes deleted.
- Observation names unchanged (`mcp.tool`, `mcp.prompt`,
  `mcp.resource`, `mcp.resource_template`).
- Tag keys unchanged (`mcp.handler.kind`, `mcp.handler.name`).
- Hot-path allocation cost unchanged (Micrometer allocates one
  default `Observation.Context` per call either way; we no longer
  allocate our subclass on top).
- **No user-visible behavior change for the drop-in starter case.**
  Metrics dashboards and tracing spans look identical.

### Breaking changes (for users who reached into the SPI)

- `McpObservationContext` deleted. Users who wrote custom
  `ObservationHandler`s casting to it must now read
  `context.getLowCardinalityKeyValue("mcp.handler.kind")` (and
  `"mcp.handler.name"`) from the default context's key-value list.
  Tag-key constants are exposed on `McpObservationInterceptor` so
  they can reference the same strings without hardcoding.
- `McpObservationConvention` deleted. Users who passed it to
  `@Observed`-annotated methods must inline the equivalent tags on
  their own convention (trivial — it's two `KeyValue` entries).

Neither class has shipped in a public release yet (`mocapi-o11y`
lands first in the next minor), so the "breaking change" caveat is
theoretical — no existing users.

## Acceptance criteria

- [ ] `McpObservationContext.java` deleted from `mocapi-o11y`.
- [ ] `McpObservationConvention.java` deleted from `mocapi-o11y`.
- [ ] `McpObservationInterceptor` constructor has three args only
      (`registry`, `handlerKind`, `handlerName`); `observationName`
      derived as `"mcp." + handlerKind`.
- [ ] `McpObservationInterceptor` exposes `HANDLER_KIND_KEY` and
      `HANDLER_NAME_KEY` as public constants.
- [ ] `McpObservationInterceptor.intercept` uses
      `Observation.createNotStarted(name, registry)` without a
      context supplier.
- [ ] `MocapiO11yAutoConfiguration` loses the four
      `*_OBSERVATION_NAME` constants; each customizer constructs the
      interceptor with three args.
- [ ] Interceptor unit tests updated — no references to
      `McpObservationContext` or `McpObservationConvention`.
- [ ] Autoconfig unit test + meter integration test still pass:
      observations emit with name `mcp.tool` / `mcp.prompt` /
      `mcp.resource` / `mcp.resource_template` and carry the same
      two tags.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Changing observation names or tag keys.** Identical on both
  sides of this change.
- **Introducing a custom `ObservationConvention` for `@Observed`
  users.** They can write their own if they want one; nothing we
  need to ship.
- **Touching `mocapi-logging`.** It's already in the trim shape we
  want (one interceptor + one key-constants class + one autoconfig).

## Implementation notes

- `Observation.createNotStarted(String, ObservationRegistry)` is
  the minimal factory; no context supplier needed. Micrometer
  creates a default `Observation.Context` internally.
- If at some future point we *do* need typed per-call context
  (e.g., to stash `isError=true` for `CallToolResult` and pass it
  to a custom handler), we can reintroduce a Context subclass
  then — surgically, with a concrete use case. Not speculatively.
