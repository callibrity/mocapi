<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/logo-dark.svg">
    <source media="(prefers-color-scheme: light)" srcset="docs/assets/logo.svg">
    <img alt="@Mocapi" src="docs/assets/logo.svg" width="360">
  </picture>
</p>

<h3 align="center">A Spring Boot framework for building <a href="https://modelcontextprotocol.io/specification/draft">Model Context Protocol (MCP)</a> servers in Java.</h3>

Define tools, prompts, and resources as annotated Spring beans. Pull in optional modules for OAuth2, per-handler authorization, Jakarta Bean Validation, structured audit logs, Micrometer observations, MDC correlation, an `/actuator/mcp` inventory endpoint, and GraalVM native-image support — all wired through one customizer SPI.

> **Status:** pre-1.0, actively developed. APIs may still change between 0.x minors. We'd love early feedback and real-world usage reports.

## What's in the box

Building an MCP server from scratch means solving the same problems every team solves: JSON-RPC dispatch, the `_meta` envelope and routing-header validation, SSE response streaming, multi-round-trip elicitation, schema generation, OAuth2, tracing, metrics, audit. Mocapi ships those pieces as Spring Boot autoconfiguration you wire by adding a transport starter, and extend through a single customizer SPI.

- **MCP 2026-07-28 surface.** Tools, prompts, resources, resource templates, completions, `server/discover`, multi-round-trip (MRTR) elicitation, progress notifications, cacheable results, and the OAuth2 authorization flow — fully stateless, as the revision requires. Exercised by the official conformance suite. Deliberate omissions (deprecated Roots/Sampling/Logging, `subscriptions/listen`, the Tasks and MCP Apps extensions) are recorded with rationale in [ADR-0022](docs/adr/0022-2026-07-28-features-not-implemented.md).
- **Transport-agnostic handler code.** Write a `@McpTool` once; run it over Streamable HTTP (for web clients) or stdio (for Claude Desktop / Cursor / IDE integrations) with no code change.
- **Observability modules.** Metrics and tracing via Micrometer Observation, SLF4J MDC correlation, structured audit logs — each activates by dropping in a module.
- **Authorization.** OAuth2 resource server (MCP 2026-07-28 spec), per-handler `Guard` SPI, and `@RequiresScope` / `@RequiresRole` annotations backed by Spring Security.
- **Stateless by design.** No sessions, no sticky routing, no shared store: any node serves any request, and elicitation round trips travel as self-contained AES-GCM-encrypted `requestState` tokens. Scale-to-zero and serverless deployments are the natural shape.
- **Typed extension SPI.** One customizer interface per handler kind. Attach interceptors, guards, or parameter resolvers with full access to the handler's descriptor, method, and bean — no blind bean-list autowiring.
- **Virtual-thread-friendly.** Context propagates across the per-call virtual-thread spawn so tracing spans parent correctly and `SecurityContextHolder` works on the handler thread. A standing soak test sustained ~565 req/s with full observability on a laptop (see [Performance Benchmarking](docs/guides/performance/benchmarking.md)).
- **GraalVM native-image hints included.**

Mocapi's goal is to be the framework you reach for when MCP is a real surface of your product rather than a prototype.

