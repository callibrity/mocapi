# Create mocapi-client module with JDK HttpClient transport

## What to build

A new `mocapi-client` module providing a Java MCP client that speaks the full
MCP 2025-11-25 protocol over Streamable HTTP using the JDK's built-in `HttpClient`.

### Module: mocapi-client

**Maven coordinates:** `com.callibrity.mocapi:mocapi-client`

**Dependencies:**
- `mocapi-model` (MCP types)
- `tools.jackson.databind` (JSON serialization)
- JDK `java.net.http` (no external HTTP library)

**No Spring dependency.** This is a plain Java library.

### McpClient

The main entry point. Built via a fluent builder:

```java
var client = McpClient.builder()
    .url("http://localhost:8080/mcp")
    .elicitationHandler(params -> handleElicitation(params))
    .samplingHandler(params -> handleSampling(params))
    .build();

client.initialize();
var tools = client.listTools();
var result = client.callTool("hello", Map.of("name", "World"));
client.close();
```

If handlers are not supplied, the corresponding capability is not declared during
initialization.

### Builder API

```java
McpClient.builder()
    .url(String url)                              // required
    .httpClient(HttpClient httpClient)             // optional, default: HttpClient.newHttpClient()
    .objectMapper(ObjectMapper mapper)             // optional, default: new ObjectMapper()
    .elicitationHandler(ElicitationHandler handler)// optional
    .samplingHandler(SamplingHandler handler)      // optional
    .requestTimeout(Duration timeout)              // optional, default: 30s
    .build()
```

### Handler interfaces

```java
@FunctionalInterface
public interface ElicitationHandler {
    ElicitResult handle(ElicitRequestFormParams params);
}

@FunctionalInterface
public interface SamplingHandler {
    CreateMessageResult handle(CreateMessageRequestParams params);
}
```

### Client API methods

```java
public interface McpClient extends AutoCloseable {

    // Lifecycle
    InitializeResult initialize();
    void close();  // sends DELETE, closes SSE stream

    // Tools
    ListToolsResult listTools();
    ListToolsResult listTools(String cursor);
    CallToolResult callTool(String name, Map<String, Object> arguments);
    CallToolResult callTool(String name, ObjectNode arguments);
    CallToolResult callTool(CallToolRequestParams params);

    // Prompts
    ListPromptsResult listPrompts();
    ListPromptsResult listPrompts(String cursor);
    GetPromptResult getPrompt(String name, Map<String, String> arguments);

    // Resources
    ListResourcesResult listResources();
    ListResourcesResult listResources(String cursor);
    ListResourceTemplatesResult listResourceTemplates();
    ReadResourceResult readResource(String uri);

    // Logging
    void setLogLevel(LoggingLevel level);

    // Ping
    void ping();
}
```

### HTTP Transport Implementation

All communication uses the JDK `HttpClient`:

**POST (calls):**
- Send JSON-RPC call as POST body with `Content-Type: application/json`
- `Accept: application/json, text/event-stream`
- Include `MCP-Session-Id` header (after initialization)
- Include `MCP-Protocol-Version` header (after initialization)
- Parse response: if `Content-Type` is `application/json`, parse directly.
  If `text/event-stream`, consume SSE events, collect the final `JsonRpcResponse`.

**POST (notifications):**
- Fire-and-forget, expect 202

**GET (SSE stream):**
- Open a long-lived connection for server-initiated messages
- Parse SSE events, dispatch to handlers (elicitation, sampling)
- Support `Last-Event-ID` for reconnection
- Run on a background virtual thread

**DELETE:**
- Send DELETE with session ID, expect 204/200

### SSE Consumption

The JDK HttpClient supports streaming responses via `HttpResponse.BodyHandlers.ofLines()`
or `ofInputStream()`. Use one of these to consume SSE events line by line.

SSE event parsing:
- Lines starting with `data:` contain JSON-RPC messages
- Lines starting with `id:` contain event IDs (store for reconnection)
- Empty lines delimit events
- Lines starting with `retry:` set reconnection interval

### Server-to-Client Request Handling

When the server sends `elicitation/create` or `sampling/createMessage` on the GET
SSE stream, the client:
1. Parses the `JsonRpcCall`
2. Dispatches to the appropriate handler
3. Sends the response as a POST with the `JsonRpcResult`

This runs on the background SSE thread.

### Session Management

- `initialize()` sends the initialize call, stores the session ID from the
  `MCP-Session-Id` response header, sends `notifications/initialized`
- All subsequent requests include the session ID header
- `close()` sends DELETE and closes the GET SSE stream

### Capability Negotiation

Client capabilities are built from the supplied handlers:

```java
{
    "elicitation": {},     // only if elicitationHandler is set
    "sampling": {},        // only if samplingHandler is set
}
```

### Error Handling

- HTTP 400 → throw `McpClientException` with message
- HTTP 404 → throw `SessionExpiredException`
- JSON-RPC error in response → throw `McpClientException` with code and message
- Connection errors → throw `McpConnectionException`

### Thread Model

- `initialize()`, `listTools()`, `callTool()`, etc. are synchronous blocking calls
- The GET SSE stream runs on a background virtual thread
- Server-to-client requests (elicitation/sampling) are dispatched on the SSE thread
  — handlers should be fast or delegate to their own thread

## Package Structure

```
com.callibrity.mocapi.client
    McpClient           — main client interface
    McpClientBuilder    — fluent builder
    DefaultMcpClient    — implementation
    ElicitationHandler  — functional interface
    SamplingHandler     — functional interface
    McpClientException  — unchecked exception for protocol errors
    SessionExpiredException — unchecked, HTTP 404
    McpConnectionException  — unchecked, connection failures
    SseEventParser      — parses SSE stream into events
```

## Testing

The client should have unit tests using a mock HTTP server (e.g., MockWebServer
from OkHttp, or JDK's `com.sun.net.httpserver.HttpServer` for lightweight testing).

The compat tests can then be rewritten to use `McpClient` instead of `McpClient`
(the MockMvc-based one). This eliminates all MockMvc SSE parsing hacks.

## Acceptance criteria

- [ ] `mocapi-client` module exists with its own POM
- [ ] `McpClient` interface with builder
- [ ] Full initialize/tools/prompts/resources/logging/ping API
- [ ] JDK HttpClient transport (POST, GET SSE, DELETE)
- [ ] SSE event parsing and server-to-client request dispatch
- [ ] Elicitation and sampling handler support
- [ ] Session management (ID tracking, header inclusion)
- [ ] Capability negotiation based on supplied handlers
- [ ] Unit tests for client protocol logic
- [ ] No Spring dependency

## Implementation notes

- Start with the synchronous call paths (initialize, listTools, callTool, ping)
- Add GET SSE stream handling next
- Add elicitation/sampling dispatch last
- The client can be used standalone (no Spring) or in Spring tests
- JDK HttpClient is available since Java 11, well within our Java 25 baseline
- Use `HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)` to ensure
  compatibility with SSE (HTTP/2 can cause issues with streaming)
- For SSE parsing, implement a simple line-based parser — no external library needed
- The `callTool` method should handle both JSON and SSE responses transparently
