# Mocapi

**An enterprise-grade Spring Boot framework for building [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25) servers in Java.**

Define tools, prompts, and resources as annotated Spring beans. Ship to production with OAuth2, per-handler authorization, Jakarta Bean Validation, structured audit logging, distributed tracing, metrics, correlation-ID logging, an ops inventory endpoint, multi-node session state, and GraalVM native-image support — all opt-in, all wired through one consistent extension SPI.

**Batteries included. Opinions explicit. Surface area minimal.**

## Why Mocapi

Building an MCP server from scratch means solving the same problems every team solves: JSON-RPC dispatch, SSE streaming, session lifecycle, event replay, schema generation, transport negotiation, OAuth2, tracing, metrics, audit. Mocapi ships all of that as a Spring Boot framework you wire once — by adding a transport starter — and extend through a single well-defined customizer SPI.

- **Protocol coverage out of the box.** Complete MCP 2025-11-25 core surface — tools, prompts, resources, resource templates, completions, elicitation, sampling, progress notifications, logging, and the OAuth2 authorization flow — validated against the official conformance suite. Optional subscription features (`resources/subscribe` / `resources/list_changed` / `prompts/list_changed`) are not currently served; the capability bits are advertised as `false`, so spec-compliant clients fall back cleanly.
- **Transport-agnostic handler code.** Write a `@McpTool` once; run it over Streamable HTTP (for web clients) or stdio (for Claude Desktop / Cursor / IDE integrations) with no code change.
- **Production-grade observability.** Metrics and distributed tracing via Micrometer Observation, SLF4J MDC correlation, structured audit logs for compliance — all activate by dropping in a module.
- **Enterprise authorization.** OAuth2 resource server (MCP 2025-11-25 spec), per-handler `Guard` SPI, and `@RequiresScope` / `@RequiresRole` annotations backed by Spring Security.
- **Multi-node by default.** Swap the session backend: Redis, PostgreSQL, NATS JetStream, DynamoDB, or Hazelcast. Workers are stateless; scale horizontally. Add `substrate-crypto` and every session, mailbox, and event-journal value is AES-GCM-encrypted at rest before it leaves the JVM.
- **Extensible without surprises.** One customizer SPI per handler kind. Add interceptors, guards, or custom parameter resolvers with full access to the handler's descriptor, method, and bean. No blind bean-list autowiring.
- **Fast where it matters.** Methodical 0.7's pre-bound parameter resolvers eliminate per-call reflection for things like Jackson `ObjectReader` construction. Context propagates across the per-call virtual-thread spawn so tracing spans parent correctly and `SecurityContextHolder` works on the handler thread. Sustained ~565 req/s with full observability in our standing soak test (see [Performance Benchmarking](docs/perf/benchmarking.md)).
- **Native-image ready.** GraalVM AOT hints shipped; native builds just work.

Mocapi is what you want when you've decided MCP is infrastructure, not a demo.

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
    <version>0.12.1</version>
