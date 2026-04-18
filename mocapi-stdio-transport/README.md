# mocapi-stdio-transport

MCP stdio transport for Mocapi: newline-delimited JSON-RPC on stdin/stdout, for
clients that launch the server as a subprocess (Claude Desktop, Cursor, MCP
Inspector, etc.).

## Enabling

Add the starter (or this transport module directly) and set one property:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-stdio-spring-boot-starter</artifactId>
</dependency>
```

```properties
mocapi.stdio.enabled=true
spring.main.web-application-type=none
spring.main.banner-mode=off
```

The `@ConditionalOnProperty(mocapi.stdio.enabled=true)` gate keeps the transport
off by default — stdio apps opt in explicitly so you don't accidentally corrupt
stdout in non-stdio Spring Boot deployments.

## Critical: stdout is protocol traffic only

Everything written to `System.out` other than a JSON-RPC message will break the
protocol. Route all logging to stderr. A minimal `logback-spring.xml`:

```xml
<configuration>
  <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
    <target>System.err</target>
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  <root level="WARN"><appender-ref ref="STDERR"/></root>
</configuration>
```

## Design in one paragraph

A single reader thread blocks on `BufferedReader.readLine()` against stdin and
hands each parsed `JsonRpcMessage` to a virtual-thread executor. Per-message
virtual threads are required because handlers can block awaiting a client
response (elicitation, sampling) — dispatching inline would deadlock the reader
against input it needs to read. `StdioTransport.send` writes one JSON line to
stdout; `PrintStream` is internally synchronized so concurrent writes from
parallel dispatches can't interleave. The reader uses try-with-resources on the
executor, so EOF → close → awaitTermination → JVM exit, with no dropped
responses.

## Example

See [`examples/stdio/`](../examples/stdio/) for a working end-to-end setup with
Claude Desktop configuration, native-image instructions, and a curl-style
smoke test pipeline.
