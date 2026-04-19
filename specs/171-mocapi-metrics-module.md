# mocapi-metrics module: Micrometer McpHandlerObserver

## What to build

A plain Java library module providing a `McpHandlerObserver` implementation
backed by Micrometer. Depends on spec 170 (observer SPI) being in place.

### Module layout

```
mocapi-metrics/
  pom.xml
  src/main/java/com/callibrity/mocapi/metrics/MicrometerMcpHandlerObserver.java
  src/main/java/com/callibrity/mocapi/metrics/MocapiMetricsAutoConfiguration.java
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/com/callibrity/mocapi/metrics/MicrometerMcpHandlerObserverTest.java
```

### pom.xml

- `artifactId`: `mocapi-metrics`
- Dependencies: `mocapi-api` (compile), `io.micrometer:micrometer-core`
  (compile), `org.springframework.boot:spring-boot-autoconfigure`
  (optional — needed for the autoconfig class but users can depend on
  the library without Spring).
- Add to top-level `<modules>` and `mocapi-bom`.

### Class: `com.callibrity.mocapi.metrics.MicrometerMcpHandlerObserver`

```java
public final class MicrometerMcpHandlerObserver implements McpHandlerObserver {
  private final MeterRegistry registry;

  public MicrometerMcpHandlerObserver(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public HandlerScope onInvocation(HandlerKind kind, String name) {
    Tags baseTags = Tags.of(tagKeyFor(kind), name);
    registry.counter(meterBase(kind) + ".invocations",
        baseTags.and("outcome", "pending")); // overwritten on close
    registry.gauge(meterBase(kind) + ".active", baseTags, ...); // inc on enter, dec on close
    var sample = Timer.start(registry);
    return new HandlerScope() {
      String outcome = "success";
      @Override public void onSuccess() { outcome = "success"; }
      @Override public void onError(Throwable t) { outcome = "error"; }
      @Override public void close() {
        sample.stop(registry.timer(meterBase(kind) + ".duration", baseTags.and("outcome", outcome)));
        registry.counter(meterBase(kind) + ".invocations", baseTags.and("outcome", outcome)).increment();
        // decrement the active gauge
      }
    };
  }
}
```

(The above is sketch. The real implementation should use `MultiGauge`
or an `AtomicLong` behind the `active` gauge to track concurrent
in-flight invocations per handler.)

**Meters** (final list — match these exactly):

| Meter                          | Type   | Tags                                   |
|--------------------------------|--------|----------------------------------------|
| `mcp.tool.invocations`         | Counter| `tool`, `outcome` (`success`/`error`)  |
| `mcp.tool.duration`            | Timer  | `tool`, `outcome`                      |
| `mcp.tool.active`              | Gauge  | `tool`                                 |
| `mcp.prompt.invocations`       | Counter| `prompt`, `outcome`                    |
| `mcp.prompt.duration`          | Timer  | `prompt`, `outcome`                    |
| `mcp.prompt.active`            | Gauge  | `prompt`                               |
| `mcp.resource.invocations`     | Counter| `resource`, `outcome`                  |
| `mcp.resource.duration`        | Timer  | `resource`, `outcome`                  |
| `mcp.resource.active`          | Gauge  | `resource`                             |

`HandlerKind.RESOURCE_TEMPLATE` uses the same `mcp.resource.*` meters —
tag value is the template name; keeps cardinality tight.

**Outcome rules:**

- `onSuccess()` → `outcome=success`. This includes tool results with
  `isError=true` (a model-visible error is still a protocol-successful
  invocation).
- `onError(Throwable)` → `outcome=error`. Covers
  `ConstraintViolationException` and any unhandled exception.

### Class: `com.callibrity.mocapi.metrics.MocapiMetricsAutoConfiguration`

```java
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
class MocapiMetricsAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  MicrometerMcpHandlerObserver micrometerMcpHandlerObserver(MeterRegistry registry) {
    return new MicrometerMcpHandlerObserver(registry);
  }
}
```

Register via `META-INF/spring/.../AutoConfiguration.imports`.

### Tests

`MicrometerMcpHandlerObserverTest`:

- Boot a `SimpleMeterRegistry`. Construct the observer. Call
  `onInvocation(TOOL, "hello") → onSuccess() → close()`. Assert
  `mcp.tool.invocations{tool=hello,outcome=success}` count is 1 and
  `mcp.tool.duration` recorded one sample.
- Same but `onError(new RuntimeException())` → assert
  `outcome=error`.
- Two concurrent `onInvocation(TOOL, "x")` → `mcp.tool.active{tool=x}`
  reads 2 before either closes, drops to 0 after both close.
- `HandlerKind.RESOURCE_TEMPLATE` lands under `mcp.resource.*` meters
  with `resource` tag = template name.

## Acceptance criteria

- [ ] `mocapi-metrics` module exists with the layout above.
- [ ] Meter names and tag keys match the table exactly.
- [ ] `outcome=success` covers tool results with `isError=true`.
- [ ] Autoconfig registers the observer bean only when `MeterRegistry`
      is present.
- [ ] No hard dependency on a specific Micrometer registry module
      (Prometheus / OTLP / etc.) — the observer works with any registry
      the user provides.
- [ ] All unit tests pass; `mvn verify` green.
- [ ] Module in `mocapi-bom`.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: new module
      `mocapi-metrics` with the meter names and tag semantics listed.
- [ ] New `docs/metrics.md` documenting the meters, tag cardinality
      notes, and wiring snippets for Prometheus / OTLP / CloudWatch.

## Commit

Suggested commit message:

```
Add mocapi-metrics: Micrometer observer for handler invocations

New module registering a MicrometerMcpHandlerObserver that emits
invocation counters, duration timers, and active-handler gauges for
every tool / prompt / resource dispatch. Zero-config when a
MeterRegistry bean is present; otherwise the module is inert.
```
