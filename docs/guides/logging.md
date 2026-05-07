# Logging

Mocapi ships an optional SLF4J MDC (Mapped Diagnostic Context) correlation
layer. Add `mocapi-logging` to your application and every
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
  <artifactId>mocapi-logging</artifactId>
</dependency>
```

That's it. The autoconfiguration registers one per-handler customizer
bean per handler kind (`CallToolHandlerCustomizer`,
`GetPromptHandlerCustomizer`, `ReadResourceHandlerCustomizer`,
`ReadResourceTemplateHandlerCustomizer`). Each customizer attaches an
`McpMdcInterceptor` to its handler's invocation chain at startup, with
the handler's kind and name baked in — so the hot path does no
reflection.

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
present but disable the behavior at runtime, exclude the
autoconfiguration:

```java
@SpringBootApplication(exclude = MocapiLoggingAutoConfiguration.class)
public class MyApp { }
```

or in `application.properties`:

```properties
spring.autoconfigure.exclude=com.callibrity.mocapi.logging.MocapiLoggingAutoConfiguration
```

## Startup log lines

Every built-in mocapi customizer emits one INFO-level line each time it
successfully attaches an interceptor, guard, or resolver to a handler.
Use these lines to confirm at boot time that the autoconfig you expected
to activate actually did — no more hunting through `/actuator/conditions`.

Format:

```
Attached <class simple name> interceptor to <kind> "<name>"
Attached <class simple name> guard       to <kind> "<name>"
```

Where `<kind>` is `tool`, `prompt`, `resource`, or `resource_template`,
and `<name>` is the tool/prompt name or resource URI / URI template.

Which class emits what:

| Logger (autoconfig FQN)                                                              | Attaches                        |
|--------------------------------------------------------------------------------------|---------------------------------|
| `com.callibrity.mocapi.logging.MocapiLoggingAutoConfiguration`                       | `McpMdcInterceptor`             |
| `com.callibrity.mocapi.o11y.MocapiO11yAutoConfiguration`                             | `McpObservationInterceptor`     |
| `com.callibrity.mocapi.jakarta.MocapiJakartaValidationAutoConfiguration`             | `JakartaValidationInterceptor`  |
| `com.callibrity.mocapi.security.spring.autoconfigure.MocapiSpringSecurityGuardsAutoConfiguration` | `ScopeGuard` / `RoleGuard` (only when `@RequiresScope` / `@RequiresRole` is present) |

Quick debugging recipes:

- *"Why don't my metrics show up?"* — `grep 'Attached McpObservationInterceptor'`
  on startup output. Zero lines means the o11y autoconfig didn't activate
  (likely no `ObservationRegistry` bean). N lines means it's wired — look
  elsewhere (exposure, actual tool invocations).
- *"Is MDC wired?"* — `grep 'Attached McpMdcInterceptor'`.
- *"Did validation activate?"* — `grep 'Attached JakartaValidationInterceptor'`.

To silence the attachment lines once you've confirmed wiring:

```properties
logging.level.com.callibrity.mocapi=WARN
```

Conditional customizers (spring-security-guards) log only when they
actually attach — methods without `@RequiresScope` / `@RequiresRole`
produce no output. Per-invocation logging (which tool was called, by
whom) is emitted by the o11y interceptor, not the customizer, and is
out of scope for the attachment log.
