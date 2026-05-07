# Architecture Overview

How mocapi is layered, how requests flow through it, and the
ScopedValue model that ties request-scoped state to handler code.

For deeper coverage of specific concerns, see:

- [Transports](transports.md) — Streamable HTTP and stdio specifics
- [Session & Context](session-and-context.md) — `McpContext`, validation results, session lifecycle
- [Storage & Substrate](storage-and-substrate.md) — pluggable session/mailbox/journal/notifier
- [Extension SPI](extension-spi.md) — customizer model, interceptor strata
- [Handlers](handlers.md) — internal handler classes
- [Observability Stack](observability-stack.md) — logging / o11y / audit / actuator

For decisions that drove the structure here, see [ADR-0001](../adr/0001-module-structure-and-packaging.md)
and [ADR-0002](../adr/0002-protocol-transport-contract.md).

## Module layering

Mocapi separates protocol concerns from transport concerns:

- **mocapi-api** — user-facing annotations and interfaces. Tool authors depend on this.
- **mocapi-model** — MCP protocol types (`Tool`, `CallToolResult`, `ElicitResult`, etc.) translated 1:1 from the official `schema.ts` (see [ADR-0014](../adr/0014-mocapi-model-from-schema-ts.md)).
- **mocapi-server** — stateful MCP server: session management, JSON-RPC dispatch, tool invocation, initialization lifecycle.
- **mocapi-streamable-http-transport** — Streamable HTTP transport: HTTP endpoints, SSE streaming, encrypted event IDs.
- **mocapi-stdio-transport** — Stdio transport: newline-delimited JSON-RPC on stdin/stdout, for subprocess-launched MCP clients.
- **mocapi-streamable-http-spring-boot-starter** — Spring Boot starter bundling `mocapi-server` + Streamable HTTP transport.
- **mocapi-stdio-spring-boot-starter** — Spring Boot starter bundling `mocapi-server` + stdio transport.

The server knows nothing about HTTP or stdio. Transports know nothing
about sessions or tools. The `McpServer` + `McpTransport` interface
pair is the contract between them — the server calls
`transport.send(message)` / `transport.emit(event)`, and the transport
calls back into `server.handleCall` / `handleNotification` /
`handleResponse`. Two transports ship today; any future transport
(Unix socket, WebSocket, named pipe, etc.) drops into the same
contract.

## Request flow

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

The same flow applies to stdio, with `StdioServer` / `StdioTransport`
in place of the controller (see [Transports — Stdio](transports.md#stdio)).

## Server capabilities

The server declares static capabilities during initialization:

- **tools** — tool listing and invocation
- **prompts** — prompt listing and retrieval
- **resources** — resource listing, reading, and template expansion
- **logging** — log level management

Capabilities are built as a `ServerCapabilities` bean in
auto-configuration and passed to `McpSessionService`. They describe
what the framework supports, not what is currently registered. A
server with no tools still declares `tools` capability.

The capability bits for subscription, list-change notifications, etc.
are advertised as `false`; see
[ADR-0018](../adr/0018-mcp-spec-features-not-implemented.md) for the
deliberate non-implementations.

## Startup logging

Every tool, prompt, resource, and resource-template bean is discovered
during `@PostConstruct` of its provider. Each discovery is logged at
`INFO` level so startup output shows exactly what was wired in —
useful for confirming that a newly added bean got picked up without
having to hit the server.

Log line patterns, by provider:

```
# Tools
INFO  c.c.m.s.a.MocapiServerToolsAutoConfiguration -- Registered MCP tool: "greet" (bean "greetingTool")

# Prompts
INFO  c.c.m.s.a.MocapiServerPromptsAutoConfiguration -- Registered MCP prompt: "summarize" (bean "summarizationPrompts")
INFO  c.c.m.s.a.MocapiServerPromptsAutoConfiguration -- 	Registered completions for argument "detail": [BRIEF, STANDARD, DETAILED]

# Resources
INFO  c.c.m.s.a.MocapiServerResourcesAutoConfiguration -- Registered MCP resource: "docs://readme" (bean "docResources")
INFO  c.c.m.s.a.MocapiServerResourcesAutoConfiguration -- Registered MCP resource template: "env://{stage}/config" (bean "docResources")
INFO  c.c.m.s.a.MocapiServerResourcesAutoConfiguration -- 	Registered completions for variable "stage": [DEV, STAGE, PROD]
```

The nested "Registered completions for argument/variable" line only
appears when the parameter is an enum type or carries
`@Schema(allowableValues = {...})` — otherwise there are no candidate
values to register.

For the stdio transport, these log lines go to **stderr** (the stdio
example's `logback-spring.xml` routes the root logger to `System.err`
so they don't corrupt the protocol stream on stdout).

## ScopedValue pattern

The server uses Java's `ScopedValue` to bind request-scoped context:

- `McpSession.CURRENT` — the current session
- `McpTransport.CURRENT` — the current transport
- `McpToolContext.CURRENT` — the tool execution context

These are resolved as method parameters via `ScopedValueResolver<T>`
subclasses:

- `McpSessionResolver` — resolves `McpSession` parameters
- `McpTransportResolver` — resolves `McpTransport` parameters
- `McpToolContextResolver` — resolves `McpToolContext` parameters

For thread-local-style state that lives outside ScopedValue (Spring
Security's `SecurityContextHolder`, Micrometer observation scope,
SLF4J MDC), see the context-propagation pattern documented in
[Transports — Thread-local context propagation](transports.md#thread-local-context-propagation)
and [ADR-0006](../adr/0006-virtual-thread-per-call.md).

## What mocapi does not implement

The MCP specification defines several features mocapi deliberately does
not implement: resource subscriptions, URL-mode elicitation, JSON-RPC
batching, cancellation processing, list-change notifications, roots,
and stateless mode. Each omission has a stated rationale.

See [ADR-0018](../adr/0018-mcp-spec-features-not-implemented.md) for
the full list and reasoning.
