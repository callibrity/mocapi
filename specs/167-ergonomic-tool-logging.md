# Ergonomic tool logging: SLF4J-style logger + session/tool MDC

## What to build

Tool authors today call `ctx.info("my-logger", "message")` and friends — every
log line needs the logger name restated, and there's no way to use modern SLF4J
idioms (parameterized messages, isEnabled checks). This spec adds an SLF4J-
shaped wrapper and automatic MDC attributes so logs are both easier to write
and easier to correlate.

### `McpLogger` interface

Add `McpLogger` in `mocapi-api`:

```java
public interface McpLogger {
  boolean isDebugEnabled();
  boolean isInfoEnabled();
  boolean isWarnEnabled();
  boolean isErrorEnabled();

  void debug(String message);
  void debug(String format, Object... args);   // SLF4J-style {} placeholders

  void info(String message);
  void info(String format, Object... args);

  void notice(String message);
  void notice(String format, Object... args);

  void warn(String message);
  void warn(String format, Object... args);

  void error(String message);
  void error(String format, Object... args);

  void critical(String message);
  void critical(String format, Object... args);

  void alert(String message);
  void alert(String format, Object... args);

  void emergency(String message);
  void emergency(String format, Object... args);
}
```

All methods route to the session's current log level — messages below the
session's threshold are dropped before formatting (hence `isEnabled` checks
for expensive argument construction).

Format placeholders follow SLF4J convention: `"user {} ran tool {}", userId,
toolName`.

### `McpToolContext.logger(String name)`

Adds a factory method:

```java
McpLogger logger(String name);
```

Default implementation wraps the existing `log(level, logger, message)`
method — so every `McpToolContext` implementation gets the logger for free.

Caller usage:

```java
@ToolMethod(name = "blast-radius")
public BlastRadiusResult blastRadius(String service, McpToolContext ctx) {
  var log = ctx.logger("catalog.blast-radius");
  log.info("Computing blast radius for {}", service);
  // ...
}
```

### Automatic MDC attributes

Whenever a tool method runs inside `DefaultMcpToolContext`, the following MDC
keys are set for the duration of the invocation:

- `mcp.session` — the MCP session id (when available)
- `mcp.tool` — the tool name being invoked
- `mcp.request` — the JSON-RPC request id (when available)

Entered via `MDC.putCloseable(...)` in a try-with-resources in
`McpToolsService.invokeTool`, cleared automatically on exit. Works correctly
under virtual threads since Logback/Log4j2 read MDC at log time on the
current thread (and mocapi sets them on the thread that runs the tool).

### What does *not* change

- `ctx.info(logger, message)` and friends remain — additive change, no
  breakage.
- `log(level, logger, message)` stays as the single abstract method.
- Nothing moves across module boundaries.

## Acceptance criteria

- [ ] `McpLogger` interface added in `com.callibrity.mocapi.api.tools`.
- [ ] `McpToolContext.logger(String name)` is a `default` method wiring to
      the existing `log(...)` abstract.
- [ ] Parameterized-format support uses SLF4J's `MessageFormatter`
      (already transitively available via Logback) — not a hand-rolled
      parser.
- [ ] Messages below the session's log level are dropped before
      `MessageFormatter` runs (assert via test that `expensiveCall()` in
      `log.debug("{}", expensiveCall())` is not invoked when DEBUG is off).
- [ ] `DefaultMcpToolContext` (or `McpToolsService.invokeTool`) sets
      `mcp.session`, `mcp.tool`, `mcp.request` MDC entries around the tool
      invocation and clears them on exit (including exception paths).
- [ ] Unit tests in `mocapi-api` assert every `McpLogger` level routes to
      the correct `LoggingLevel` and respects session threshold.
- [ ] Integration test in `mocapi-server` asserts MDC attributes are present
      during a tool invocation and absent before / after.
- [ ] Docs updated: README + `docs/tools.md` (or equivalent) show the new
      pattern as the recommended one.

## Implementation notes

- SLF4J's `org.slf4j.helpers.MessageFormatter` does the `{}` substitution
  and is public API — reuse it; don't implement a formatter by hand.
- MDC propagation across virtual-thread hops is *not* in scope — if a tool
  spawns its own executor, the tool author is responsible for copying
  `MDC.getCopyOfContextMap()` themselves. Document this limitation.
- Keep `ctx.info("logger", "msg")` style methods — do not deprecate them.
  The new API is additive.
