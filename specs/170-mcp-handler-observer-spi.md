# McpHandlerObserver SPI + hook into tool / prompt / resource dispatch

## What to build

An observer SPI that lets pluggable modules (metrics, tracing, audit
logging, etc.) see every handler invocation without adding hard
dependencies to `mocapi-server`. This is the plumbing shared by the
metrics (171/172) and tracing (175) specs.

### File: `mocapi-api/src/main/java/com/callibrity/mocapi/api/observability/HandlerKind.java`

```java
public enum HandlerKind {
  TOOL, PROMPT, RESOURCE, RESOURCE_TEMPLATE
}
```

### File: `mocapi-api/src/main/java/com/callibrity/mocapi/api/observability/McpHandlerObserver.java`

```java
public interface McpHandlerObserver {
  /**
   * Called at handler entry. Returned scope is {@code close()}d in a
   * try-with-resources by the dispatcher; observers that want to record
   * duration / emit spans / stop timers / etc. do their cleanup there.
   * Must not throw.
   */
  HandlerScope onInvocation(HandlerKind kind, String handlerName);

  interface HandlerScope extends AutoCloseable {
    /** Called when the handler returns normally. */
    default void onSuccess() {}

    /** Called when the handler throws. The exception is not swallowed. */
    default void onError(Throwable throwable) {}

    /** Called exactly once — either normally on exit, or after onError(). */
    @Override void close();
  }

  /** No-op scope useful for observers that have nothing to record. */
  HandlerScope NOOP_SCOPE = new HandlerScope() {
    @Override public void close() {}
  };
}
```

### File: `mocapi-api/src/main/java/com/callibrity/mocapi/api/observability/CompositeMcpHandlerObserver.java`

Fan-out wrapper that invokes every supplied observer in order. Used when
multiple observers are registered (e.g. metrics + tracing). Exceptions
thrown by any individual observer's scope method are caught and logged
at WARN level — one broken observer must not crash the dispatch or
cascade to the others.

### Hook: `mocapi-server/.../tools/McpToolsService.java`

Constructor takes an additional `McpHandlerObserver observer` parameter
(defaulting to no-op via Spring autoconfig if no observers are
registered). Inside `invokeTool(...)`, wrap the existing try/catch:

```java
try (var scope = observer.onInvocation(HandlerKind.TOOL, name)) {
  try {
    Object result = ScopedValue.where(McpToolContext.CURRENT, ctx).call(...);
    var r = toCallToolResult(result);
    scope.onSuccess();
    return r;
  } catch (ConstraintViolationException cve) {
    scope.onError(cve);
    return ConstraintViolationFormatter.toCallToolResult(cve);
  } catch (Exception e) {
    scope.onError(e);
    log.warn(...);
    return toErrorCallToolResult(e);
  }
}
```

### Hook: `mocapi-server/.../prompts/McpPromptsService.java`

Analogous wrap in `getPrompt(...)`, using `HandlerKind.PROMPT`.

### Hook: `mocapi-server/.../resources/McpResourcesService.java`

Analogous wrap in `readResource(...)`, using `HandlerKind.RESOURCE`.

### Hook: `mocapi-server/.../resources/McpResourceTemplatesService.java`

Analogous wrap, using `HandlerKind.RESOURCE_TEMPLATE`.

### Autoconfig: `mocapi-server/.../autoconfigure/MocapiObserverAutoConfiguration.java`

```java
@AutoConfiguration
class MocapiObserverAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  McpHandlerObserver compositeMcpHandlerObserver(List<McpHandlerObserver> observers) {
    return observers.isEmpty()
        ? (kind, name) -> McpHandlerObserver.NOOP_SCOPE
        : new CompositeMcpHandlerObserver(observers);
  }
}
```

Wire into the existing tool/prompt/resource service bean constructors.
Add its class to `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Acceptance criteria

- [ ] `HandlerKind`, `McpHandlerObserver`, `McpHandlerObserver.HandlerScope`,
      `CompositeMcpHandlerObserver` all exist in
      `com.callibrity.mocapi.api.observability`.
- [ ] The four dispatch services each wrap their handler invocation in
      `try (var scope = observer.onInvocation(...))` and call
      `onSuccess()` / `onError(...)` appropriately.
- [ ] Zero registered observers → no-op scope, no overhead.
- [ ] One broken observer cannot crash the dispatch (verified by test).
- [ ] Autoconfig registers the composite observer bean when one or more
      `McpHandlerObserver` beans exist.
- [ ] Unit tests for `CompositeMcpHandlerObserver` (fan-out order, error
      swallowing, scope close ordering).
- [ ] Integration test: register two `McpHandlerObserver` test doubles,
      invoke a tool, assert both observers saw `onInvocation` →
      `onSuccess` → `close` in order.
- [ ] `mvn verify` green.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: entry introducing
      the observer SPI and noting the intent (metrics + tracing modules
      to follow).
- [ ] New `docs/observability.md` (or section in existing docs)
      explaining how to register a custom observer.

## Commit

Suggested commit message:

```
Add McpHandlerObserver SPI for pluggable invocation observability

Tool, prompt, and resource dispatch each wrap their handler call in a
HandlerScope obtained from registered McpHandlerObserver beans. Scopes
receive onSuccess/onError/close callbacks; exceptions from observer
code are caught and logged, never propagated. Groundwork for the
forthcoming mocapi-metrics and mocapi-tracing modules that plug in via
this SPI without pulling Micrometer or OpenTelemetry into
mocapi-server.
```
