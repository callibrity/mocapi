# Auto-metrics on tool / prompt / resource invocations

## What to build

Zero-config Micrometer meters around every `tools/call`, `prompts/get`, and
`resources/read` dispatch. Users who have a `MeterRegistry` bean (typically
via `spring-boot-starter-actuator` + a Micrometer registry starter) get
production-quality per-handler metrics for free.

### Two new modules, per project convention

- **`mocapi-metrics`** — the code. Depends on `mocapi-api` (for the observer
  SPI) and `micrometer-core`. Registers no Spring beans itself — plain
  library exposing observer implementations (`MicrometerToolObserver`,
  `MicrometerPromptObserver`, `MicrometerResourceObserver`) that take a
  `MeterRegistry` in their constructor.
- **`mocapi-metrics-spring-boot-starter`** — dependency aggregator, no
  code. Depends on `mocapi-metrics`, `mocapi-streamable-http-spring-boot-
  starter`, and carries an `AutoConfiguration.imports` pointing at an
  autoconfig class in `mocapi-metrics` that wires the observers as Spring
  beans, conditional on `MeterRegistry` presence.

Users wanting metrics add the starter. Users building without Spring can
depend on `mocapi-metrics` directly.

### Meters

Three instrument triples, one per handler type:

| Meter name                  | Type   | Tags                                   |
|-----------------------------|--------|----------------------------------------|
| `mcp.tool.invocations`      | Counter| `tool`, `outcome` (`success`/`error`)  |
| `mcp.tool.duration`         | Timer  | `tool`, `outcome`                      |
| `mcp.prompt.invocations`    | Counter| `prompt`, `outcome`                    |
| `mcp.prompt.duration`       | Timer  | `prompt`, `outcome`                    |
| `mcp.resource.reads`        | Counter| `resource`, `outcome`                  |
| `mcp.resource.duration`     | Timer  | `resource`, `outcome`                  |

**Outcome tagging:**

- `success` — handler returned normally, including tool handlers that
  returned `CallToolResult.isError=true` (a model-visible error is still a
  protocol-successful call — the tool did its job).
- `error` — handler threw. This covers `ConstraintViolationException` (which
  also produces `isError=true` but via exception path today — bucket it here
  since the failure was outside the handler's control).

Two outcomes is enough; resist the temptation to add `validation`,
`timeout`, etc. dimensions — each tag multiplies the time series count.

### Concurrent invocation gauge

One additional gauge:

| Meter                          | Type  | Tags   |
|--------------------------------|-------|--------|
| `mcp.tool.active`              | Gauge | `tool` |
| `mcp.prompt.active`            | Gauge | `prompt`|
| `mcp.resource.active`          | Gauge | `resource`|

Incremented on entry, decremented on exit (via try-finally). Useful for
spotting stuck invocations.

### Where the instrumentation hooks in

- Tools: `McpToolsService.invokeTool` — wrap the `tool.call(args)` block.
- Prompts: `McpPromptsService.getPrompt` — wrap the `prompt.get(args)` block.
- Resources: `McpResourcesService.readResource` — wrap the `resource.read()`
  block.
- Resource templates: same meter family as resources but tag the template
  name.

Implementation via `MeterRegistry`-injected helper class in the starter —
keep the server module itself free of a hard Micrometer dependency.

### Unauthenticated invocations

When OAuth2 is enabled and a request lacks auth, the invocation never
reaches the handler — the security filter rejects it upstream. Those don't
count. Meters only fire for actual dispatched invocations.

## Acceptance criteria

- [ ] Two new Maven modules: `mocapi-metrics` (code, Spring-free library)
      and `mocapi-metrics-spring-boot-starter` (no-code aggregator that
      pulls in the core module + registers the autoconfig).
- [ ] Autoconfig (in `mocapi-metrics`) conditional on `MeterRegistry.class`
      and `MeterRegistry` bean; registers meter-binder beans for tools,
      prompts, resources.
- [ ] Hooks integrate without adding a Micrometer dependency to
      `mocapi-server` — use an SPI interface in mocapi-api
      (`McpHandlerObserver`) that `mocapi-metrics` implements.
- [ ] Tag cardinality is fixed and documented — no user-supplied content
      ever becomes a tag value.
- [ ] Tests: unit tests for each observer impl using
      `SimpleMeterRegistry`; integration test in the starter module boots
      a Spring context with a `SimpleMeterRegistry` and a trivial tool,
      invokes it, and asserts the expected meters fired with expected tags
      and counts.
- [ ] README doc explaining the meter names, tag semantics, and how to wire
      to Prometheus / OTLP / CloudWatch.
- [ ] Both modules added to `mocapi-bom`.

## Implementation notes

- Define the observer SPI in `mocapi-api` (since mocapi-server runs the
  dispatch and calls the hooks). Example:
  ```java
  public interface McpHandlerObserver {
    AutoCloseable onInvocation(HandlerKind kind, String name);
  }
  ```
  `McpToolsService.invokeTool` calls it via try-with-resources so
  start/stop are automatic.
- No-op default if no observers are registered — zero cost when nobody
  cares about metrics.
- `outcome="success"` should include `isError=true` results. Reason: an
  LLM-visible error is a *protocol-successful* handler invocation. A
  separate `isError` tag is tempting but inflates cardinality; a future
  spec can add a standalone `mcp.tool.isError` counter if operators want
  that signal separately.
