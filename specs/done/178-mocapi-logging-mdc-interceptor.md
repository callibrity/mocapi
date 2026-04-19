# mocapi-logging: MDC correlation interceptor

## What to build

Ship a new `mocapi-logging` module plus its `mocapi-logging-spring-boot-starter`
that contribute a single `MethodInterceptor<Object>` bean. The
interceptor sets SLF4J MDC attributes for the duration of every
handler invocation so every log line emitted during the call —
including lines from user-written handler code — carries correlation
context automatically.

Pop the starter onto the classpath, it wires in. Remove it, MDC
attributes stop appearing. No mocapi API change either way.

This spec is the **pathfinder** for the observability starters
(179 metrics, 180 tracing). Subsequent specs mirror this one's
module layout, autoconfig shape, and conditional-on rules.

### MDC keys

- `mcp.session` — current MCP session id (only set when a session is
  bound).
- `mcp.handler.kind` — one of `tool` / `prompt` / `resource` /
  `resource_template`.
- `mcp.handler.name` — the tool / prompt name or resource URI / URI
  template.
- `mcp.request` — JSON-RPC request id. Reserved; only set if the
  interceptor can read it from a scoped value that mocapi binds
  around every invocation. If the plumbing for that isn't in place
  yet, leave the key unset and document the gap — a later spec wires
  the request id.

The interceptor removes exactly the keys it added — pre-existing MDC
state from upstream filters is preserved.

---

## Modules

### `mocapi-logging`

The code.

```
mocapi-logging/
  pom.xml
  src/main/java/com/callibrity/mocapi/logging/McpMdcInterceptor.java
  src/main/java/com/callibrity/mocapi/logging/McpMdcKeys.java
  src/main/java/com/callibrity/mocapi/logging/MocapiLoggingAutoConfiguration.java
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/com/callibrity/mocapi/logging/McpMdcInterceptorTest.java
```

**pom.xml:**

- `groupId`: `com.callibrity.mocapi`, `artifactId`: `mocapi-logging`,
  parent `mocapi-parent`.
- Compile dependencies: `mocapi-api` (for the method annotations),
  `mocapi-server` (for `McpSession` / `McpSession.CURRENT`),
  `org.slf4j:slf4j-api`, `org.jwcarman.methodical:methodical-core`,
  `spring-boot-autoconfigure` (optional — but required for the
  autoconfig class; mark as `optional=true` so users without Spring
  can still depend on `mocapi-logging` and use `McpMdcInterceptor`
  directly).

### `mocapi-logging-spring-boot-starter`

No code — pom-only aggregator. Depends on `mocapi-logging` and the
streamable-http starter.

```
mocapi-logging-spring-boot-starter/
  pom.xml
```

### Root pom + BOM

Both modules added to the parent pom's `<modules>` and to
`mocapi-bom`'s `<dependencyManagement>`.

---

## Files

### `McpMdcKeys.java`

```java
public final class McpMdcKeys {
  public static final String SESSION = "mcp.session";
  public static final String HANDLER_KIND = "mcp.handler.kind";
  public static final String HANDLER_NAME = "mcp.handler.name";
  public static final String REQUEST = "mcp.request";

  public static final String KIND_TOOL = "tool";
  public static final String KIND_PROMPT = "prompt";
  public static final String KIND_RESOURCE = "resource";
  public static final String KIND_RESOURCE_TEMPLATE = "resource_template";

  private McpMdcKeys() {}
}
```

### `McpMdcInterceptor.java`

```java
public final class McpMdcInterceptor implements MethodInterceptor<Object> {

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    var method = invocation.method();
    var kind = kindOf(method);
    var name = nameOf(method, kind);
    var sessionId = McpSession.CURRENT.isBound() ? McpSession.CURRENT.get().id() : null;

    var added = new ArrayList<String>();
    putIfPresent(added, HANDLER_KIND, kind);
    putIfPresent(added, HANDLER_NAME, name);
    putIfPresent(added, SESSION, sessionId);

    try {
      return invocation.proceed();
    } finally {
      added.forEach(MDC::remove);
    }
  }

  private static void putIfPresent(List<String> added, String key, String value) {
    if (value != null) {
      MDC.put(key, value);
      added.add(key);
    }
  }

  /** Returns "tool" / "prompt" / "resource" / "resource_template" or null if not an MCP handler. */
  private static String kindOf(Method m) {
    if (m.isAnnotationPresent(McpTool.class))             return KIND_TOOL;
    if (m.isAnnotationPresent(McpPrompt.class))           return KIND_PROMPT;
    if (m.isAnnotationPresent(McpResource.class))         return KIND_RESOURCE;
    if (m.isAnnotationPresent(McpResourceTemplate.class)) return KIND_RESOURCE_TEMPLATE;
    return null;
  }

  private static String nameOf(Method m, String kind) {
    if (kind == null) return null;
    return switch (kind) {
      case KIND_TOOL              -> m.getAnnotation(McpTool.class).name();
      case KIND_PROMPT            -> m.getAnnotation(McpPrompt.class).name();
      case KIND_RESOURCE          -> m.getAnnotation(McpResource.class).uri();
      case KIND_RESOURCE_TEMPLATE -> m.getAnnotation(McpResourceTemplate.class).uriTemplate();
      default -> null;
    };
  }
}
```

### `MocapiLoggingAutoConfiguration.java`

```java
@AutoConfiguration
@ConditionalOnClass({MDC.class, MethodInterceptor.class, McpSession.class})
public class MocapiLoggingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  McpMdcInterceptor mcpMdcInterceptor() {
    return new McpMdcInterceptor();
  }
}
```

Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

