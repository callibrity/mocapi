# Architecture Overview

How mocapi is layered, how requests flow through it, and the
ScopedValue model that ties request-scoped state to handler code.
Since MCP 2026-07-28 ([ADR-0019](../adr/0019-clean-break-2026-07-28.md),
[ADR-0020](../adr/0020-stateless-request-model.md)) the server is
stateless: every request is self-contained, and there are no sessions
anywhere in the stack.

For deeper coverage of specific concerns, see:

- [Transports](transports.md) — Streamable HTTP and stdio specifics
- [Elicitation — MRTR Replay](elicitation-mrtr.md) — multi-round-trip elicitation
- [Extension SPI](extension-spi.md) — customizer model, interceptor strata
- [Handlers](handlers.md) — internal handler classes
- [Observability Stack](observability-stack.md) — logging / o11y / audit / actuator

For decisions that drove the structure here, see [ADR-0001](../adr/0001-module-structure-and-packaging.md)
and [ADR-0002](../adr/0002-protocol-transport-contract.md).

## Module layering

Mocapi separates protocol concerns from transport concerns:

- **mocapi-api** — user-facing annotations and interfaces. Tool authors depend on this.
- **mocapi-model** — MCP protocol types (`Tool`, `CallToolResult`, `ElicitResult`, etc.) translated 1:1 from the official `schema.ts` (see [ADR-0014](../adr/0014-mocapi-model-from-schema-ts.md)).
- **mocapi-server** — stateless MCP server: `_meta` envelope parsing, JSON-RPC dispatch, tool invocation, `server/discover`, the MRTR elicitation replay engine.
- **mocapi-streamable-http-transport** — Streamable HTTP transport: the POST-only MCP endpoint, routing-header validation, per-request SSE response streams.
- **mocapi-stdio-transport** — Stdio transport: newline-delimited JSON-RPC on stdin/stdout, for subprocess-launched MCP clients.
- **mocapi-streamable-http-spring-boot-starter** — Spring Boot starter bundling `mocapi-server` + Streamable HTTP transport.
- **mocapi-stdio-spring-boot-starter** — Spring Boot starter bundling `mocapi-server` + stdio transport.

The server knows nothing about HTTP or stdio. Transports know nothing
about tools. The `McpServer` + `McpTransport` interface pair is the
contract between them — the transport calls `server.handleCall` /
`server.handleNotification`, and the server replies through
`transport.send(message)` on the response channel of the request being
handled. There is no server-initiated channel: MCP 2026-07-28 has no
server→client requests (see
[Elicitation — MRTR Replay](elicitation-mrtr.md)). Two transports ship
today; any future transport (Unix socket, WebSocket, named pipe, etc.)
drops into the same contract.

## Request flow

```
Client HTTP POST
    |
    v
StreamableHttpController (transport)
    |-- validates Accept, Origin, Content-Type
    |-- validates routing headers (MCP-Protocol-Version, Mcp-Method, Mcp-Name)
    |       against the body — mismatch → 400 / -32020 HeaderMismatch
    |-- delegates to server.handleCall / handleNotification
    |-- maps JSON-RPC error codes to HTTP status (single mapping table)
    |
    v
DefaultMcpServer (protocol)
    |-- parses the _meta envelope (protocolVersion, clientCapabilities
    |       required; clientInfo optional) → McpExchange;
    |       missing/malformed → -32602, unsupported version → -32022
    |       with the supported-version list
    |-- binds McpExchange and McpTransport as ScopedValues
    |-- dispatches to RipCurl JSON-RPC dispatcher
    |-- RipCurl routes to @JsonRpcMethod handlers
    |
    v
McpToolsService / McpPromptsService / McpResourcesService / etc.
    |-- validates input schema
    |-- invokes tool method via Methodical
    |-- wraps result in CallToolResult (resultType: "complete")
    |       — or InputRequiredResult when an elicitation is pending (MRTR)
    |
    v
DefaultMcpServer (protocol)
    |-- on every successful result, stamps
    |       _meta["io.modelcontextprotocol/serverInfo"] if absent
    |       (opt-out: mocapi.emit-server-info, default true) — see
    |       ADR-0026
```

The same flow applies to stdio, with `StdioServer` / `StdioTransport`
in place of the controller (see [Transports — Stdio](transports.md#stdio)).
There is no handshake: `server/discover` is an ordinary request that may
be sent at any time (and serves as the version probe — an unsupported
version in its envelope earns `-32022` carrying the server's supported
list).

## Server capabilities

The server's static capabilities are advertised via `server/discover`:

- **tools** — tool listing and invocation
- **prompts** — prompt listing and retrieval
- **resources** — resource listing, reading, and template expansion
- **completions** — argument completion
- **extensions** — advertised as an empty map (no extensions implemented)

Capabilities are built as a `ServerCapabilities` bean in
auto-configuration and served by `DiscoverHandler`. They describe what
the framework supports, not what is currently registered. A server with
no tools still declares `tools` capability. The deprecated `logging`
capability is not advertised ([ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md)).

The capability bits for list-change notifications are advertised as
`false`; see ADR-0022 for the deliberate non-implementations.

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

- `McpExchange.CURRENT` — the per-request protocol context parsed from
  the `_meta` envelope (protocol version, client info, client
  capabilities, trace context)
- `McpTransport.CURRENT` — the current transport
- `McpToolContext.CURRENT` — the tool execution context

These are resolved as method parameters via `ScopedValueResolver<T>`
subclasses (e.g. `McpTransportResolver`, `McpToolContextResolver`).

For thread-local-style state that lives outside ScopedValue (Spring
Security's `SecurityContextHolder`, Micrometer observation scope,
SLF4J MDC), see the context-propagation pattern documented in
[Transports — Thread-local context propagation](transports.md#thread-local-context-propagation)
and [ADR-0006](../adr/0006-virtual-thread-per-call.md).

## Application state without sessions

Mocapi holds no per-client state between calls. Tools that need
cross-call state use the spec's explicit-handle pattern: return an
identifier (`basket_id`) and take it back as an argument on subsequent
calls, backed by whatever store the application already has. This is a
userland pattern — mocapi ships no framework machinery for it
([ADR-0020](../adr/0020-stateless-request-model.md)).

## What mocapi does not implement

The MCP specification defines several features mocapi deliberately does
not implement: `subscriptions/listen`, the Tasks and MCP Apps
extensions, URL-mode elicitation, JSON-RPC batching, full cancellation
processing, `x-mcp-header` parameter mirroring, and the deprecated
Roots/Sampling/Logging features. Each omission has a stated rationale.

See [ADR-0022](../adr/0022-2026-07-28-features-not-implemented.md) for
the full list and reasoning.
