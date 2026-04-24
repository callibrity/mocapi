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

Tool return types are strictly checked at registration time (since 0.15.0) — the signature determines exactly how mocapi maps the value to a `CallToolResult`, with no runtime guessing. See [Permitted Return Types](#permitted-return-types) for the full rule set. Tools whose return type doesn't match one of the permitted shapes fail to register with a clear message at startup.

### Naming

If you omit the `name` attribute, the framework generates a name from the class and method names. For a class `MathTool` with method `add`, the generated name would be `math-tool.add`.

You can also set a `title` and `description`:

```java
@McpTool(
    name = "calculate",
    title = "Calculator",
    description = "Performs basic arithmetic operations")
public CalculatorResult calculate(String operation, double a, double b) { ... }

public record CalculatorResult(double value) {}
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

### Permitted Return Types

Mocapi accepts exactly four return-type shapes. Anything else is rejected at handler-build time with a message explaining which rule the signature violated. The rule is applied to the _effective_ return type — if the method returns `CompletionStage<X>` (or `CompletableFuture<X>`), mocapi unwraps one layer and applies the rules to `X`.

| Shape | Behavior | `outputSchema` |
|---|---|---|
| `void` / `Void` | Empty `CallToolResult` (text-only, no structured content) | — |
| `CallToolResult` | Author constructs the result manually; passed through as-is | — |
| `CharSequence` (typically `String`) | `toString()` becomes a single text content block; no structured content | — |
| Record/POJO whose derived JSON schema is `type: "object"` with declared properties | Jackson-serialized to `structuredContent` with a matching text block | Advertised to clients |

**Async** is supported by wrapping any of the above in `CompletionStage<T>` / `CompletableFuture<T>`. Mocapi awaits the future on an interceptor and applies the inner-type mapping.

**Rejected at registration** — anything else. Common mistakes and what to do instead:

| You wrote | Why it's rejected | Fix |
|---|---|---|
| `List<Widget>` / `Widget[]` | Serializes to a JSON array, not an object | Wrap in a record: `record Widgets(List<Widget> widgets) {}` |
| `Map<String, Widget>` | Serializes to an open-shape object — no declared properties | Wrap in a record with named fields |
| `int` / `double` / `boolean` | Non-object scalar | Wrap in a record |
| `Optional<Widget>` | Serializes to null-or-the-unwrapped-value | Wrap the nullable payload in a record |
| `JsonNode` / `ObjectNode` | No inferable schema for structured content | Return a record, or return `CallToolResult` |
| Raw `CompletionStage` / wildcard `CompletionStage<?>` | No type argument to unwrap | Parameterize the stage |
| Nested `CompletionStage<CompletionStage<X>>` | Only one async layer is awaited | Flatten via `thenCompose` |

### Structured Return (the common case)

Return a record or POJO. The framework serializes it to JSON, puts it in `structuredContent`, and also produces a text representation in `content`.

```java
// Returns {"message": "Hello!"} as structuredContent
@McpTool(name = "greet", description = "Greets someone")
public GreetResponse greet(String name) {
    return new GreetResponse("Hello, " + name + "!");
}

public record GreetResponse(String message) {}
```

### Text-Only Return

A tool that just wants to return a line of prose can return `String` (or any `CharSequence`). No structured content is produced and no output schema is advertised.

```java
@McpTool(name = "motd", description = "Returns the message of the day")
public String motd() {
    return "Be excellent to each other.";
}
```

### Void Tools

A tool that returns `void` (or `Void`) produces an empty `CallToolResult`. Useful for fire-and-forget operations with no meaningful return value.

```java
@McpTool(name = "notify", description = "Sends a notification")
public void sendNotification(String message) {
    notificationService.send(message);
}
```

### Returning CallToolResult Directly

For full control over the response — multiple content blocks, custom `isError`, or a structured payload whose shape mocapi can't (or shouldn't) derive — return a `CallToolResult`. No output schema is advertised in this case; the author owns the full shape of the result.

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

### Async Tools

A tool that does I/O or delegates to an async API can return `CompletionStage<T>` / `CompletableFuture<T>`. Mocapi awaits the future on the server thread and maps the awaited value using the same rules as for a synchronous return of `T`.

```java
@McpTool(name = "fetch-weather", description = "Fetches current weather asynchronously")
public CompletableFuture<WeatherResponse> fetchWeather(String city) {
    return weatherClient.fetchAsync(city)
        .thenApply(payload -> new WeatherResponse(city, payload.tempF(), payload.condition()));
}

public record WeatherResponse(String city, double temperature, String condition) {}
```

If the future completes exceptionally, the original exception surfaces with its type preserved (the JDK's `CompletionException` wrapper is unwrapped). Domain exceptions therefore flow through the same error-handling path as synchronously thrown ones.

## Custom Parameter Resolvers

The tool builder wires mocapi's own parameter resolvers by default — an `McpToolContext` resolver, an `@McpToolParams` deserializer, and a Jackson catch-all that deserializes named arguments from the request tree. You can layer your own resolver alongside those to bind bespoke parameter types (for example, a "current tenant" pulled from the session).

Write the resolver:

```java
public final class CurrentTenantResolver implements ParameterResolver<JsonNode> {
    @Override
    public boolean supports(ParameterInfo info) {
        return info.parameter().isAnnotationPresent(CurrentTenant.class)
                && info.resolvedType() == String.class;
    }

    @Override
    public Object resolve(ParameterInfo info, JsonNode arguments) {
        return McpSession.CURRENT.get().attribute("tenant");
    }
}
```

Attach it to every tool via a customizer bean:

```java
@Bean
CallToolHandlerCustomizer currentTenantResolverCustomizer() {
    CurrentTenantResolver resolver = new CurrentTenantResolver();
    return config -> config.resolver(resolver);
}
```

And declare it on a handler:

```java
@McpTool(name = "list_tenant_widgets")
public TenantWidgets listTenantWidgets(@CurrentTenant String tenant) {
    return new TenantWidgets(widgetService.listForTenant(tenant));
}

public record TenantWidgets(List<Widget> widgets) {}
```

A bare `List<Widget>` would be rejected at registration — MCP requires `structuredContent` to be a JSON object, not an array, so the payload is wrapped in a record.

Ordering: user resolvers are placed ahead of the catch-all Jackson resolver, so a specific `supports()` check always wins over generic JSON deserialization. Resolver selection within Methodical is first-match-wins.

## Externalizing Metadata

Every string attribute on `@McpTool` (`name`, `title`, `description`) supports Spring's `${...}` property placeholder syntax, so long descriptions don't have to live inline on the annotation. See [Externalizing Annotation Metadata](externalizing-metadata.md).

## Error Handling

### Automatic Error Wrapping

Any exception thrown from a tool method is caught by the framework and returned as a `CallToolResult` with `isError=true`. The exception message is sent to the LLM as text content:

```java
@McpTool(name = "divide", description = "Divides two numbers")
public DivideResult divide(double a, double b) {
    if (b == 0) {
        throw new IllegalArgumentException("Cannot divide by zero");
    }
    return new DivideResult(a / b);
}

public record DivideResult(double quotient) {}
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

### Rich Error Results with `McpToolException`

Plain exceptions only give you a text message. For tool errors that should carry
**structured machine-readable detail** — error categories, domain-specific codes,
or extra content blocks — throw `McpToolException` (from `mocapi-api`). Mocapi
catches it and produces a `CallToolResult` with `isError: true` whose
`structuredContent` is your serialized error payload and whose `content` starts
with the exception message.

```java
import com.callibrity.mocapi.api.tools.McpToolException;

@McpTool(name = "get-user", description = "Looks up a user by id")
public UserResponse getUser(String id) {
    return userRepository.findById(id)
        .map(UserResponse::from)
        .orElseThrow(() -> new McpToolException(
            "User not found: " + id,
            new UserNotFoundDetails("USER_NOT_FOUND", id)));
}

public record UserNotFoundDetails(String code, String userId) {}
```

The client receives:

```json
{
  "content": [{"type": "text", "text": "User not found: 42"}],
  "isError": true,
  "structuredContent": { "code": "USER_NOT_FOUND", "userId": "42" }
}
```

**Note on error codes.** MCP has no dedicated "error code" slot on
`CallToolResult` — the only machine-readable error signal per spec is the
`isError` flag. If you want to communicate a code or category to the client,
put it inside `structuredContent`, as in the example above.

**Structured content must serialize to a JSON object.** Mocapi validates this
at catch time: passing a `String`, `List`, number, or any non-object payload
fails the request with a diagnostic error message rather than silently dropping
the `structuredContent` field. Wrap your error data in a record or POJO.

#### Subclassing for reusable error shapes

Define domain-specific subclasses for errors you throw from more than one
place. Mocapi catches the parent type, so every subclass is handled uniformly.

```java
public class UserNotFoundException extends McpToolException {
    private final String userId;

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }

    @Override
    public Object getStructuredContent() {
        return new Details("USER_NOT_FOUND", userId);
    }

    public record Details(String code, String userId) {}
}
```

Now any tool can just `throw new UserNotFoundException(id)` and get the same
structured error shape on the wire.

#### Appending extra content blocks

Override `getAdditionalContent()` to append extra content blocks (images,
embedded resources, extra text) after the automatically-generated message
block:

```java
throw new McpToolException("Validation failed") {
    @Override
    public List<ContentBlock> getAdditionalContent() {
        return List.of(new TextContent("See https://example.com/docs/validation", null));
    }
};
```

#### Async

Works the same way inside a `CompletionStage` / `CompletableFuture` that
completes exceptionally — the await interceptor unwraps the JDK's
`CompletionException` so the original `McpToolException` reaches the error path
unchanged.

```java
@McpTool(name = "get-user-async", description = "Async user lookup")
public CompletableFuture<UserResponse> getUserAsync(String id) {
    return userClient.fetchAsync(id).thenApply(maybe -> maybe.orElseThrow(() ->
        new UserNotFoundException(id)));
}
```

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