</dependency>
```

If you depend on multiple mocapi artifacts (e.g., a starter plus one of the `mocapi-prompts-*` modules), import the BOM to keep versions aligned:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.callibrity.mocapi</groupId>
            <artifactId>mocapi-bom</artifactId>
            <version>0.12.1</version>
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

- [Writing Tools](docs/tools-guide.md) -- defining tools, parameters, return values, and error handling
- [Writing Prompts](docs/prompts-guide.md) -- defining prompts, argument binding, and return messages
- [Writing Resources](docs/resources-guide.md) -- fixed resources, templated resources, and path-variable binding
- [Externalizing Annotation Metadata](docs/externalizing-metadata.md) -- `${...}` property placeholders for tool/prompt/resource descriptions, URIs, and names
- [Authorization](docs/authorization.md) -- OAuth2 resource-server setup for the Streamable HTTP transport (MCP 2025-11-25)
- [Guards](docs/guards.md) -- per-handler visibility + call-time authorization via the `Guard` SPI; `@RequiresScope` / `@RequiresRole` via `mocapi-spring-security-guards`
- [Validation](docs/validation.md) -- Jakarta Bean Validation on user `@McpTool` / `@McpPrompt` / `@McpResourceTemplate` parameters via the optional `mocapi-jakarta-validation`
- [Interactive Features](docs/interactive-guide.md) -- progress notifications, logging, elicitation, and sampling
- [Observability](docs/observability.md) -- metrics + tracing (Micrometer Observation), MDC correlation, and structured audit logging
- [OpenTelemetry](docs/otel.md) -- drop-in OTel tracing via `mocapi-otel`: bundles `mocapi-o11y` + Spring Boot 4's OTel SDK + tracing bridge; emits a two-layer `jsonrpc.server` / `mcp.handler.execution` trace with OTel MCP / JSON-RPC / GenAI semconv attrs
- [Logging](docs/logging.md) -- MDC correlation keys via `mocapi-logging`
- [Audit](docs/audit.md) -- structured audit logging via `mocapi-audit` for compliance queries and SIEM ingestion
- [Actuator Endpoint](docs/actuator.md) -- `/actuator/mcp` handler-inventory endpoint shape and operational checks
- [Customizers](docs/customizers.md) -- the `*HandlerCustomizer` SPI for extending mocapi: attach interceptors, guards, and parameter resolvers per handler
- [Custom Parameter Resolvers](docs/parameter-resolvers.md) -- writing `@CurrentTenant`-style parameter resolvers via the customizer SPI
- [Configuration Reference](docs/configuration.md) -- all `mocapi.*` properties
- [Architecture](docs/architecture.md) -- server/transport separation, session lifecycle, module structure
- [Backend Integration](docs/backends.md) -- using Redis, PostgreSQL, Hazelcast, or other session stores
- [Performance Benchmarking](docs/perf/benchmarking.md) -- periodic soak-test + JFR-profiling runbook to track regressions

## Modules

### Core

- **`mocapi-api`** — user-facing API: `@McpTool`, `@McpPrompt`, `@McpResource`/`@McpResourceTemplate`, `PromptTemplate`/`PromptTemplateFactory`, `McpToolContext`, provider interfaces
- **`mocapi-model`** — MCP protocol types (Tool, CallToolResult, ElicitResult, etc.) — mechanical mapping from the MCP spec
- **`mocapi-server`** — stateful MCP server: session management, JSON-RPC dispatch, tool/prompt/resource invocation

### Transports

- **`mocapi-streamable-http-transport`** — HTTP + SSE, encrypted event IDs
- **`mocapi-stdio-transport`** — newline-delimited JSON-RPC on stdin/stdout, for subprocess-launched MCP clients

### Spring Boot starters — pick your transport

Only two starters. Every mocapi application adds exactly one.

- **`mocapi-streamable-http-spring-boot-starter`** — bundles `mocapi-server` + streamable-HTTP transport + `spring-boot-starter-web`. Expose an `/mcp` endpoint accessible over the network.
- **`mocapi-stdio-spring-boot-starter`** — bundles `mocapi-server` + stdio transport. For subprocess-launched MCP clients (Claude Desktop, Cursor, IDE integrations); no web stack.

### Feature modules — drop in to activate

Each module is plain Java + an optional Spring Boot autoconfig (hosted in `mocapi-autoconfigure`). Add the module to your pom; the corresponding feature activates automatically.

- **`mocapi-oauth2`** — OAuth2 resource-server protection on the MCP endpoint (MCP 2025-11-25 authorization); wraps Spring Boot's OAuth2 resource-server starter and adds the RFC 9728 protected-resource metadata document. See [Authorization](docs/authorization.md).
- **`mocapi-spring-security-guards`** — annotation-driven `Guard` implementations backed by Spring Security. Reads `@RequiresScope` / `@RequiresRole` off user handler methods at startup and attaches matching guards via the customizer SPI; denied handlers disappear from `tools/list` etc. and call-time returns JSON-RPC `-32003 Forbidden`. See [Guards](docs/guards.md).
- **`mocapi-jakarta-validation`** — Jakarta Bean Validation on user `@McpTool` / `@McpPrompt` / `@McpResourceTemplate` parameters. Annotations like `@NotBlank`/`@Size`/`@Pattern` surface as `CallToolResult.isError=true` for tools (MCP-spec-idiomatic for LLM self-correction) and JSON-RPC `-32602 Invalid params` with per-violation detail for prompts and resources. See [Validation](docs/validation.md).
- **`mocapi-logging`** — SLF4J MDC correlation for MCP handler invocations. Stamps `mcp.session`, `mcp.handler.kind`, and `mcp.handler.name` onto the MDC for the duration of every handler call so every log line from user code carries correlation context automatically. See [Logging](docs/logging.md).
- **`mocapi-o11y`** — metrics + distributed tracing via Micrometer's `Observation` API. Two layers: a filter enriches ripcurl-o11y's outer `jsonrpc.server` observation with `mcp.method.name` / `mcp.session.id` / `mcp.protocol.version` tags; a per-handler interceptor emits an inner `mcp.handler.execution` observation carrying GenAI / MCP-resource attrs (`gen_ai.tool.name`, `gen_ai.prompt.name`, `mcp.resource.uri`). Self-sufficient — transitively pulls `spring-boot-micrometer-observation` so an `ObservationRegistry` is always present. See [Observability](docs/observability.md).
- **`mocapi-otel`** — drop-in OpenTelemetry tracing. Source-less dependency bundle that pulls `mocapi-o11y` plus `spring-boot-starter-opentelemetry` (OTel SDK + Micrometer Observation → OTel tracing bridge + autoconfig). Add this module plus the exporter for your backend — OTLP for Jaeger/Tempo, Azure Monitor starter for App Insights, Datadog, etc. — and `jsonrpc.server` / `mcp.handler.execution` spans flow end-to-end. See [OTel guide](docs/otel.md).
- **`mocapi-audit`** — structured audit logging for every MCP handler invocation. Emits one INFO event on the `mocapi.audit` SLF4J logger per call with caller identity, session id, handler kind/name, outcome (`success`/`forbidden`/`invalid_params`/`error`), duration, and (opt-in) a SHA-256 hash of the arguments — everything compliance / SIEM queries need, nothing PII-shaped. See [Audit](docs/audit.md).
- **`mocapi-actuator`** — Spring Boot Actuator endpoint (`/actuator/mcp`) exposing a read-only inventory of the tools, prompts, resources, and resource templates registered on this node. Publishes handler names + schema digests. See [Actuator Endpoint](docs/actuator.md).
- **`mocapi-autoconfigure`** — one module hosting every mocapi autoconfig (pulled in by either transport starter; you normally don't depend on it directly).

### Prompt templating (optional)

- **`mocapi-prompts-spring`** — `PromptTemplateFactory` using Spring's `${name}` placeholder syntax; no extra dependencies
- **`mocapi-prompts-mustache`** — `PromptTemplateFactory` backed by [JMustache](https://github.com/samskivert/jmustache) for richer `{{name}}` templates with sections

### Bill of Materials (optional)

- **`mocapi-bom`** — imports into your `<dependencyManagement>` to align versions across multiple mocapi artifacts without hard-coding each one

## Examples

Working examples are in the [`examples/`](examples/) directory:

| Example | Backend | Description |
|---------|---------|-------------|
| [In-Memory](examples/in-memory) | In-memory, HTTP | Simplest setup, no external dependencies |
| [Stdio](examples/stdio) | In-memory, stdio | Minimal echo server launchable by Claude Desktop or MCP Inspector over stdio |
| [Redis](examples/redis) | Redis, HTTP | Clustered sessions via Redis |
| [PostgreSQL](examples/postgresql) | PostgreSQL, HTTP | Durable sessions via PostgreSQL |
| [NATS](examples/nats) | NATS JetStream, HTTP | Clustered sessions via NATS JetStream |

To run the in-memory HTTP example:

```bash
cd examples/in-memory
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

