# Validation

Mocapi ships an optional Spring Boot starter, `mocapi-jakarta-validation-spring-boot-starter`, that turns on Jakarta Bean Validation across mocapi's reflective-dispatch surface. Once the starter is on the classpath, `@NotBlank`/`@Size`/`@Pattern`/etc. annotations on user `@McpTool`, `@McpPrompt`, and `@McpResourceTemplate` parameters are enforced at runtime, and violations surface in the MCP-spec-idiomatic shape for each handler type.

Mocapi's internal protocol handlers deliberately do *not* use jakarta validation — they rely on hand-rolled checks so mocapi's own contract is enforced regardless of whether the consumer has opted into validation. This starter is user-code-only.

## Getting started

Add the starter alongside whatever transport starter you're already using:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-streamable-http-spring-boot-starter</artifactId>
    <version>${mocapi.version}</version>
</dependency>
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-jakarta-validation-spring-boot-starter</artifactId>
    <version>${mocapi.version}</version>
</dependency>
```

The starter is a pom-only aggregator that pulls three pieces:

- `spring-boot-starter-validation` — Hibernate Validator + Jakarta EL + the `jakarta.validation-api`
- `methodical-jakarta-validation` — wires a `MethodValidatorFactory` bean that methodical's invoker uses to run `executableValidator.validateParameters(...)` before each dispatch
- `ripcurl-jakarta-validation` — provides a `ConstraintViolationExceptionTranslator` that converts jakarta `ConstraintViolationException` to JSON-RPC `-32602 Invalid params` with per-violation detail

No Spring configuration is needed beyond adding the dependency. All wiring is autoconfig-driven.

## Annotating your handlers

```java
@Component
@ToolService
public class GreetTool {
    @McpTool(name = "greet", description = "Returns a greeting")
    public GreetResponse greet(@NotBlank @Size(min = 2, max = 60) String name) {
        return new GreetResponse("Hello, " + name + "!");
    }
    public record GreetResponse(String message) {}
}

@Component
@PromptService
public class SummarizePrompt {
    @McpPrompt(name = "summarize", description = "Summarizes text")
    public GetPromptResult summarize(@NotBlank @Size(min = 10, max = 2000) String text) {
        return new GetPromptResult("summarize", List.of(new PromptMessage(Role.USER,
            new TextContent("Please summarize:\n\n" + text, null))));
    }
}

@Component
@ResourceService
public class ConfigResources {
    @McpResourceTemplate(uriTemplate = "config://{env}/app", name = "Per-env config", mimeType = "text/plain")
    public ReadResourceResult config(@Pattern(regexp = "^[a-z]+$") String env) {
        return new ReadResourceResult(List.of(
            new TextResourceContents("config://" + env + "/app", "text/plain", "env=" + env)));
    }
}
```

## Error shapes

Violations surface differently per handler type, matching the [MCP 2025-11-25 specification](https://modelcontextprotocol.io/specification/2025-11-25):

### `tools/call` → `CallToolResult.isError = true`

The MCP spec is explicit: "Input validation errors (e.g., date in wrong format, value out of range)" belong in the result body with `isError: true` so the calling LLM can read the message and retry with adjusted arguments. This is *not* a JSON-RPC error.

```http
POST /mcp
{"jsonrpc":"2.0","id":1,"method":"tools/call",
 "params":{"name":"greet","arguments":{"name":""}}}
```

```json
{"jsonrpc":"2.0","id":1,
 "result":{
   "content":[{"type":"text",
     "text":"greet.name: must not be blank, greet.name: size must be between 2 and 60"}],
   "isError":true}}
```

Mocapi's existing catch block in `McpToolsService.invokeTool` handles this; the starter requires no mocapi-side code changes.

### `prompts/get` → `-32602 Invalid params`

The MCP spec explicitly maps "Missing required arguments" to `-32602`. Violations surface as JSON-RPC errors with per-violation detail in the response's `data` array:

```http
POST /mcp
{"jsonrpc":"2.0","id":1,"method":"prompts/get",
 "params":{"name":"summarize","arguments":{"text":""}}}
```

```json
{"jsonrpc":"2.0","id":1,
 "error":{"code":-32602,"message":"Invalid params",
   "data":[
     {"field":"summarize.text","message":"must not be blank"},
     {"field":"summarize.text","message":"size must be between 10 and 2000"}
   ]}}
```

### `resources/read` → `-32602 Invalid params`

Same JSON-RPC error shape as prompts. The MCP spec doesn't explicitly enumerate `-32602` for resource argument validation (it lists `-32002` for "Resource not found" and `-32603` for internal errors), but `-32602` is the standard JSON-RPC code for invalid params and the spec doesn't forbid it.

```http
POST /mcp
{"jsonrpc":"2.0","id":1,"method":"resources/read",
 "params":{"uri":"config://DEV/app"}}
```

```json
{"jsonrpc":"2.0","id":1,
 "error":{"code":-32602,"message":"Invalid params",
   "data":[{"field":"config.env","message":"must match \"^[a-z]+$\""}]}}
```

## `data` field contract

Every entry in the JSON-RPC error's `data` array is an object with two string fields:

- `field` — the jakarta property path (method name + parameter, e.g. `summarize.text`)
- `message` — the constraint's configured message

The rejected input value is deliberately *omitted*. Reflecting it back can leak secrets (passwords, tokens, PII) through error responses; clients that need the input should capture it at the call site rather than rely on the server echoing it.

## Runnable example

See [`examples/jakarta-validation`](../examples/jakarta-validation) for a minimal streamable-http app showing all three handler types with jakarta constraints. Run with `mvn spring-boot:run` after `mvn install` at the repo root.

## Not covered

- **Mocapi's own protocol records.** Mocapi-server uses hand-rolled validation on its internal JSON-RPC request/response types (`CallToolRequestParams`, `InitializeRequest`, etc.) so mocapi's contract is always enforced, whether or not the consumer adds this starter.
- **Tools where `@NotBlank`/`@Size` apply to fields of a typed parameter record.** In that case mocapi's existing JSON-schema pipeline (via `jsonschema-module-jakarta-validation`, a compile dep of `mocapi-server`) may surface violations as `-32602` before the runtime validator runs. Behavior depends on whether your parameter is a raw primitive/String or a record field. Run the integration tests in `mocapi-jakarta-validation-spring-boot-starter` for the authoritative behavior map.