---

## Shared kind/name helper

Both `McpMdcInterceptor` here and the forthcoming metrics / tracing
interceptors need the same `kindOf(Method)` + `nameOf(Method, kind)`
logic. Don't copy-paste.

Proposal: extract a tiny utility into `mocapi-api`'s handler-related
package (since it's purely annotation-introspection, depends on no
runtime state). Something like
`com.callibrity.mocapi.api.handlers.HandlerKinds`:

```java
public final class HandlerKinds {
  public static String kindOf(Method m) { /* ... */ }
  public static String nameOf(Method m) { /* ... */ }
  private HandlerKinds() {}
}
```

Every observability starter uses this instead of rolling its own.
Landing this utility is part of this spec (178), not a separate spec.

---

## Tests

`McpMdcInterceptorTest.java` covers:

- For a `@McpTool`-annotated method, invocation sets
  `mcp.handler.kind=tool` and `mcp.handler.name=<annotation name>`
  for the duration of `proceed()`. After `proceed()` returns, the
  MDC keys are gone.
- Same for `@McpPrompt`, `@McpResource`, `@McpResourceTemplate`.
- When `McpSession.CURRENT` is bound, `mcp.session` is also set; when
  it's not bound, the key is absent.
- Exception thrown from `proceed()` — MDC keys still get removed
  (assert on `MDC.getCopyOfContextMap()` after the exception
  propagates).
- Pre-existing MDC entries (added by the test before invocation) are
  preserved through the invocation and still present after it
  returns.
- The interceptor does not register its keys if the annotation is
  missing (non-MCP method) — useful if someone registers the
  interceptor and Methodical runs it on an off-the-path method
  somehow.

Use the static `MethodInvocation.of(...)` factory to construct
invocations for testing — no reflection setup needed.

---

## Acceptance criteria

- [ ] `mocapi-logging` module exists with the layout above.
- [ ] `mocapi-logging-spring-boot-starter` module exists, no `src/`.
- [ ] Both modules added to the parent pom's `<modules>` and the
      BOM's `<dependencyManagement>`.
- [ ] `HandlerKinds` utility added to `mocapi-api` (package TBD;
      `com.callibrity.mocapi.api.handlers` is the proposal).
- [ ] `McpMdcInterceptor` uses `HandlerKinds` — no duplicated switch.
- [ ] MDC keys match the table exactly: `mcp.session`,
      `mcp.handler.kind`, `mcp.handler.name` (+ `mcp.request`
      reserved, only set if a scoped request id is available).
- [ ] Interceptor removes only the keys it added; pre-existing MDC
      state is preserved.
- [ ] `mcp.handler.kind` values are exactly `tool` / `prompt` /
      `resource` / `resource_template` — stable strings suitable
      for log-grep / alert rules.
- [ ] Exception paths still clear MDC (test assertion).
- [ ] Autoconfig conditional on `MDC.class`, `MethodInterceptor.class`,
      `McpSession.class`.
- [ ] A consumer that adds only
      `mocapi-logging-spring-boot-starter` gets MDC wiring with no
      additional mocapi API calls.
- [ ] `mvn verify` + `mvn spotless:check` green.
- [ ] SonarCloud: no new issues.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: new
      `mocapi-logging` + `-spring-boot-starter` modules. Document the
      MDC key table and the on-classpath contract (add the starter
      → keys appear; remove it → they don't).
- [ ] New `docs/logging.md`: short page explaining the keys, the
      virtual-threads caveat (MDC reads happen on the current
      thread, not propagated across executor boundaries without
      explicit handoff), and a Logback pattern snippet showing the
      keys in the layout.
- [ ] `docs/observability-roadmap.md`: mark MDC as shipped (link to
      this spec).

## Commit

Suggested commit message:

```
Add mocapi-logging: SLF4J MDC correlation interceptor

New module pair: mocapi-logging (code) + mocapi-logging-spring-boot-starter
(pom-only aggregator). Contributes a single MethodInterceptor<Object>
bean that sets mcp.session, mcp.handler.kind, and mcp.handler.name
MDC attributes around every handler invocation. Every log line —
including those from user handler code — carries correlation
context automatically.

Keys are scoped to the invocation (try-finally removes exactly what
was added); pre-existing MDC entries from upstream filters survive
unchanged.

First of four observability starters landing post-interceptor-
adoption. Sets the module layout + autoconfig + conditional-on
patterns that 179 (metrics), 180 (tracing), and 181 (actuator)
will mirror.
```

## Implementation notes

- SLF4J MDC is ThreadLocal-backed. On virtual threads (JDK 21+),
  MDC is read per-thread at log time, so setting attributes on the
  thread that runs the handler is sufficient for logs emitted by
  that thread. Handlers that spawn their own executors need to
  propagate MDC themselves — document this in `docs/logging.md`.
- Don't use `MDC.MDCCloseable` — it's fine for single-key scopes
  but gets awkward with a variable number of keys. The manual
  try-finally + `added` list is clearer.
- Leave request-id wiring (`mcp.request`) for a follow-up. It needs
  either a new ScopedValue bound in the dispatch layer or an
  accessor on the current `McpToolContext` / equivalent. Either
  change belongs with a broader "per-invocation scoped state" spec
  that isn't queued yet.
- No `@ConditionalOnProperty` toggle. Starter on classpath = MDC
  on. Starter off classpath = MDC off. Users who want to disable
  it while the starter is present can `@Bean`-override with a no-op
  `McpMdcInterceptor` subclass.