[![Maven Central](https://img.shields.io/maven-central/v/com.callibrity.mocapi/mocapi-server)](https://central.sonatype.com/artifact/com.callibrity.mocapi/mocapi-server)
![GitHub License](https://img.shields.io/github/license/callibrity/mocapi)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=coverage)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=callibrity_mocapi&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=callibrity_mocapi)

## Quick Start

Add the starter dependency:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-streamable-http-spring-boot-starter</artifactId>
    <version>0.17.0</version>
</dependency>
```

If you depend on multiple mocapi artifacts (e.g., a starter plus one of the `mocapi-prompts-*` modules), import the BOM to keep versions aligned:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.callibrity.mocapi</groupId>
            <artifactId>mocapi-bom</artifactId>
            <version>0.17.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Then declare individual mocapi artifacts without a `<version>` — the BOM pins them.

Define a tool:

```java
import com.callibrity.mocapi.api.tools.McpTool;
import org.springframework.stereotype.Component;

@Component
public class GreetingTool {

    @McpTool(name = "greet", description = "Returns a greeting message")
    public GreetingResponse greet(String name) {
        return new GreetingResponse("Hello, " + name + "!");
    }

    public record GreetingResponse(String message) {}
}
```

Define a prompt:

```java
import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SummarizationPrompts {

    @McpPrompt(name = "summarize", description = "Summarize the provided text")
    public GetPromptResult summarize(String text) {
        return new GetPromptResult(
            "Summarization prompt",
            List.of(new PromptMessage(
                Role.USER,
                new TextContent("Summarize the following:\n\n" + text, null))));
    }
}
```

Define a resource (fixed URI) and a resource template (pattern-matched URI):

```java
import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocResources {

    @McpResource(uri = "docs://readme", mimeType = "text/markdown")
    public ReadResourceResult readme() {
        return new ReadResourceResult(
            List.of(new TextResourceContents("docs://readme", "text/markdown", "# Hello")));
    }

    @McpResourceTemplate(uriTemplate = "docs://pages/{slug}", mimeType = "text/markdown")
    public ReadResourceResult page(String slug) {
        return new ReadResourceResult(
            List.of(new TextResourceContents(
                "docs://pages/" + slug, "text/markdown", "Content for " + slug)));
    }
}
```

Run your Spring Boot application. With `mocapi-streamable-http-spring-boot-starter`, Mocapi exposes a Streamable HTTP endpoint at `/mcp`. For clients that launch the server as a subprocess (Claude Desktop, Cursor, and other IDE integrations), depend on `mocapi-stdio-spring-boot-starter` instead and set `mocapi.stdio.enabled=true` — same tools, same code, different pipe.

## Documentation

Docs live under [`docs/`](docs/) in three trees:

- **[`docs/guides/`](docs/guides/)** — how to use mocapi as a library consumer.
- **[`docs/design/`](docs/design/)** — internal architecture, kept synchronized with the code.
- **[`docs/adr/`](docs/adr/)** — point-in-time architecture decisions with status.

### Guides

- [Writing Tools](docs/guides/tools.md) -- defining tools, parameters, return values, and error handling
- [Writing Prompts](docs/guides/prompts.md) -- defining prompts, argument binding, and return messages
- [Writing Resources](docs/guides/resources.md) -- fixed resources, templated resources, and path-variable binding
- [Externalizing Annotation Metadata](docs/guides/externalizing-metadata.md) -- `${...}` property placeholders for tool/prompt/resource descriptions, URIs, and names
- [Authorization](docs/guides/authorization.md) -- OAuth2 resource-server setup for the Streamable HTTP transport (MCP 2026-07-28)
- [Guards](docs/guides/guards.md) -- per-handler visibility + call-time authorization via the `Guard` SPI; `@RequiresScope` / `@RequiresRole` via `mocapi-spring-security-guards`
- [Validation](docs/guides/validation.md) -- Jakarta Bean Validation on user `@McpTool` / `@McpPrompt` / `@McpResourceTemplate` parameters via the optional `mocapi-jakarta-validation`
- [Interactive Tools](docs/guides/interactive-tools.md) -- progress notifications and multi-round-trip elicitation
- [Observability](docs/guides/observability.md) -- metrics + tracing (Micrometer Observation), MDC correlation, and structured audit logging
- [OpenTelemetry](docs/guides/opentelemetry.md) -- drop-in OTel tracing via `mocapi-otel`: bundles `mocapi-o11y` + Spring Boot 4's OTel SDK + tracing bridge; emits a two-layer `jsonrpc.server` / `mcp.handler.execution` trace with OTel MCP / JSON-RPC / GenAI semconv attrs
- [Logging](docs/guides/logging.md) -- MDC correlation keys via `mocapi-logging`
- [Audit](docs/guides/audit.md) -- structured audit logging via `mocapi-audit` for compliance queries and SIEM ingestion
- [Actuator Endpoint](docs/guides/actuator.md) -- `/actuator/mcp` handler-inventory endpoint shape and operational checks
- [Customizers](docs/guides/customizers.md) -- the `*HandlerCustomizer` SPI for extending mocapi: attach interceptors, guards, and parameter resolvers per handler
- [Custom Parameter Resolvers](docs/guides/parameter-resolvers.md) -- writing `@CurrentTenant`-style parameter resolvers via the customizer SPI
- [Configuration Reference](docs/guides/configuration.md) -- all `mocapi.*` properties
- [Performance Benchmarking](docs/guides/performance/benchmarking.md) -- periodic soak-test + JFR-profiling runbook to track regressions

### Design

- [Architecture Overview](docs/design/architecture-overview.md) -- module layering, request flow, ScopedValues
- [Transports](docs/design/transports.md) -- Streamable HTTP, stdio, the `McpServer` ↔ `McpTransport` contract
- [Extension SPI](docs/design/extension-spi.md) -- customizer model, six interceptor strata, parameter resolvers
- [Authorization Model](docs/design/authorization-model.md) -- how OAuth2 + Guard SPI compose
- [Observability Stack](docs/design/observability-stack.md) -- design of the four-module observability story
- [Elicitation — MRTR Replay](docs/design/elicitation-mrtr.md) -- requestState tokens, the replay ledger, schema constraints

See [`docs/adr/`](docs/adr/) for the full list of architecture
decision records.

## Modules

### Core

- **`mocapi-api`** — user-facing API: `@McpTool`, `@McpPrompt`, `@McpResource`/`@McpResourceTemplate`, `PromptTemplate`/`PromptTemplateFactory`, `McpToolContext`, provider interfaces
- **`mocapi-model`** — MCP protocol types (Tool, CallToolResult, ElicitResult, etc.) — mechanical mapping from the MCP spec
- **`mocapi-server`** — stateless MCP server: `_meta` envelope parsing, JSON-RPC dispatch, tool/prompt/resource invocation, `server/discover`, the MRTR elicitation replay engine

### Transports

- **`mocapi-streamable-http-transport`** — HTTP + SSE, encrypted event IDs
- **`mocapi-stdio-transport`** — newline-delimited JSON-RPC on stdin/stdout, for subprocess-launched MCP clients

### Spring Boot starters — pick your transport

Only two starters. Every mocapi application adds exactly one.

- **`mocapi-streamable-http-spring-boot-starter`** — bundles `mocapi-server` + streamable-HTTP transport + `spring-boot-starter-web`. Expose an `/mcp` endpoint accessible over the network.
- **`mocapi-stdio-spring-boot-starter`** — bundles `mocapi-server` + stdio transport. For subprocess-launched MCP clients (Claude Desktop, Cursor, IDE integrations); no web stack.

### Feature modules — drop in to activate

Each module is plain Java + an optional Spring Boot autoconfig (hosted in `mocapi-autoconfigure`). Add the module to your pom; the corresponding feature activates automatically.

- **`mocapi-oauth2`** — OAuth2 resource-server protection on the MCP endpoint (MCP 2026-07-28 authorization); wraps Spring Boot's OAuth2 resource-server starter and adds the RFC 9728 protected-resource metadata document. Ships two `SecurityFilterChain` beans (public metadata + authenticated MCP) each with its own customizer SPI (`McpMetadataFilterChainCustomizer`, `McpFilterChainCustomizer`), a swappable `McpTokenStrategy` for JWT vs. opaque tokens, and a facet-based `McpMetadataCustomizer` SPI for shaping the metadata document. See [Authorization](docs/guides/authorization.md).
- **`mocapi-spring-security-guards`** — annotation-driven `Guard` implementations backed by Spring Security. Reads `@RequiresScope` / `@RequiresRole` off user handler methods at startup and attaches matching guards via the customizer SPI; denied handlers disappear from `tools/list` etc. and call-time returns JSON-RPC `-32010 Forbidden`. See [Guards](docs/guides/guards.md).
- **`mocapi-jakarta-validation`** — Jakarta Bean Validation on user `@McpTool` / `@McpPrompt` / `@McpResourceTemplate` parameters. Annotations like `@NotBlank`/`@Size`/`@Pattern` surface as `CallToolResult.isError=true` for tools (MCP-spec-idiomatic for LLM self-correction) and JSON-RPC `-32602 Invalid params` with per-violation detail for prompts and resources. See [Validation](docs/guides/validation.md).
- **`mocapi-logging`** — SLF4J MDC correlation for MCP handler invocations. Stamps `mcp.protocol.version`, `mcp.client.name`, `mcp.handler.kind`, `mcp.handler.name`, and `mcp.request.id` onto the MDC for the duration of every handler call so every log line from user code carries correlation context automatically. See [Logging](docs/guides/logging.md).
- **`mocapi-o11y`** — metrics + distributed tracing via Micrometer's `Observation` API. Two layers: a filter enriches ripcurl-o11y's outer `jsonrpc.server` observation with `mcp.method.name` / `mcp.protocol.version` tags (joining inbound W3C trace context from the `_meta` envelope); a per-handler interceptor emits an inner `mcp.handler.execution` observation carrying GenAI / MCP-resource attrs (`gen_ai.tool.name`, `gen_ai.prompt.name`, `mcp.resource.uri`). Self-sufficient — transitively pulls `spring-boot-micrometer-observation` so an `ObservationRegistry` is always present. See [Observability](docs/guides/observability.md).
- **`mocapi-otel`** — drop-in OpenTelemetry tracing. Source-less dependency bundle that pulls `mocapi-o11y` plus `spring-boot-starter-opentelemetry` (OTel SDK + Micrometer Observation → OTel tracing bridge + autoconfig). Add this module plus the exporter for your backend — OTLP for Jaeger/Tempo, Azure Monitor starter for App Insights, Datadog, etc. — and `jsonrpc.server` / `mcp.handler.execution` spans flow end-to-end. See [OTel guide](docs/guides/opentelemetry.md).
- **`mocapi-audit`** — structured audit logging for every MCP handler invocation. Emits one INFO event on the `mocapi.audit` SLF4J logger per call with caller identity, protocol version, client name, handler kind/name, outcome (`success`/`forbidden`/`invalid_params`/`error`), duration, and (opt-in) a SHA-256 hash of the arguments — everything compliance / SIEM queries need, nothing PII-shaped. See [Audit](docs/guides/audit.md).
- **`mocapi-actuator`** — Spring Boot Actuator endpoint (`/actuator/mcp`) exposing a read-only inventory of the tools, prompts, resources, and resource templates registered on this node. Publishes handler names + schema digests. See [Actuator Endpoint](docs/guides/actuator.md).
- **`mocapi-autoconfigure`** — one module hosting every mocapi autoconfig (pulled in by either transport starter; you normally don't depend on it directly).

### Prompt templating (optional)

- **`mocapi-prompts-spring`** — `PromptTemplateFactory` using Spring's `${name}` placeholder syntax; no extra dependencies
- **`mocapi-prompts-mustache`** — `PromptTemplateFactory` backed by [JMustache](https://github.com/samskivert/jmustache) for richer `{{name}}` templates with sections

### Bill of Materials (optional)

- **`mocapi-bom`** — imports into your `<dependencyManagement>` to align versions across multiple mocapi artifacts without hard-coding each one

## Examples

Working examples are in the [`examples/`](examples/) directory:

| Example | Transport | Description |
|---------|-----------|-------------|
| [HTTP](examples/http) | Streamable HTTP | Comprehensive app: tools, resources, prompts, elicitation, and Jakarta Bean Validation |
| [Stdio](examples/stdio) | stdio | Minimal echo server launchable by Claude Desktop or MCP Inspector over stdio |

To run the HTTP example:

```bash
cd examples/http
mvn spring-boot:run
```

Then connect with the [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector):

```bash
npx @modelcontextprotocol/inspector
```

Enter `http://localhost:8080/mcp` and select "Streamable HTTP" transport.

To run the stdio example (no HTTP server — MCP client launches it as a subprocess):

```bash
mvn -pl examples/stdio -am package
npx @modelcontextprotocol/inspector \
    java -jar examples/stdio/target/mocapi-example-stdio-*.jar
```

See [`examples/stdio/README.md`](examples/stdio/README.md) for Claude Desktop configuration.

## MCP Conformance

Mocapi targets the [MCP 2026-07-28](https://modelcontextprotocol.io/specification/draft) specification (release candidate until 2026-07-28). Conformance is validated against the official suite's draft track — see [mocapi-conformance/README.md](mocapi-conformance/README.md) for current results and the expected-failures baseline:

```bash
# Start the conformance server
cd mocapi-conformance
mvn spring-boot:run

# In another terminal
npx @modelcontextprotocol/conformance server --url http://localhost:8081/mcp
```

## Building from Source

```bash
mvn clean install
```

Requires Java 25+ and Maven 3.9+.

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Apache License 2.0 -- see [LICENSE](LICENSE).

## How Mocapi Was Built

Mocapi was built primarily with [Claude Code](https://www.anthropic.com/claude-code), using a spec-driven, iterative workflow we called "the ralph loop" — one spec, one autonomous iteration, in strict numeric order.

The cycle was: write a numbered Markdown spec describing a single focused change (breaking change, new module, bug fix, refactor — one thing at a time), let an autonomous Claude Code agent pick up the lowest-numbered spec, implement it end-to-end (code + tests + docs + commit), then move on to the next one. The human role was spec author, reviewer, and course-corrector — not typist. Iteration 180 didn't know iteration 179 existed; it read the spec and the current codebase from scratch.

The 210+ Ralph specs that drove 0.1.0 → 0.17.0 have been retired from the tree. Their **architectural** outcomes are now captured in living design docs under [`docs/design/`](docs/design/) and as Architecture Decision Records under [`docs/adr/`](docs/adr/) (the ADRs are dated to when each decision *landed in the codebase*, not when the document was written; see the [ADR README](docs/adr/README.md) for provenance). The per-change rationale that the individual specs carried is no longer in-tree — `git log` is the surviving record. Project-level Claude instructions (`CLAUDE.md`, `~/CLAUDE-ralph.md`) describe how the loop was operated.


## What's in a Name?

Mocapi is a made-up word that includes the letters MCP (Model Context Protocol). It's pronounced moh-cap-ee (/ˈmoʊˌkæpi/), like a friendly little robot who speaks protocol.
