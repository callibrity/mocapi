# Architecture

## Overview

Mocapi separates protocol concerns from transport concerns:

- **mocapi-api** -- user-facing annotations and interfaces. Tool authors depend on this.
- **mocapi-model** -- MCP protocol types (Tool, CallToolResult, ElicitResult, etc.)
- **mocapi-server** -- stateful MCP server: session management, JSON-RPC dispatch, tool invocation, initialization lifecycle
- **mocapi-transport-streamable-http** -- Streamable HTTP transport: HTTP endpoints, SSE streaming, encrypted event IDs

The server knows nothing about HTTP. The transport knows nothing about sessions or tools. The `McpServer` interface is the contract between them.

## Request Flow

```
Client HTTP Request
    |
    v
StreamableHttpController (transport)
    |-- validates Accept, Origin, Content-Type
    |-- for non-initialize: calls server.createContext(sessionId, protocolVersion)
    |-- maps McpContextResult errors to HTTP status codes
    |-- for valid contexts: delegates to server.handleCall/handleNotification/handleResponse
    |
    v
DefaultMcpServer (protocol)
    |-- binds McpSession and McpTransport as ScopedValues
    |-- dispatches to RipCurl JSON-RPC dispatcher
    |-- RipCurl routes to @JsonRpcMethod handlers
    |
    v
McpToolsService / McpPromptsService / McpResourcesService / etc.
    |-- validates input schema
    |-- invokes tool method via Methodical
    |-- wraps result in CallToolResult
```

## McpContext and Validation

The transport asks the server to create a context before dispatching:

```java
McpContextResult result = server.createContext(sessionId, protocolVersion);
```

The server returns a sealed type:

```java
sealed interface McpContextResult {
    record ValidContext(McpContext context) {}
    record SessionIdRequired(int code, String message) {}
    record SessionNotFound(int code, String message) {}
    record ProtocolVersionMismatch(int code, String message) {}
}
```

The transport maps each variant to its native error format. For Streamable HTTP:

| Result | HTTP Status | JSON-RPC Code |
|--------|-------------|---------------|
| `ValidContext` | (proceed) | -- |
| `SessionIdRequired` | 400 | -32000 |
| `SessionNotFound` | 404 | -32001 |
| `ProtocolVersionMismatch` | 400 | -32000 |

A stdio transport would handle these differently (e.g., log and close the pipe).

The `McpContext` carries the resolved session:

```java
interface McpContext {
    String sessionId();
    String protocolVersion();
    Optional<McpSession> session();
}
```

For `initialize` requests (which have no session), the transport uses `McpContext.empty()`.

## Session Lifecycle

1. Client sends `initialize` -- server creates a session, returns session ID in response header
2. Client sends `notifications/initialized` -- server marks session as initialized
3. Between steps 1 and 2, only `ping` is allowed (per MCP spec)
4. Normal operations proceed
5. Client sends HTTP DELETE -- server terminates the session
6. Subsequent requests with that session ID get HTTP 404

Sessions are stored in a pluggable `McpSessionStore` backed by Substrate's Atom SPI. Each session has a configurable TTL that is refreshed on access.

## Transport

Every `JsonRpcCall` POST runs on a virtual thread through `StreamableHttpTransport`, which chooses JSON vs SSE based on the first outbound message:

1. Controller creates a `StreamableHttpTransport` with an `SseStream` supplier and spawns a virtual thread to run `server.handleCall()`
2. The transport holds a `MessageWriter` state machine starting in `DirectMessageWriter`
3. First `send()` decides the response shape:
   - `JsonRpcResponse` → commit as `application/json` body, transition to `ClosedMessageWriter`
   - `JsonRpcRequest` (notification or server-initiated call) → pull an `SseStream` from the supplier, commit `text/event-stream` body, transition to `SseMessageWriter`
4. Subsequent `send()` calls on `SseMessageWriter` publish to the stream; a terminal `JsonRpcResponse` closes the stream and transitions to `ClosedMessageWriter`
5. `ClosedMessageWriter` rejects any further writes

Simple tools that only return a response get JSON — no unnecessary SSE upgrade. Tools that emit progress/log notifications or issue sampling/elicitation requests upgrade lazily when the first notification or server-initiated call is sent.

### Encrypted Event IDs

SSE event IDs are encrypted using AES-256-GCM with the session ID as context. This prevents cross-session event enumeration and forged resumption requests. Encryption lives in `DefaultSseStreamFactory`; the controller and transport have no direct knowledge of cipher details.

### GET SSE Streams

Clients can open a GET connection to receive server-initiated messages on a session-scoped stream (`SseStreamFactory.sessionStream(context)`, named by session ID). A `Last-Event-ID` header on reconnect routes through `SseStreamFactory.resumeStream(context, lastEventId)`, which decodes the stream name and event ID from the encrypted token.

## Server Capabilities

The server declares static capabilities during initialization:

