# mocapi-actuator starter + metrics integration in /actuator/mcp

## What to build

Two additions on top of spec 173's `/actuator/mcp` endpoint:

1. The conventional `mocapi-actuator-spring-boot-starter` pom-aggregator
   module.
2. An optional `metrics` section in the endpoint response when both
   `MeterRegistry` and the observer from spec 171
   (`MicrometerMcpHandlerObserver`) are present.

### Module: `mocapi-actuator-spring-boot-starter`

Layout:

```
mocapi-actuator-spring-boot-starter/
  pom.xml  (no src/)
```

- `artifactId`: `mocapi-actuator-spring-boot-starter`
- Dependencies (compile):
    - `com.callibrity.mocapi:mocapi-actuator`
    - `com.callibrity.mocapi:mocapi-streamable-http-spring-boot-starter`
- Added to parent `<modules>` and `mocapi-bom`.

### Metrics section in the endpoint

Extend `EndpointSnapshot` with an optional `metrics` field:

```java
public record EndpointSnapshot(
    ServerInfo server,
    Map<String, Integer> handlers,
    Map<String, Integer> sessions,
    String uptime,
    Metrics metrics    // null if MeterRegistry is absent
) {}

public record Metrics(
    Map<String, HandlerMetrics> tools,
    Map<String, HandlerMetrics> prompts,
    Map<String, HandlerMetrics> resources,
    Map<String, HandlerMetrics> resourceTemplates
) {}

public record HandlerMetrics(
    Map<String, Long> invocations,   // outcome → count
    String p50,                       // ISO-8601 Duration
    String p95,
    int active
) {}
```

Jackson serializes `null` fields as omitted (per the mocapi-wide
`@JsonInclude(NON_NULL)` setup).

### Wiring

New file `mocapi-actuator/src/main/java/com/callibrity/mocapi/actuator/MetricsSnapshot.java`:

A helper with a single static method:

```java
static Metrics read(MeterRegistry registry) {
  // Group all mcp.* meters by handler kind and name; return the Metrics DTO.
}
```

The `McpEndpoint` constructor gains an optional `ObjectProvider<MeterRegistry>`
parameter; if the provider has a bean, `MetricsSnapshot.read(...)` is
called and attached to the snapshot. Otherwise `metrics` is `null`.

### Tests

Add to `McpEndpointTest`:

- With no `MeterRegistry` bean: `metrics` is `null` / absent from JSON.
- With a `SimpleMeterRegistry` containing one tool invocation record:
  `metrics.tools.<tool-name>.invocations.success == 1`,
  `metrics.tools.<tool-name>.p50` is ISO-8601.

No new module-level tests in the starter (pom-only module).

## Acceptance criteria

- [ ] `mocapi-actuator-spring-boot-starter` exists, pom-only.
- [ ] `EndpointSnapshot` has an optional `Metrics metrics` field.
- [ ] When `MeterRegistry` is absent the field is omitted.
- [ ] When present, the metrics group under tools/prompts/resources/
      resourceTemplates by name with invocation counts, p50/p95
      durations, and active gauge.
- [ ] Golden JSON fixture updated to cover both variants.
- [ ] `mvn verify` green.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: two entries — one
      for the starter module, one for the metrics section in the
      endpoint.
- [ ] `docs/actuator.md` updated with the metrics section details.

## Commit

Suggested commit message:

```
Wire metrics into /actuator/mcp + add mocapi-actuator starter

The endpoint now includes a `metrics` section when a MeterRegistry
bean is present, summarizing invocation counts, p50/p95 durations, and
active-handler gauges per tool / prompt / resource / resource template.
Field is omitted when no registry is configured. Also ships the
conventional mocapi-actuator-spring-boot-starter pom aggregator.
```

## Implementation notes

- Order dependency: this spec depends on 171 (metrics) and 173 (actuator
  core). Ralph should tackle them in order.
- Using `ObjectProvider<MeterRegistry>` in the endpoint constructor
  keeps the actuator module from forcing a Micrometer dependency on
  users who don't want metrics.