Mocapi targets the [MCP 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25) specification. Conformance is validated against the official test suite:

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

Mocapi was built primarily with [Claude Code](https://www.anthropic.com/claude-code), using a spec-driven, iterative workflow we call "the ralph loop" — one spec, one autonomous iteration, in strict numeric order.

The cycle: write a numbered Markdown spec under [`specs/`](specs/) describing a single focused change (breaking change, new module, bug fix, refactor — one thing at a time), let an autonomous Claude Code agent pick up the lowest-numbered spec, implement it end-to-end (code + tests + docs + commit), then move on to the next one. The human role is spec author, reviewer, and course-corrector — not typist. Iteration 180 doesn't know iteration 179 existed; it reads the spec and the current codebase from scratch.

[`specs/done/`](specs/done) holds the 210+ specs that produced the 0.1.0 → 0.12.0 journey. They're the closest thing to a design diary this project has — each file captures the *why* of one change before it landed.

Things that made the loop work (and things that didn't) are captured under [`specs/backlog/`](specs/backlog) and in project-level Claude instructions (`CLAUDE.md`, `~/CLAUDE-ralph.md`). If you're trying this approach on your own project, those are probably more useful reading than the individual specs.


## What's in a Name?

Mocapi is a made-up word that includes the letters MCP (Model Context Protocol). It's pronounced moh-cap-ee (/ˈmoʊˌkæpi/), like a friendly little robot who speaks protocol.
