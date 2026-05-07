# ADR-0001 — Split mocapi into api, model, server, transport, and consolidated starter modules

- **Status:** Accepted
- **Date:** 2025-07-09

## Context

Mocapi started life as a single `mocapi-core` module that mixed user-facing
annotations, MCP protocol types, server logic (session management, JSON-RPC
dispatch, registries), and Spring MVC HTTP/SSE delivery. That coupling made
three things impossible:

1. Adding a second transport (stdio for desktop MCP clients) without dragging
   Spring MVC, Tomcat, and Odyssey onto the classpath.
2. Letting tool authors depend on a stable, narrow API jar without pulling
   in the entire server implementation.
3. Reusing the protocol types (`Tool`, `CallToolResult`, `ElicitResult`, …)
   from a non-server context such as a conformance harness or a thin client.

A parallel sprawl problem grew on the Spring Boot side: every feature
(OAuth2, Jakarta Validation, MDC logging, observability, actuator) shipped
its own `-spring-boot-starter` module, plus four backend-specific starters
(`mocapi-redis-…`, `mocapi-hazelcast-…`, `mocapi-postgresql-…`,
`mocapi-aws-…`) that did nothing except bundle a Substrate backend with the
HTTP starter. Users had to pick a starter from a menu before writing a line
of code; CHANGELOG and docs ballooned with per-starter entries.

## Decision

The library is split along two axes — protocol/transport and
publish-as-jar/publish-as-starter — with the following module shape:

**Library modules:**

- `mocapi-api` — annotations and SPIs that tool/prompt/resource authors
  depend on (`@ToolService`, `@ToolMethod`, `@McpToolParams`, `McpToolContext`,
  `McpTool`, `McpPrompt`, `McpResource`, the matching `*Provider` SPIs).
  Depends only on `mocapi-model`.
- `mocapi-model` — MCP wire types (`Tool`, `CallToolResult`, `ElicitResult`,
  `CreateMessageResult`, `LoggingLevel`, schema types, JSON-RPC method
  constants). No Spring, no transport.
- `mocapi-server` — stateful protocol implementation: session lifecycle,
  JSON-RPC dispatch, response correlation, registries, the `McpServer` /
  `McpTransport` / `McpContext` interfaces. No Spring MVC, no HTTP, no SSE.
  See [ADR-0002](0002-protocol-transport-contract.md).
- `mocapi-streamable-http-transport`, `mocapi-stdio-transport` — peer
  transports implementing the same `McpTransport` contract.
  See [ADR-0003](0003-streamable-http-and-stdio.md).
- Feature modules (`mocapi-oauth2`, `mocapi-logging`, `mocapi-o11y`,
  `mocapi-jakarta-validation`, `mocapi-actuator`, `mocapi-audit`,
  `mocapi-spring-security-guards`, …) — feature implementations,
  transport-agnostic where possible.

**Starter modules (only two user-facing choices):**

- `mocapi-streamable-http-spring-boot-starter` — bundles `mocapi-server`,
  the HTTP transport, and `spring-boot-starter-web`.
- `mocapi-stdio-spring-boot-starter` — bundles `mocapi-server` and the
  stdio transport.

**Autoconfiguration consolidation:**

A single `mocapi-autoconfigure` module owns every feature autoconfig with
`optional` dependencies on the feature jars. Adding `mocapi-oauth2` to a
pom lights up the OAuth2 autoconfig via `@ConditionalOnClass`; no
mocapi-specific feature starter is required. This matches Spring Boot's
own pattern (`spring-boot-starter-oauth2-resource-server` is plain Spring
Boot, not a custom starter).

**Backend-specific starters are not published.** Backend choice is a
pom-level decision: add `mocapi-spring-boot-starter` plus the Substrate
backend (`substrate-redis`, `substrate-jdbc`, `substrate-hazelcast`, …)
and the appropriate Spring Boot data starter. See
[ADR-0007](0007-substrate-storage-spi.md).

**Feature starter dependency rule:** transport-agnostic feature starters
(when they exist as compatibility shims) depend on `mocapi-server`, never
on `mocapi-streamable-http-spring-boot-starter`. A stdio-only consumer who
wants MDC logging or Jakarta Validation must not pull the HTTP transport
transitively.

## Consequences

**Wins:**

- Tool authors compile against `mocapi-api` (a tiny, stable jar). The
  server can be swapped or upgraded without breaking authoring code.
- A stdio-only deployment ships without Spring MVC, Tomcat, or Odyssey.
- The "which starter do I pick?" matrix collapses to a single transport
  choice. Features come alive by adding their own jar, the Spring Boot
  way.
- Adding a third transport (WebSocket, Unix socket, named pipe) needs a
  new `mocapi-<name>-transport` module and a new starter — nothing else
  in the build moves.

**Costs:**

- More modules in the reactor (twenty-plus). The parent pom and BOM carry
  the bookkeeping.
- Pre-1.0 users who depended on backend-specific starters had to migrate
  their poms. Documented in the 0.x CHANGELOG.

**Non-goals:** mocapi does not publish a "kitchen-sink" starter that
includes every feature module. Users opt in by adding the jars they want.

**Code anchors:** the top-level `pom.xml` (Maven reactor) and per-module `pom.xml` files. The module split landed in commit `72cd8731` ("Breaking mocapi up into modules", 2025-07-09).
