# mocapi-o11y: one-interceptor observability via Micrometer Observation API

## What to build

Ship `mocapi-o11y` + `mocapi-o11y-spring-boot-starter`: a drop-in
observability layer that wraps every tool / prompt / resource /
resource-template invocation in a Micrometer `Observation`. The same
interceptor produces **both** metrics (via `MeterObservationHandler`)
and distributed-tracing spans (via `TracingObservationHandler`) —
whichever observation handlers the user has on their classpath
participate automatically. No separate metrics or tracing interceptors.

This is the first real client of the handler customizer SPI from spec
180. It attaches per-handler, reads each handler's kind / name off the
Config at build time, and closes over them so the hot path has zero
reflection.

### Why single-interceptor

Methodical's invocation pipeline runs interceptors reflectively on
every call. Two interceptors = double the per-call overhead and two
chances to mismeasure. Micrometer's Observation API already solves
this: one `Observation` emits `onStart` / `onStop` / `onError` events
that every registered `ObservationHandler` sees. `MeterRegistry` ships
a handler that converts events to `Timer` / `Counter`; Micrometer
Tracing ships a handler that converts events to spans. Users enable as
many as they like; we ship one interceptor.

### Module layout

```
mocapi-o11y/                              — pure Java, no Spring
  src/main/java/com/callibrity/mocapi/o11y/
    McpObservationInterceptor.java        — the one interceptor
    McpObservationConvention.java         — low-cardinality keys (mcp.handler.kind, mcp.handler.name)
    McpObservationContext.java            — Observation.Context subclass carrying kind + name + descriptor

mocapi-o11y-spring-boot-starter/          — autoconfig + starter pom
  src/main/java/com/callibrity/mocapi/o11y/autoconfigure/
    MocapiO11yAutoConfiguration.java
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Both modules depend on `mocapi-server` (not on a transport starter —
per spec 179 pattern).

### The interceptor

One class, one job: wrap the `MethodInvocation::proceed` call in an
`Observation`. The observation name, kind tag, and handler-name tag
are closed over at construction time from the Config — no per-call
introspection.

```java
// mocapi-o11y — package com.callibrity.mocapi.o11y
public final class McpObservationInterceptor implements MethodInterceptor<Object> {

    private final ObservationRegistry registry;
    private final String observationName;     // e.g. "mcp.tool"
    private final String handlerKind;         // "tool" / "prompt" / "resource" / "resource_template"
    private final String handlerName;         // tool name / prompt name / resource URI / URI template

    public McpObservationInterceptor(
            ObservationRegistry registry,
            String observationName,
            String handlerKind,
            String handlerName) {
        this.registry = registry;
        this.observationName = observationName;
        this.handlerKind = handlerKind;
        this.handlerName = handlerName;
    }

    @Override
    public Object intercept(MethodInvocation<?> invocation) throws Throwable {
        McpObservationContext context = new McpObservationContext(handlerKind, handlerName);
        Observation observation = Observation.createNotStarted(observationName, () -> context, registry)
                .lowCardinalityKeyValue("mcp.handler.kind", handlerKind)
                .lowCardinalityKeyValue("mcp.handler.name", handlerName);
        return observation.observeChecked(invocation::proceed);
    }
}
```

`observeChecked` (Micrometer's checked-throwable variant) handles
`onStart` / `onStop` / `onError` for us; exceptions propagate after
the observation is closed with `ERROR` status.

### Observation naming

One observation name per handler kind:

| Kind | Observation name |
|---|---|
| tool | `mcp.tool` |
| prompt | `mcp.prompt` |
| resource | `mcp.resource` |
| resource_template | `mcp.resource_template` |

Why four names (not one `mcp.invocation` with a kind tag): meter
registries surface a separate `Timer` / `Counter` per observation
name, and users filtering metrics dashboards by "tool latency" vs
"prompt latency" want distinct meter names, not the same meter
partitioned by tag. Dimensional separation at the meter level matches
how mocapi users think about the four handler kinds.

### Tags (low-cardinality)

- `mcp.handler.kind` = `tool` / `prompt` / `resource` / `resource_template`
- `mcp.handler.name` = the annotation's name / uri / uriTemplate (same
  value `HandlerKinds.nameOf(...)` returns)

Both are low-cardinality by construction: handler names come from a
fixed set defined at startup by the annotations on user beans.

**Not in this spec (explicitly deferred):**

- `success` / `error` outcome tag (Observation already captures this
  via `onError` → tracing span status; meter handlers will surface it
  as an `outcome` tag on the resulting `Timer`). Nothing additional
  for us to add.
- `mcp.session` and `mcp.request` as observation attributes. MDC
  already carries them (spec 178); wiring them into the Observation
  context is a follow-up once the request-id piece of the MDC
  interceptor lands.
- `isError=true` detection for `CallToolResult`. That's a
  CallToolHandler-kind concern only; handle in a follow-up if the
  standard `outcome` tag isn't enough.

### Customizer wiring

Four customizer beans, one per handler kind, each wiring an
`McpObservationInterceptor` closed over that handler's kind and name:

```java
// MocapiO11yAutoConfiguration — abbreviated