- **tools** -- tool listing and invocation
- **prompts** -- prompt listing and retrieval
- **resources** -- resource listing, reading, and template expansion
- **logging** -- log level management

Capabilities are built as a `ServerCapabilities` bean in auto-configuration and passed to `McpSessionService`. They describe what the framework supports, not what is currently registered. A server with no tools still declares `tools` capability.

## ScopedValue Pattern

The server uses Java's `ScopedValue` to bind request-scoped context:

- `McpSession.CURRENT` -- the current session
- `McpTransport.CURRENT` -- the current transport
- `McpToolContext.CURRENT` -- the tool execution context

These are resolved as method parameters via `ScopedValueResolver<T>` subclasses:

- `McpSessionResolver` -- resolves `McpSession` parameters
- `McpTransportResolver` -- resolves `McpTransport` parameters
- `McpToolContextResolver` -- resolves `McpToolContext` parameters

## What Mocapi Does Not Implement

The MCP specification defines features that Mocapi intentionally does not implement. This section documents each omission and the rationale.

### Completions (completion/complete)

**Spec reference:** [Server / Completions](https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/completion)

The completions feature provides autocomplete suggestions for prompt arguments and resource template URIs. Mocapi does not declare the `completions` capability and does not register a handler. Clients that call `completion/complete` receive a JSON-RPC "method not found" error.

**Rationale:** The client already has the JSON Schema for tool parameters and prompt arguments. Autocomplete can be implemented client-side from the schema. Server-side completions add complexity for marginal value.

### Resource Subscriptions (resources/subscribe, resources/unsubscribe)

**Spec reference:** [Server / Resources](https://modelcontextprotocol.io/specification/2025-11-25/server/resources)

Mocapi declares `resources` capability with `subscribe: false`. The `resources/subscribe` and `resources/unsubscribe` methods are not registered.

**Rationale:** Resource subscriptions require the server to push notifications when resource content changes. This adds significant complexity (change detection, subscriber tracking) for a feature that most MCP use cases don't need. Resources are available for listing and reading.

### URL-Mode Elicitation

**Spec reference:** [Client / Elicitation (URL Mode)](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation)

Mocapi supports form-mode elicitation (`elicitation/create` with `mode: "form"`). URL-mode elicitation (`mode: "url"`) and the `URLElicitationRequiredError` (code -32042) are not implemented.

**Rationale:** URL-mode elicitation is marked as a new feature in the 2025-11-25 spec with the caveat "its design and implementation may change in future protocol revisions." It involves out-of-band browser interactions and OAuth flows that are significantly more complex than form-mode. Mocapi will revisit when the spec stabilizes.

### JSON-RPC Batching

**Spec reference:** [Transports / Streamable HTTP](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports/streamable-http)

The MCP spec explicitly states the POST body "MUST be a single JSON-RPC request, notification, or response." JSON-RPC batching (arrays of messages) is **prohibited** by the spec.

Note: the official TypeScript SDK supports batching despite the spec prohibition. Mocapi follows the spec.

### Cancellation Processing (notifications/cancelled)

**Spec reference:** [Utilities / Cancellation](https://modelcontextprotocol.io/specification/2025-11-25/basic/utilities/cancellation)

Mocapi accepts `notifications/cancelled` and logs it, but does not attempt to interrupt in-flight tool execution. The spec says receivers SHOULD stop processing but MAY ignore cancellation if "the request cannot be cancelled." Since tool methods run on virtual threads without cooperative cancellation, interrupting them safely is not feasible without tool author cooperation.

The server does send `notifications/cancelled` to the client when a `sendAndAwait` call (elicitation or sampling) times out.

### List Change Notifications

**Spec reference:** [Server / Tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools), [Server / Resources](https://modelcontextprotocol.io/specification/2025-11-25/server/resources), [Server / Prompts](https://modelcontextprotocol.io/specification/2025-11-25/server/prompts)

Mocapi declares `listChanged: false` for tools, prompts, and resources. The server does not send `notifications/tools/list_changed`, `notifications/resources/list_changed`, or `notifications/prompts/list_changed`.

**Rationale:** Mocapi discovers tools, prompts, and resources at application startup. Dynamic registration at runtime is not currently supported. When it is, `listChanged` will be enabled.

### Roots (roots/list, notifications/roots/list_changed)

**Spec reference:** [Client / Roots](https://modelcontextprotocol.io/specification/2025-11-25/client/roots)

Mocapi accepts the `notifications/roots/list_changed` notification (logs and ignores it) but does not call `roots/list` or use root information.

**Rationale:** Roots provide filesystem context hints to the server. Mocapi's tool-oriented architecture does not currently use filesystem context.

### Stateless / Serverless Mode

The TypeScript SDK supports a stateless mode (no session IDs, no session store) for serverless deployments. Mocapi always creates sessions and requires a session store.

**Rationale:** Mocapi is designed for stateful, multi-node deployments where sessions, elicitation, and sampling require durable state. A stateless server variant may be added in the future.
