# Mocapi

Mocapi is a modular framework for building [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25) tools using Spring Boot. It simplifies secure, structured interactions between LLMs and services via annotated Java components.

![Maven Central Version](https://img.shields.io/maven-central/v/com.callibrity.mocapi/mocapi-parent)
![GitHub License](https://img.shields.io/github/license/callibrity/mocapi)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=coverage)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)

## Getting Started

Mocapi includes a Spring Boot starter, making it easy to get started by simply adding a dependency:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```
By default, Mocapi will listen for MCP requests on the `/mcp` endpoint. You can change this by setting the `mocapi.endpoint` property:

```properties
mocapi.endpoint=/your-custom-endpoint
```

## Defining Tools

Annotate a Spring bean with `@ToolService` and its methods with `@ToolMethod` to register MCP tools:

```java
import com.callibrity.mocapi.tools.annotation.ToolMethod;
import com.callibrity.mocapi.tools.annotation.ToolService;

@ToolService
public class HelloTool {

    @ToolMethod(name = "hello", description = "Returns a greeting message")
    public HelloResponse sayHello(String name) {
        return new HelloResponse(String.format("Hello, %s!", name));
    }

    public record HelloResponse(String message) {}
}
```

## Streaming and Progress

To send progress updates or use interactive features (elicitation, sampling), add an `McpStreamContext<R>` parameter to your tool method. **Streaming tool methods must return `void`** — the final result is sent explicitly via `ctx.sendResult(...)` rather than returned from the method:

```java
import com.callibrity.mocapi.stream.McpStreamContext;

@ToolMethod(name = "countdown", description = "Counts down with progress updates")
public void countdown(int from, McpStreamContext<CountdownResponse> ctx) {
    for (int i = from; i > 0; i--) {
        ctx.sendProgress((double) (from - i), from);
    }
    ctx.sendResult(new CountdownResponse("Done!"));
}
```

The context provides:

- `sendProgress(double, double)` for progress notifications
- `sendResult(R)` to publish the final result on the SSE stream (terminal — call exactly once)
- `log(LoggingLevel, String)` for structured logging

Non-streaming tool methods (those without an `McpStreamContext` parameter) return their result directly as shown in the "Defining Tools" section above.

## Elicitation

Tools can prompt the user for input during execution using the `elicit` method on `McpStreamContext`. Build a schema using the fluent `RequestedSchemaBuilder` API:

```java
import com.callibrity.mocapi.model.ElicitResult;

@ToolMethod(name = "greet", description = "Asks for user details")
public String greet(McpStreamContext<String> ctx) {
    ElicitResult result = ctx.elicit("Please enter your details", schema -> {
        schema.string("name", "Your name");
        schema.integer("age", "Your age");
        schema.bool("subscribe", "Subscribe to updates?", false);
        schema.choose("role", List.of("admin", "user", "guest"));
    });

    if (result.isAccepted()) {
        return "Hello, " + result.getString("name") + "!";
    }
    return "User declined.";
}
```

For type-safe results, use the bean-binding overload which deserializes accepted responses into a Java class and returns `Optional.empty()` when the user declines:

```java
@ToolMethod(name = "register", description = "Registers a new user")
public String register(McpStreamContext<String> ctx) {
    Optional<Registration> reg = ctx.elicit("Enter registration info", schema -> {
        schema.string("name", "Full name");
        schema.string("email", "Email address");
    }, Registration.class);

    return reg.map(r -> "Registered: " + r.name())
              .orElse("Registration cancelled.");
}

record Registration(String name, String email) {}
```

## Sampling

Tools can request LLM completions via the `sample` method:

```java
import com.callibrity.mocapi.model.CreateMessageResult;

@ToolMethod(name = "summarize", description = "Summarizes text using an LLM")
public String summarize(String text, McpStreamContext<String> ctx) {
    CreateMessageResult result = ctx.sample("Summarize: " + text, 200);
    return result.text();
}
```

## Tool Errors

Unhandled exceptions thrown from tool methods are automatically caught and returned as `CallToolResult` responses with `isError=true`. The exception message is sent to the LLM as a `TextContent` block, allowing it to understand and recover from the error. This follows the MCP specification's distinction between tool errors (content-level, visible to the LLM) and protocol errors (JSON-RPC errors for transport/protocol failures).

You can also return tool errors explicitly:

```java
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;

@ToolMethod(name = "validate", description = "Validates input")
public CallToolResult validate(String input) {
    if (input.isBlank()) {
        return new CallToolResult(
            List.of(new TextContent("Input must not be blank", null)),
            true,
            null);
    }
    return new CallToolResult(
        List.of(new TextContent("Valid", null)),
        null,
        null);
}
```

## HTTP Transport

Mocapi exposes a Streamable HTTP endpoint that accepts `JsonRpcMessage` payloads directly via `@RequestBody`. The controller handles JSON-RPC request dispatch, SSE streaming, and session management automatically. No manual JSON parsing is required — Jackson's `@JsonCreator` on `JsonRpcMessage` handles polymorphic deserialization of calls, notifications, results, and errors.

## Running the Example Application

Mocapi includes an example application to demonstrate how to use the framework. To run it, follow these steps:

1. Navigate to the `mocapi-example` directory.
    ```bash
    cd mocapi-example
    ```
2. Run the application using Maven:

    ```bash
    mvn spring-boot:run
    ```
3. The application will start on port 8080 by default. You can access the example tools at the `/mcp` endpoint.

## Using the MCP Inspector

You can interact with the example application using the MCP Inspector. To do this, follow these steps:
1. Launch the MCP inspector by running the following command in your terminal:
    ```bash
    npx @modelcontextprotocol/inspector
    ```
2. Your browser will open the MCP Inspector interface. Enter the URL of the running example application (e.g., `http://localhost:8080/mcp`) in the URL field.
3. Select the "Streamable HTTP" transport option.
4. Click "Connect" to establish a connection to the example application.
5. You can now explore the available tools, send requests, and view responses directly in the MCP Inspector interface.
6. Enjoy!

## What's in a Name?

Mocapi is a made-up word that includes the letters MCP (Model Context Protocol). It’s pronounced moh-cap-ee (/ˈmoʊˌkæpi/), like a friendly little robot who speaks protocol.

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Building from Source

To build the project yourself, simply clone the repository and use [Apache Maven](https://maven.apache.org/) to compile and package it:

```bash
mvn clean install
```

## License

This project is licensed under the Apache License 2.0—see the [LICENSE](LICENSE) file for details.
