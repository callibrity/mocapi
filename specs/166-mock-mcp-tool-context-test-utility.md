# MockMcpToolContext test utility for unit-testing tool methods

## What to build

A ready-to-use `McpToolContext` implementation that tool authors use in unit
tests to assert what their tool method sent to the client — progress updates,
log notifications, elicitation and sampling requests — without standing up a
transport, session service, or Spring context.

### New module: `mocapi-test`

Pure test-scope library, no Spring dependency, depends only on `mocapi-api`
and `mocapi-model`. Published alongside the rest of the modules.

### MockMcpToolContext

```java
var ctx = new MockMcpToolContext();

// ... invoke the tool under test with ctx ...
MyToolResult result = ScopedValue.where(McpToolContext.CURRENT, ctx)
    .call(() -> myTool.invoke(args));

// Assert interactions
assertThat(ctx.progressEvents()).hasSize(3);
assertThat(ctx.logEntries()).anyMatch(e ->
    e.level() == LoggingLevel.INFO && e.message().contains("done"));
assertThat(ctx.elicitCalls()).singleElement()
    .satisfies(call -> assertThat(call.message()).isEqualTo("Enter details"));
assertThat(ctx.sampleCalls()).singleElement()
    .satisfies(call -> assertThat(call.maxTokens()).isEqualTo(500));
```

### API

`MockMcpToolContext implements McpToolContext` with:

- **`List<ProgressEvent> progressEvents()`** — every `sendProgress(p, t)` call,
  captured as `record ProgressEvent(long progress, long total)`.
- **`List<LogEntry> logEntries()`** — every `log(level, logger, message)` call
  (including via `info(...)` / `debug(...)` / etc.), captured as
  `record LogEntry(LoggingLevel level, String logger, String message)`.
- **`List<ElicitRequestFormParams> elicitCalls()`** — every `elicit(...)` call
  the tool made.
- **`List<CreateMessageRequestParams> sampleCalls()`** — every `sample(...)`
  call.
- **Scripted responses:**
    - `elicitResponse(ElicitResult fixed)` — return this result for all elicits.
    - `elicitResponse(Function<ElicitRequestFormParams, ElicitResult> fn)` —
      per-call dynamic response.
    - `sampleResponse(CreateMessageResult fixed)` — return this for all samples.
    - `sampleResponse(Function<CreateMessageRequestParams, CreateMessageResult>
      fn)` — per-call dynamic response.
- **Defaults:** if no scripted response is configured, `elicit` returns a
  canned `ElicitAction.ACCEPT` with empty content; `sample` returns a canned
  `CreateMessageResult(Role.ASSISTANT, TextContent("mock-response", null), null, null)`.
  Makes no-op happy paths easy; tests that care configure a response.
- **`reset()`** — clear all captured calls and scripted responses.

`sample(Consumer<CreateMessageRequestConfig>)` resolves the customizer against
a builder that throws on `tool(String)` / `allServerTools()` (no server
registry available in tests) — documented in the javadoc, with a scripted hook
for advanced cases.

### Why this lives in its own module

- Keeps `mocapi-api` free of test-only code.
- Users pull it in with `<scope>test</scope>` alongside their existing
  `spring-boot-starter-test`.
- Doesn't force an AssertJ or Mockito dependency — plain Java accessors.

## Acceptance criteria

- [ ] New Maven module `mocapi-test` with `<packaging>jar</packaging>`, no
      Spring dependency, test-scope-only transitive intent documented.
- [ ] `MockMcpToolContext` implements every `McpToolContext` method.
- [ ] All captured-call accessors return `List` copies (mutating the list
      doesn't affect subsequent captures).
- [ ] Unit tests inside `mocapi-test/src/test/java` exercise every captured-
      call accessor and every scripted-response variant.
- [ ] A new test in `mocapi-server` or `examples/example-autoconfigure`
      demonstrates a real tool method being unit-tested against
      `MockMcpToolContext` — proves the integration.
- [ ] Module added to `mocapi-bom` so downstream users get version management.
- [ ] README entry explaining the test utility with a usage example.

## Implementation notes

- Follow the existing capturing-context pattern from
  `McpToolContextDefaultMethodsTest.CapturingContext` — this spec formalizes
  and ships it.
- `sample(Consumer<CreateMessageRequestConfig>)` needs a builder that works
  without `McpToolsService`. Easiest: an in-`mocapi-test` implementation of
  the `CreateMessageRequestConfig` interface that mirrors the server one but
  throws from `tool(String)` / `allServerTools()` with a clear message
  ("Server tool registry unavailable in tests").
