# Create mocapi-test module with MockMcpToolContext

## What to build

A new `mocapi-test` Maven module providing `MockMcpToolContext` — a plain
Java implementation of `McpToolContext` that tool authors use in unit tests
to capture and assert what their `@ToolMethod` handler sent to the client.

### Module layout

```
mocapi-test/
  pom.xml
  src/main/java/com/callibrity/mocapi/test/MockMcpToolContext.java
  src/main/java/com/callibrity/mocapi/test/CapturedCall.java
  src/test/java/com/callibrity/mocapi/test/MockMcpToolContextTest.java
```

### pom.xml

- `groupId`: `com.callibrity.mocapi`
- `artifactId`: `mocapi-test`
- Parent: `mocapi-parent`
- Dependencies (compile scope): `mocapi-api`, `mocapi-model`.
- No Spring dependencies.
- Add module to top-level `pom.xml` `<modules>` and to `mocapi-bom`.

### Class: `com.callibrity.mocapi.test.MockMcpToolContext`

Public final class implementing `McpToolContext`.

Captured call records as nested records:

```java
public record ProgressEvent(long progress, long total) {}
public record LogEntry(LoggingLevel level, String logger, String message) {}
public record ElicitCall(ElicitRequestFormParams params) {}
public record SampleCall(CreateMessageRequestParams params) {}
```

Instance state (all `private final`):

```java
private final List<ProgressEvent> progressEvents = new ArrayList<>();
private final List<LogEntry> logEntries = new ArrayList<>();
private final List<ElicitCall> elicitCalls = new ArrayList<>();
private final List<SampleCall> sampleCalls = new ArrayList<>();
private Function<ElicitRequestFormParams, ElicitResult> elicitResponder =
    params -> new ElicitResult(ElicitAction.ACCEPT, null);
private Function<CreateMessageRequestParams, CreateMessageResult> sampleResponder =
    params -> new CreateMessageResult(Role.ASSISTANT, new TextContent("mock-response", null), null, null);
```

Public accessors (return `List.copyOf(...)` for the captured lists):

```java
public List<ProgressEvent> progressEvents()
public List<LogEntry> logEntries()
public List<ElicitCall> elicitCalls()
public List<SampleCall> sampleCalls()
```

Scripting methods:

```java
public MockMcpToolContext elicitResponse(ElicitResult fixed)
public MockMcpToolContext elicitResponse(Function<ElicitRequestFormParams, ElicitResult> fn)
public MockMcpToolContext sampleResponse(CreateMessageResult fixed)
public MockMcpToolContext sampleResponse(Function<CreateMessageRequestParams, CreateMessageResult> fn)
public void reset()
```

`McpToolContext` implementations:

- `sendProgress(long, long)` → append `ProgressEvent`.
- `log(LoggingLevel, String, String)` → append `LogEntry`. (The convenience
  methods `info(...)` / `warn(...)` / etc. land here via the existing
  defaults.)
- `elicit(ElicitRequestFormParams)` → append `ElicitCall`, return
  `elicitResponder.apply(params)`.
- `elicit(String, Consumer<RequestedSchemaBuilder>)` already delegates to
  the above default — no additional override needed.
- `sample(CreateMessageRequestParams)` → append `SampleCall`, return
  `sampleResponder.apply(params)`.
- `sample(String)` already delegates via default — no override needed.
- `sample(Consumer<CreateMessageRequestConfig>)` → construct a throw-on-
  lookup config impl (next bullet), run the customizer, call `build()`,
  pass to `sample(CreateMessageRequestParams)`.

### Helper: `TestCreateMessageRequestConfig`

Package-private class in `com.callibrity.mocapi.test` implementing
`CreateMessageRequestConfig`. Accumulates messages and settings identically
to `CreateMessageRequestBuilder` but:

- `tool(String name)` throws `UnsupportedOperationException("Server tool
  registry unavailable in MockMcpToolContext")`.
- `allServerTools()` throws the same.

Has a package-private `CreateMessageRequestParams build()` method. Not
exposed publicly — only used by `MockMcpToolContext#sample(Consumer)`.

## Acceptance criteria

- [ ] `mocapi-test/pom.xml` exists; module is in top-level `<modules>`
      and in `mocapi-bom` `<dependencyManagement>`.
- [ ] `mvn verify` at the repo root succeeds with the new module.
- [ ] `MockMcpToolContextTest` exercises: each captured-call accessor
      returns exactly what was recorded, in order; `reset()` clears all
      four lists; scripted fixed-response and dynamic `Function`-based
      response both work for elicit and sample; `log(...)` captures calls
      from `info/debug/warn/error/notice/critical/alert/emergency`
      default methods.
- [ ] `sample(Consumer<CreateMessageRequestConfig>)` calling `tool(String)`
      or `allServerTools()` throws `UnsupportedOperationException` with a
      message mentioning `MockMcpToolContext`.
- [ ] One sample in `examples/example-autoconfigure` (or similar) unit-
      tests a real `@ToolMethod` handler against `MockMcpToolContext`.

## Docs

- [ ] `CHANGELOG.md` under `## [Unreleased]`: entry under `### Added`
      describing the new `mocapi-test` module and `MockMcpToolContext`
      with a 5-line usage sketch.
- [ ] README links the test module in the "Modules" section (or
      equivalent).

## Commit

Suggested commit message:

```
Add mocapi-test module with MockMcpToolContext

A ready-to-use McpToolContext implementation for unit testing
@ToolMethod handlers. Captures progress, log, elicit, and sample
calls; supports scripted elicit/sample responses (fixed or per-call
dynamic); ships in a new mocapi-test module with no Spring dependency.
```

## Implementation notes

- Keep the class `final`; test authors subclass only if they have a
  compelling reason, in which case they should just write their own
  implementation of `McpToolContext`.
- The `Function`-based scripted responses replace the fixed ones — each
  setter call overwrites the prior responder.
- Captured-call lists expose `List.copyOf(...)` so test code can assert
  over them with AssertJ without worrying about mutation as the tool
  continues running.