@Bean
CallToolHandlerCustomizer mcpObservationToolCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(new McpObservationInterceptor(
            registry, "mcp.tool", "tool", config.descriptor().name()));
}

@Bean
GetPromptHandlerCustomizer mcpObservationPromptCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(new McpObservationInterceptor(
            registry, "mcp.prompt", "prompt", config.descriptor().name()));
}

@Bean
ReadResourceHandlerCustomizer mcpObservationResourceCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(new McpObservationInterceptor(
            registry, "mcp.resource", "resource", config.descriptor().uri()));
}

@Bean
ReadResourceTemplateHandlerCustomizer mcpObservationResourceTemplateCustomizer(ObservationRegistry registry) {
    return config -> config.interceptor(new McpObservationInterceptor(
            registry, "mcp.resource_template", "resource_template", config.descriptor().uriTemplate()));
}
```

Each customizer is keyed on the `ObservationRegistry` bean existing
in the context — if no registry, no observations (see activation
rule below).

### Activation rule

```java
@AutoConfiguration
@ConditionalOnClass(ObservationRegistry.class)
@ConditionalOnBean(ObservationRegistry.class)
public class MocapiO11yAutoConfiguration { ... }
```

`@ConditionalOnClass` guards against the starter being on the
classpath without the Observation API (shouldn't happen — it's a
direct dep of `mocapi-o11y` — but cheap insurance).
`@ConditionalOnBean(ObservationRegistry.class)` is the real gate:
Spring Boot 3+ auto-wires an `ObservationRegistry` when any metrics
/ tracing starter is present, so adding our starter alongside e.g.
`spring-boot-starter-actuator` or
`micrometer-tracing-bridge-otel` just works. Stdio-only apps with no
metrics / tracing stack on the classpath get no registry → our
customizers don't wire → no-op.

### Dependencies

`mocapi-o11y/pom.xml`:

- `com.callibrity.mocapi:mocapi-api` (for the customizer SPI + `HandlerKinds`)
- `io.micrometer:micrometer-observation` (the Observation API —
  core-only; no metrics SDK, no tracing SDK pulled in)
- `org.jwcarman.methodical:methodical-api` (transitive via
  `mocapi-api`, already present)

`mocapi-o11y-spring-boot-starter/pom.xml`:

- `com.callibrity.mocapi:mocapi-o11y`
- `com.callibrity.mocapi:mocapi-server` (per spec 179 pattern — not
  a transport starter)
- `org.springframework.boot:spring-boot-autoconfigure`

No `spring-boot-starter-actuator`, no `micrometer-core`, no tracing
bridge — users bring their own SDK. Starter stays transport-agnostic
and observability-SDK-agnostic.

### Example wiring (user's pom)

```xml
<!-- Metrics only (Prometheus scrape via actuator) -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-o11y-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

