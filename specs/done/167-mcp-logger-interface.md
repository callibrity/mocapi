# McpLogger interface with SLF4J-style parameterized messages (breaking)

## What to build

Replace the 8 per-level convenience methods on `McpToolContext`
(`info`, `debug`, `warn`, `notice`, `error`, `critical`, `alert`,
`emergency`) with a single `logger(String name)` factory that returns an
SLF4J-shaped `McpLogger`. Tool authors switch from:

```java
ctx.info("catalog.blast-radius", "Computing blast radius for " + service);
```

to:

```java
var log = ctx.logger("catalog.blast-radius");
log.info("Computing blast radius for {}", service);
```

This is a **breaking change** to `McpToolContext` — the 0.11.x line
accepts the churn for a much cleaner API.

### File: `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/McpLogger.java`

Public interface. Methods for every MCP `LoggingLevel` value, each with a
plain-string and parameterized-format variant:

```java
public interface McpLogger {
  boolean isDebugEnabled();
  boolean isInfoEnabled();
  boolean isNoticeEnabled();
  boolean isWarnEnabled();
  boolean isErrorEnabled();
  boolean isCriticalEnabled();
  boolean isAlertEnabled();
  boolean isEmergencyEnabled();

  void debug(String message);
  void debug(String format, Object... args);
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

### File: `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/McpToolContext.java`

**Remove** the eight convenience default methods (`debug`, `info`,
`notice`, `warn`, `error`, `critical`, `alert`, `emergency`). Each was a
one-line shim that we're replacing with the logger.

**Add** the factory plus a no-arg convenience tied to the current handler
name:

```java
/** Logger named {@code name}. */
default McpLogger logger(String name) {
  return new ContextMcpLogger(this, name);
}

/**
 * Logger named after the handler that's currently executing (the
 * {@code @ToolMethod} name, or the prompt / resource name). Falls back
 * to {@code "mcp"} if the runtime didn't supply a name.
 */
default McpLogger logger() {
  return logger(handlerName());
}

/**
 * Name of the handler currently executing, as provided by the runtime.
 * Implementations that don't know (test doubles, programmatic callers)
 * return {@code "mcp"}.
 */
default String handlerName() {
  return "mcp";
}
```

The single `log(LoggingLevel, String, String)` abstract method stays —
everything else routes through it.

### File: `mocapi-server/.../tools/DefaultMcpToolContext.java`

Add a `String handlerName` field and constructor parameter. Override
`handlerName()` to return it. `McpToolsService#invokeTool` passes the
tool's name when constructing the context. Mirror the change in the
prompt and resource context paths (`DefaultMcpPromptContext`,
`DefaultMcpResourceContext`) if those exist; otherwise add the plumbing
in whichever context class is used.

### File: `mocapi-api/src/main/java/com/callibrity/mocapi/api/tools/ContextMcpLogger.java`

Package-private final class implementing `McpLogger`. Delegates every
level to `McpToolContext#log(LoggingLevel, String, String)` after formatting.

Field layout:

```java
private final McpToolContext ctx;
private final String loggerName;
```

Parameterized format uses SLF4J's `org.slf4j.helpers.MessageFormatter`
(already transitively available via Logback). Example:

```java
@Override
public void info(String format, Object... args) {
  if (!isInfoEnabled()) return;
  ctx.log(LoggingLevel.INFO, loggerName,
      MessageFormatter.arrayFormat(format, args).getMessage());
}
```

### Log-level gating

`isXxxEnabled()` returns `true` when the session's current log level is at
or below the requested level. Reads from
`McpSession.CURRENT.get().logLevel()` *if* `McpSession.CURRENT.isBound()`;
otherwise returns `true` (no session = permit everything, matches existing
`McpToolContext#log` fallback behavior).

The plain-string overloads (e.g. `info(String message)`) also check
`isInfoEnabled()` before calling `ctx.log(...)`. This is a small
redundancy — `ctx.log` already drops below-threshold messages — but
keeps the semantics identical between the two overloads.

### Tests

`mocapi-api/src/test/java/com/callibrity/mocapi/api/tools/ContextMcpLoggerTest.java`:

- Every level method routes to `ctx.log(...)` with the correct
  `LoggingLevel`.
- Parameterized format: `log.info("user {} ran tool {}", "alice",
  "blast-radius")` produces exactly the message
  `"user alice ran tool blast-radius"` on the underlying `ctx.log`.
- When the bound session's log level is `WARNING`, `log.info("{}",
  expensiveCall())` does *not* invoke `expensiveCall()` (use a Mockito
  `never()` verify or an `AtomicInteger` side-effect probe).
- `logger(name)` returns a distinct instance each call but all instances
  route to the same `ctx`.

## Acceptance criteria

- [ ] `McpLogger` public interface committed with every method listed.
- [ ] `ContextMcpLogger` package-private implementation committed.
- [ ] `McpToolContext#logger(String)`, `logger()`, and
      `handlerName()` default methods added.
- [ ] `DefaultMcpToolContext` accepts a handler name and overrides
      `handlerName()` to return it; `McpToolsService#invokeTool` passes
      the tool name when constructing the context.
- [ ] `MessageFormatter` from `org.slf4j.helpers` is the only formatting
      path — no hand-rolled `{}` parsing.
- [ ] `mvn verify` green at the repo root.
- [ ] `ContextMcpLoggerTest` contains the four test scenarios above; all
      pass.
- [ ] The eight per-level default methods on `McpToolContext` (`debug`,
      `info`, `notice`, `warn`, `error`, `critical`, `alert`,
      `emergency`) are removed. The single `log(LoggingLevel, String,
      String)` abstract method remains.
- [ ] `McpToolContextDefaultMethodsTest` updated: the `Logging_convenience_methods`
      nested class and its parameterized source are removed or replaced
      with a test against `logger(name).info(...)`.
- [ ] Any mocapi-internal callers of the removed convenience methods
      (example apps, tests, `DefaultMcpToolContext` if present) migrate
      to `logger(name).level(...)`.

## Docs

- [ ] `CHANGELOG.md` under `## [Unreleased]`: entry under
      `### Breaking changes` describing the removal of the eight
      per-level convenience methods and the new `logger(String)` path.
      Include a before/after migration snippet.
- [ ] Any prose in `docs/` referencing the old `ctx.info(logger, msg)`
      form is updated.

## Commit

Suggested commit message:

```
Replace ctx.info/debug/... with ctx.logger(name) McpLogger

BREAKING: McpToolContext no longer exposes info/debug/warn/notice/error/
critical/alert/emergency default methods. Callers get an SLF4J-style
McpLogger via ctx.logger("logger-name") instead, with parameterized-
format support via SLF4J MessageFormatter.

Before: ctx.info("catalog", "took " + ms + "ms");
After:  ctx.logger("catalog").info("took {}ms", ms);

McpToolContext#log(level, logger, message) is unchanged. Existing
tests / examples migrated.
```

## Implementation notes

- `MessageFormatter.arrayFormat(fmt, args).getMessage()` is safe to call
  with `null` args.
- Do *not* add an `McpLogger getLogger(Class<?> clazz)` overload — MCP
  logger names are free-form, not Java-class-name-derived, so taking a
  `String` keeps the abstraction honest.
- Leaves MDC out of scope — that's spec 168.
