# Mocapi


A [Model Context Protocol (2025-06-18)](https://modelcontextprotocol.io/specification/2025-06-18) compliant framework built for Spring Boot.

## Getting Started

Mocapi includes a Spring Boot starter, making it easy to get started by simply adding a dependency:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-boot-starter</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```
By default, Mocapi will listen for MCP requests on the `/mcp` endpoint. You can change this by setting the `ripcurl.endpoint` property:

```properties
ripcurl.endpoint=/your-custom-endpoint
```

## Writing Tools

In order to create MCP tools using Mocapi, you first need to import the `mocapi-tools` dependency into your project:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-tools</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

This will automatically activate the `MocapiToolsAutoConfiguration` which will enable MCP tools support. To register a
tool, you need to create a bean method annotated with `@Tool`:

```java
import com.callibrity.mocapi.tools.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class HelloTool {

    @Tool
    public HelloResponse sayHello(String name) {
        return new HelloResponse(String.format("Hello, %s!", name));
    }

    public record HelloResponse(String message) { }
}
```

Next, you must register a `McpToolProvider` bean to expose the tool to Mocapi:

```java
@Bean
public McpToolProvider helloToolsProvider(AnnotationMcpToolProviderFactory factory, HelloTool helloTool) {
    return factory.create(helloTool);
}
```