```xml
<!-- Tracing only (OTLP) -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-o11y-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

```xml
<!-- Both -->
<!-- same o11y starter + both stacks above -->
```

### Interaction with user `@Observed`

Users can skip our starter and put `@Observed` on their own tool
methods instead — Spring Boot's built-in `@Observed` AOP advice will
wrap the method, producing the same Observation stream. Our
interceptor path and Spring's AOP path don't fight: mocapi's
`MethodInvoker` calls `Method.invoke(proxyBean, args)`, so the CGLIB
proxy's advice still fires on virtual dispatch. Users who want
per-method custom observation names / tags beyond `mcp.tool.name` are
better served by `@Observed` directly; users who want
"every handler, no annotation noise" use our starter.

Worth noting: if a user enables **both** our starter and `@Observed`
on the same method, they'll get two nested observations (outer from
our interceptor, inner from Spring AOP). Document this clearly in the
starter README — it's not a bug, it's composition — but recommend
picking one.

## Acceptance criteria

- [ ] New module `mocapi-o11y` under root `pom.xml` modules list.
- [ ] New module `mocapi-o11y-spring-boot-starter` under root modules
      list.
- [ ] `mocapi-o11y` contains `McpObservationInterceptor`,
      `McpObservationConvention`, `McpObservationContext`.
- [ ] `McpObservationInterceptor` closes over kind + name at
      construction time; its `intercept` method does zero reflection
      and zero map lookups beyond what Micrometer does internally.
- [ ] `MocapiO11yAutoConfiguration` registers four customizer beans
      (one per handler kind) guarded by
      `@ConditionalOnBean(ObservationRegistry.class)`.
- [ ] Each customizer reads the descriptor name / uri / uriTemplate
      off the Config and bakes it into the interceptor.
- [ ] Observation names: `mcp.tool`, `mcp.prompt`, `mcp.resource`,
      `mcp.resource_template`.
- [ ] Low-cardinality tags: `mcp.handler.kind`, `mcp.handler.name`.
- [ ] `mocapi-o11y` depends on `micrometer-observation` only (no
      metrics SDK, no tracing SDK).
- [ ] `mocapi-o11y-spring-boot-starter` depends on `mocapi-server`
      (not a transport starter).
- [ ] `mocapi-bom` gains entries for both new modules.
- [ ] Unit tests: feed `McpObservationInterceptor` an
      `ObservationRegistry` with a `TestObservationRegistry` / stub
      handler, invoke through a Methodical `MethodInvocation` stub,
      assert start + stop events and the expected low-cardinality
      tags. One test per kind.
- [ ] Autoconfig unit test: `ApplicationContextRunner` with +/-
      `ObservationRegistry` bean, assert the four customizer beans
      only appear when the registry is present.
- [ ] Integration test (either in `mocapi-o11y-spring-boot-starter`
      `src/test` or a new example under `examples/`): boot a Spring
      context with `mocapi-o11y-spring-boot-starter` +
      `spring-boot-starter-actuator` + `SimpleMeterRegistry`, call a
      tool, assert a `Timer` with name `mcp.tool` and tags
      `mcp.handler.kind=tool`, `mcp.handler.name=<toolName>` shows
      up in the registry.
- [ ] `README.md` "Modules" section lists both new modules with
      one-line descriptions.
- [ ] `docs/observability-roadmap.md` updated: move "Metrics
      (Micrometer)" and "Tracing (OpenTelemetry)" into the shipped
      section, noting that one starter handles both via the
      Observation API.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Added`: entry for
      `mocapi-o11y` and `mocapi-o11y-spring-boot-starter`.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Per-handler metadata contributing extra tags** (e.g. plugins
  declaring "tag this tool's invocations with `tenant=X`"). Defer —
  `@Observed` with a custom `ObservationConvention` bean already
  covers the sophisticated case; our starter stays simple.
- **Session / request attributes on the Observation.** Deferred
  pending spec-178's MDC request-id follow-up (we want ScopedValue
  propagation for request id first).
- **`mcp.tool.is_error` attribute on `CallToolResult.isError=true`.**
  Standard Micrometer `outcome` tag covers the common case; a
  follow-up can add this if it turns out to matter for dashboards.
- **Bundled example consuming the starter.** The integration test
  covers the wiring; a dedicated example can land later if users
  ask.

## Implementation notes

- `micrometer-observation` is a tiny (~50KB) dependency. Users on
  stdio with no observability needs can still include the starter
  without pulling in a metrics or tracing SDK — the Observation
  registry is a no-op until a handler registers.
- `ObservationRegistry` is auto-created by Spring Boot 3+ whenever
  `spring-boot-starter-actuator` or any Micrometer Observation
  autoconfiguration is on the classpath. On mocapi's user apps that
  means: include actuator, our starter lights up.
- The customizer runs once per handler at startup; the closed-over
  kind / name strings are final. Hot-path cost per invocation =
  `Observation.createNotStarted(...)` + `lowCardinalityKeyValue(...)` ×
  2 + `observeChecked(...)`. Everything else is handler plugins
  (MeterObservationHandler writing to a `Timer`,
  TracingObservationHandler starting a span). No reflection.
- Follow-up spec 182 — `mocapi-actuator-spring-boot-starter`
  (`/actuator/mcp` endpoint) — is independent of this work but
  complements it: actuator gives you the "what handlers exist"
  snapshot, o11y gives you the "how are they doing" stream.
