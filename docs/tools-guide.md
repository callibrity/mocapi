# Writing Tools

Tools are the primary way to expose functionality to MCP clients. A tool is any Java method annotated with `@McpTool` on a Spring bean.

## Defining a Tool

Annotate methods with `@McpTool` and register the enclosing class as a Spring bean:

```java
import com.callibrity.mocapi.api.tools.McpTool;
import org.springframework.stereotype.Component;

@Component
public class WeatherTool {

    @McpTool(name = "get-weather", description = "Gets the current weather for a city")
    public WeatherResponse getWeather(String city) {
        // your logic here
        return new WeatherResponse(city, 72.0, "sunny");
    }

    public record WeatherResponse(String city, double temperature, String condition) {}
}
```

Any bean-hood mechanism works — `@Component`, `@Service`, or a `@Bean` factory method:

```java
@Configuration
public class ToolConfig {

    @Bean
    public WeatherTool weatherTool(WeatherApiClient client) {
        return new WeatherTool(client);
    }
}
```

The framework discovers every bean in the context, scans its methods for `@McpTool`, and registers one handler per annotated method. Each registered tool is logged at `INFO` level during startup (see [Startup Logging](architecture.md#startup-logging) for the full catalog).

## Tool Method Basics

A `@McpTool` method receives its arguments as method parameters and returns a result. The framework handles JSON serialization in both directions.

```java
@McpTool(name = "add", description = "Adds two numbers")
public AddResult add(int a, int b) {
    return new AddResult(a + b);
}

public record AddResult(int sum) {}
```

Tool methods should return records or objects, not scalar types. The framework serializes the return value to JSON and includes it as `structuredContent` in the response. Scalar returns (int, String, etc.) produce text-only content without structured data.

### Naming

If you omit the `name` attribute, the framework generates a name from the class and method names. For a class `MathTool` with method `add`, the generated name would be `math-tool.add`.

You can also set a `title` and `description`:

```java
@McpTool(
    name = "calculate",
    title = "Calculator",
    description = "Performs basic arithmetic operations")
public double calculate(String operation, double a, double b) { ... }
```

### Parameters

Method parameters map directly to the tool's input schema. The framework generates a JSON Schema from the method signature:

```java
@McpTool(name = "search", description = "Searches for documents")
public SearchResult search(String query, int maxResults) { ... }
```

This generates an input schema like:

```json
{
  "type": "object",
  "properties": {
    "query": { "type": "string" },
    "maxResults": { "type": "integer" }
  },
  "required": ["query", "maxResults"]
}
```

Use Swagger/OpenAPI annotations for richer schemas:

```java
import io.swagger.v3.oas.annotations.media.Schema;

@McpTool(name = "search", description = "Searches for documents")
public SearchResult search(
    @Schema(description = "The search query") String query,
    @Schema(description = "Maximum results to return", minimum = "1", maximum = "100") int maxResults) {
    ...
}
```

### Record Parameters

For tools with many parameters, use a record annotated with `@McpToolParams`:

```java
import com.callibrity.mocapi.api.tools.McpToolParams;

@McpTool(name = "create-user", description = "Creates a new user")
public UserResponse createUser(@McpToolParams CreateUserRequest request) {
    return new UserResponse(request.name(), request.email());
}

public record CreateUserRequest(String name, String email, int age) {}
```

The input schema is generated from the record's fields.

### Return Values

Tools return their result directly. The framework serializes it to JSON and wraps it in a `CallToolResult` with both `content` (text representation) and `structuredContent` (JSON object).

```java
// Returns {"message": "Hello!"} as structuredContent
@McpTool(name = "greet", description = "Greets someone")
public GreetResponse greet(String name) {
    return new GreetResponse("Hello, " + name + "!");
}

public record GreetResponse(String message) {}
```

### Void Tools

A tool that returns `void` produces an empty `CallToolResult`:

```java
@McpTool(name = "notify", description = "Sends a notification")
public void sendNotification(String message) {
    notificationService.send(message);
}
```

### Returning CallToolResult Directly

For full control over the response, return a `CallToolResult`:

```java
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;

@McpTool(name = "status", description = "Returns system status")
public CallToolResult getStatus() {
    return new CallToolResult(
        List.of(new TextContent("System is healthy", null)),
        null,
        null);
}
```

## Externalizing Metadata

Every string attribute on `@McpTool` (`name`, `title`, `description`) supports Spring's `${...}` property placeholder syntax, so long descriptions don't have to live inline on the annotation. See [Externalizing Annotation Metadata](externalizing-metadata.md).

## Error Handling

### Automatic Error Wrapping

Any exception thrown from a tool method is caught by the framework and returned as a `CallToolResult` with `isError=true`. The exception message is sent to the LLM as text content:

```java
@McpTool(name = "divide", description = "Divides two numbers")
public double divide(double a, double b) {
    if (b == 0) {
        throw new IllegalArgumentException("Cannot divide by zero");
    }
    return a / b;
}
```

If `b` is 0, the client receives:

```json
{
  "content": [{"type": "text", "text": "Cannot divide by zero"}],
  "isError": true
}
```

This follows the MCP specification's distinction:
- **Tool errors** (exceptions from your code) become `CallToolResult` with `isError=true` -- the LLM can see and react to the error
- **Protocol errors** (unknown tool, invalid parameters) become JSON-RPC errors -- these indicate a problem with the request itself

### Explicit Error Results

You can also return an error result explicitly:

```java
@McpTool(name = "validate", description = "Validates input")
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
