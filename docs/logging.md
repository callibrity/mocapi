# Logging

Mocapi ships an optional SLF4J MDC (Mapped Diagnostic Context) correlation
layer. Add `mocapi-logging-spring-boot-starter` to your application and every
log line emitted during an MCP handler invocation — including lines from
user handler code — carries correlation keys automatically. Remove the
starter and the keys stop appearing. No mocapi API calls required either way.

## Keys

| Key                | Value                                                                   |
|--------------------|-------------------------------------------------------------------------|
| `mcp.session`      | Current MCP session id (only set when a session is bound to the call). |
| `mcp.handler.kind` | One of `tool`, `prompt`, `resource`, `resource_template`.              |
| `mcp.handler.name` | Tool / prompt name, or resource URI / URI template.                    |
| `mcp.request`      | Reserved for JSON-RPC request id; wired by a follow-up spec.           |

The interceptor removes exactly the keys it added. Pre-existing MDC
entries populated by upstream servlet filters, tracing agents, or custom
code survive the handler call unchanged.

## Usage

Add the starter:

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-logging-spring-boot-starter</artifactId>
</dependency>
```

That's it. The autoconfiguration registers an `McpMdcInterceptor` bean,
which Methodical picks up as an ambient interceptor on every mocapi
handler invoker.

## Logback pattern

A minimal logback pattern that surfaces the keys:

```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level [%X{mcp.session:-},%X{mcp.handler.kind:-}/%X{mcp.handler.name:-}] %logger - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="STDOUT"/>
  </root>
</configuration>
```

`%X{key:-}` prints an empty placeholder when the key is absent, so lines
from non-handler code (Spring startup, scheduled jobs) still render cleanly.

## Virtual threads and async handoffs

SLF4J's MDC is backed by `ThreadLocal`. On virtual threads (JDK 21+), MDC
reads happen on the current carrier thread at log time, so setting
attributes on the thread that runs the handler is sufficient for any log
line emitted by that thread — including downstream synchronous work and
async operations that rejoin on the same virtual thread.

**MDC does not propagate automatically across arbitrary executor
boundaries.** If your handler submits work to its own `ExecutorService`
and logs from the pool thread, you must propagate MDC explicitly:

```java
var snapshot = MDC.getCopyOfContextMap();
executor.submit(() -> {
  MDC.setContextMap(snapshot == null ? Map.of() : snapshot);
  try {
    doWork();
  } finally {
    MDC.clear();
  }
});
```

For wider coverage, wrap your executor with a utility that captures and
restores MDC per task (e.g. an `ExecutorService` decorator, or Micrometer
observation propagation if you're already using it).

## Disabling

No `@ConditionalOnProperty` toggle is provided. Starter on classpath = MDC
on. Starter off classpath = MDC off. If you want to keep the starter
present but disable the behavior at runtime, define a `@Bean` of type
`McpMdcInterceptor` that does nothing:

```java
@Bean
McpMdcInterceptor mcpMdcInterceptor() {
  return new McpMdcInterceptor() {
    @Override
    public Object intercept(org.jwcarman.methodical.intercept.MethodInvocation<?> inv) {
      return inv.proceed();
    }
  };
}
```

`@ConditionalOnMissingBean` on the autoconfig bean means your override
wins.
