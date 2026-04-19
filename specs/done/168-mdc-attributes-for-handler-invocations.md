# MDC attributes around tool / prompt / resource invocations

## What to build

Set SLF4J MDC attributes for the duration of every handler invocation so
all log lines emitted *during* that invocation — including those from
user-written tool code — carry correlation keys automatically.

### MDC keys

Written as `private static final String` constants in a new file
`mocapi-server/src/main/java/com/callibrity/mocapi/server/observability/McpMdcKeys.java`:

```java
public final class McpMdcKeys {
  public static final String SESSION = "mcp.session";
  public static final String REQUEST = "mcp.request";
  public static final String HANDLER_KIND = "mcp.handler.kind";
  public static final String HANDLER_NAME = "mcp.handler.name";

  private McpMdcKeys() {}
}
```

### Handler-kind values

Literal strings — enumerate in the same class as `public static final
String` constants:

```java
public static final String KIND_TOOL = "tool";
public static final String KIND_PROMPT = "prompt";
public static final String KIND_RESOURCE = "resource";
public static final String KIND_RESOURCE_TEMPLATE = "resource-template";
```

### File: `mocapi-server/.../tools/McpToolsService.java`

In `invokeTool(String, McpTool, JsonNode, CallToolRequestParams)`, wrap
the `ScopedValue.where(...).call(...)` block in a try-with-resources that
sets and clears the four MDC attributes. Sketch:

```java
try (var ignored = pushMdc(
    KIND_TOOL, name,
    params.meta() != null ? String.valueOf(params.meta().requestId()) : null)) {
  Object result = ScopedValue.where(McpToolContext.CURRENT, ctx)
      .call(() -> tool.call(args));
  return toCallToolResult(result);
} catch (Exception e) {
  // existing handling
}
```

Where `pushMdc(...)` is a private static helper returning a
`Closeable` that `MDC.put`s the four keys on entry and
`MDC.remove`s them on close. Session id comes from
`McpSession.CURRENT.isBound() ? McpSession.CURRENT.get().id() : null`.
Request id comes from the JSON-RPC request id when available — extract
from a `ScopedValue` if one exists (check how ripcurl exposes it), or
leave blank for this iteration if not easily accessible.

### Files: `McpPromptsService.java`, `McpResourcesService.java`,
`McpResourceTemplatesService.java`

Mirror the same `pushMdc(...)` wrapping in each dispatch method with the
appropriate `KIND_*` constant. Extract the MDC helper into a shared
location (e.g. `McpMdcScope.java` in the same `observability` package) so
it's not duplicated four times.

### File: `mocapi-server/.../observability/McpMdcScope.java`

Package-private utility:

```java
public final class McpMdcScope implements AutoCloseable {
  public static McpMdcScope push(String handlerKind, String handlerName, String requestId) { ... }

  private final List<String> addedKeys = new ArrayList<>();

  @Override public void close() {
    for (String key : addedKeys) MDC.remove(key);
  }
}
```

`push(...)` reads `McpSession.CURRENT` for the session id, pushes each
non-null key into MDC, and records which keys it added so `close()`
removes only what it added (doesn't touch pre-existing MDC state from
upstream filters).

### Tests

`mocapi-server/src/test/java/com/callibrity/mocapi/server/observability/McpMdcScopeTest.java`:

- `push(...)` sets every MDC key it's given non-null values for.
- `close()` removes the keys it added but leaves pre-existing MDC entries
  alone.
- Null `requestId` → `mcp.request` is not set.
- Session not bound → `mcp.session` is not set.

Integration test `McpToolsServiceMdcTest.java`:

- Invokes a trivial tool whose body asserts that `MDC.get("mcp.tool")`
  equals the tool's name *during* invocation, and
  `MDC.get("mcp.session")` is present when a session is bound.
- Asserts MDC is empty after the invocation returns.
- Asserts MDC is also empty after the invocation throws (failure path).

## Acceptance criteria

- [ ] `McpMdcKeys` class with the six `public static final String`
      constants.
- [ ] `McpMdcScope` utility in the `observability` package.
- [ ] `McpToolsService#invokeTool`, `McpPromptsService#getPrompt`,
      `McpResourcesService#readResource`, and the resource-template
      counterpart each wrap their dispatch in `McpMdcScope.push(...)`.
- [ ] `McpMdcScope` never leaks MDC entries (asserted by integration test
      using a fresh `MDC.getCopyOfContextMap()` before/after).
- [ ] `mvn verify` green.
- [ ] Unit tests for `McpMdcScope` pass; integration test in
      `mocapi-server` verifying MDC during tool invocation passes.

## Docs

- [ ] `CHANGELOG.md` under `## [Unreleased]`: entry under `### Added`
      listing the four MDC keys (`mcp.session`, `mcp.request`,
      `mcp.handler.kind`, `mcp.handler.name`) and noting that they're
      scoped to the invocation and cleared on exit.
- [ ] `docs/logging.md` (or equivalent) section added covering MDC and
      the virtual-threads propagation caveat.

## Commit

Suggested commit message:

```
Set mcp.session / mcp.tool / mcp.handler.kind MDC during handler dispatch

Tool, prompt, and resource invocations now wrap their dispatch in an
McpMdcScope that pushes correlation keys into SLF4J MDC and removes
them on exit. Any log lines emitted during the invocation — including
from user tool code — carry the keys automatically, so operators can
grep logs by session or tool without the handler having to plumb
context into every log call.
```

## Implementation notes

- MDC is backed by SLF4J; no external dependency change needed. Logback
  is already on the classpath transitively via Spring Boot.
- On virtual threads, MDC is per-carrier-thread but read at log time — so
  setting MDC on the thread that runs the tool handler is sufficient.
  Tools that spawn their own executor are responsible for propagating
  MDC themselves; document this in the `McpLogger` / MDC section of the
  docs.
- `McpSession.CURRENT.get().id()` — verify the exact accessor name on
  `McpSession` before coding; adjust if it's `sessionId()` or similar.
- Request id extraction: if ripcurl / `McpToolContext` doesn't surface
  the current JSON-RPC request id easily, leave the MDC key unset for
  this iteration. A follow-up spec can add it once the plumbing is
  clear.
