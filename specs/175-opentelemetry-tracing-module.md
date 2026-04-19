# mocapi-tracing module + starter: OpenTelemetry spans per invocation

## What to build

An OpenTelemetry `McpHandlerObserver` implementation and its pom-only
starter. Emits one span per tool / prompt / resource invocation with the
correlation attributes operators care about.

### Modules

```
mocapi-tracing/
  pom.xml
  src/main/java/com/callibrity/mocapi/tracing/OtelMcpHandlerObserver.java
  src/main/java/com/callibrity/mocapi/tracing/MocapiTracingAutoConfiguration.java
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/...

mocapi-tracing-spring-boot-starter/
  pom.xml  (no src/)
```

### mocapi-tracing pom.xml

- `artifactId`: `mocapi-tracing`
- Dependencies: `mocapi-api`, `io.opentelemetry:opentelemetry-api`,
  `spring-boot-autoconfigure` (optional).
- Do NOT depend on an SDK / exporter — users supply their own.

### mocapi-tracing-spring-boot-starter pom.xml

- `artifactId`: `mocapi-tracing-spring-boot-starter`
- Depends on `mocapi-tracing` and
  `mocapi-streamable-http-spring-boot-starter`.

### Class: `com.callibrity.mocapi.tracing.OtelMcpHandlerObserver`

```java
public final class OtelMcpHandlerObserver implements McpHandlerObserver {
  private final Tracer tracer;

  public OtelMcpHandlerObserver(OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer("com.callibrity.mocapi", versionOf("mocapi"));
  }

  @Override
  public HandlerScope onInvocation(HandlerKind kind, String name) {
    String spanName = "mcp." + kindTag(kind) + " " + name;
    Span span = tracer.spanBuilder(spanName)
        .setSpanKind(SpanKind.INTERNAL)
        .setAttribute("mcp.handler.kind", kindTag(kind))
        .setAttribute("mcp.handler.name", name)
        // session id + request id from ScopedValues if present
        .startSpan();
    Scope otelScope = span.makeCurrent();
    return new HandlerScope() {
      @Override public void onSuccess() {
        // for tools, check if the result was isError=true — see implementation notes
      }
      @Override public void onError(Throwable t) {
        span.setStatus(StatusCode.ERROR);
        span.recordException(t);
      }
      @Override public void close() {
        otelScope.close();
        span.end();
      }
    };
  }
}
```

**Span names:**

- Tools: `mcp.tool <tool-name>`
- Prompts: `mcp.prompt <prompt-name>`
- Resources: `mcp.resource <resource-name>`
- Resource templates: `mcp.resource-template <template-name>`

**Base attributes (always set):**

- `mcp.handler.kind`: `tool` / `prompt` / `resource` / `resource-template`
- `mcp.handler.name`: the handler name

**Conditional attributes:**

- `mcp.session.id`: from `McpSession.CURRENT` if bound
- `mcp.request.id`: from request-id scoped value if available (skip if
  plumbing is unclear — documented gap, add later)
- `mcp.tool.is_error`: `true` when a tool returned `CallToolResult.isError
  = true`. Requires the observer to see the result, which `HandlerScope`
  currently doesn't — either add a `onSuccess(Object result)` overload
  to `McpHandlerObserver` OR leave this attribute out for this iteration
  and follow up. **Decision: leave it out for 175; add in a follow-up
  spec once the SPI needs to evolve anyway.**

**Span status:**

- Normal return: default (UNSET, which OTel treats as OK).
- Exception: ERROR + `recordException(t)`.
- `isError=true` result: does NOT set ERROR status (deferred, per above).

### Autoconfig: `MocapiTracingAutoConfiguration`

```java
@AutoConfiguration
@ConditionalOnClass({Tracer.class, OpenTelemetry.class})
@ConditionalOnBean(OpenTelemetry.class)
class MocapiTracingAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  OtelMcpHandlerObserver otelMcpHandlerObserver(OpenTelemetry openTelemetry) {
    return new OtelMcpHandlerObserver(openTelemetry);
  }
}
```

Register via `AutoConfiguration.imports`.

### Tests

`mocapi-tracing/src/test/.../OtelMcpHandlerObserverTest.java`:

- Uses OTel's `InMemorySpanExporter` via an in-process SDK for tests
  (test-scope dependency on `opentelemetry-sdk` + `opentelemetry-sdk-testing`).
- Invoking a tool emits one span named `mcp.tool <name>` with
  `SpanKind.INTERNAL`, correct `mcp.handler.kind` /
  `mcp.handler.name` attributes.
- Throwing path: span status is ERROR, exception recorded.
- Session binding: `mcp.session.id` attribute set when
  `McpSession.CURRENT` is bound during the invocation.

## Acceptance criteria

- [ ] Both modules exist with the layouts above.
- [ ] Span names, kinds, and base attributes match the spec.
- [ ] Exception path sets ERROR + records exception.
- [ ] `isError=true` tool results do NOT fail the span (and don't yet
      add `mcp.tool.is_error`; documented follow-up).
- [ ] Tests pass with `opentelemetry-sdk-testing` in test scope.
- [ ] `mvn verify` green.
- [ ] Both modules in `mocapi-bom`.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: `mocapi-tracing`
      + starter with the span name convention and attribute list.
- [ ] `docs/tracing.md` (new) explaining the integration and the OTel
      Spring Boot starter setup users typically already have.

## Commit

Suggested commit message:

```
Add mocapi-tracing: OpenTelemetry spans per handler invocation

New module registers an OtelMcpHandlerObserver that emits one span per
tool / prompt / resource / resource-template dispatch with
mcp.handler.kind, mcp.handler.name, and mcp.session.id attributes.
Ships alongside the conventional pom-only
mocapi-tracing-spring-boot-starter. Users supply their own OTel SDK /
exporter; the module only depends on opentelemetry-api.
```

## Implementation notes

- Depends on spec 170 (observer SPI) being in place.
- Don't hold on to the `Span` past the scope's `close()` — OTel's
  context propagation handles child spans opened by user code inside
  the handler.
- OTel metrics are out of scope; operators can bridge Micrometer meters
  from spec 171 to OTel via the Micrometer-OTel bridge.
