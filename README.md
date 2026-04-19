# Mocapi

Mocapi is a Spring Boot framework for building [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25) servers in Java. Define tools, prompts, and resources as annotated Spring beans and let Mocapi handle the protocol, transport, and session management.

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
    <version>0.10.0</version>
</dependency>
```

If you depend on multiple mocapi artifacts (e.g., a starter plus one of the `mocapi-prompts-*` modules), import the BOM to keep versions aligned:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.callibrity.mocapi</groupId>
            <artifactId>mocapi-bom</artifactId>
            <version>0.10.0</version>
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
- [Validation](docs/validation.md) -- Jakarta Bean Validation on user `@McpTool` / `@McpPrompt` / `@McpResourceTemplate` parameters via the optional `mocapi-jakarta-validation-spring-boot-starter`
- [Interactive Features](docs/interactive-guide.md) -- progress notifications, logging, elicitation, and sampling
- [Configuration Reference](docs/configuration.md) -- all `mocapi.*` properties
- [Architecture](docs/architecture.md) -- server/transport separation, session lifecycle, module structure
- [Backend Integration](docs/backends.md) -- using Redis, PostgreSQL, Hazelcast, or other session stores

## Modules

### Core

- **`mocapi-api`** — user-facing API: `@McpTool`, `@McpPrompt`, `@McpResource`/`@McpResourceTemplate`, `PromptTemplate`/`PromptTemplateFactory`, `McpToolContext`, provider interfaces
- **`mocapi-model`** — MCP protocol types (Tool, CallToolResult, ElicitResult, etc.) — mechanical mapping from the MCP spec
- **`mocapi-server`** — stateful MCP server: session management, JSON-RPC dispatch, tool/prompt/resource invocation

### Transports

- **`mocapi-streamable-http-transport`** — HTTP + SSE, encrypted event IDs
- **`mocapi-stdio-transport`** — newline-delimited JSON-RPC on stdin/stdout, for subprocess-launched MCP clients

### Spring Boot starters

- **`mocapi-streamable-http-spring-boot-starter`** — bundles `mocapi-server` + streamable-http transport
- **`mocapi-stdio-spring-boot-starter`** — bundles `mocapi-server` + stdio transport
- **`mocapi-oauth2-spring-boot-starter`** — OAuth2 resource-server protection on the MCP endpoint (MCP 2025-11-25 authorization); wraps Spring Boot's OAuth2 resource-server starter and adds the RFC 9728 protected-resource metadata document. See [Authorization](docs/authorization.md).
- **`mocapi-jakarta-validation-spring-boot-starter`** — Jakarta Bean Validation on user `@McpTool` / `@McpPrompt` / `@McpResourceTemplate` parameters. Annotations like `@NotBlank`/`@Size`/`@Pattern` surface as `CallToolResult.isError=true` for tools (MCP-spec-idiomatic for LLM self-correction) and JSON-RPC `-32602 Invalid params` with per-violation detail for prompts and resources. See [Validation](docs/validation.md).
- **`mocapi-o11y`** — single-interceptor observability library. Wraps every tool / prompt / resource / resource-template invocation in a Micrometer `Observation` so the same code emits both metrics (via a `MeterObservationHandler`) and tracing spans (via a `TracingObservationHandler`) — no separate metrics / tracing interceptors.
- **`mocapi-o11y-spring-boot-starter`** — autoconfig that wires the o11y interceptor onto every handler whenever an `ObservationRegistry` bean is present. Drop in alongside `spring-boot-starter-actuator` + a Micrometer meter registry for metrics, or alongside `micrometer-tracing-bridge-otel` for tracing, or both.
- **`mocapi-actuator-spring-boot-starter`** — Spring Boot Actuator endpoint at `/actuator/mcp` exposing a read-only inventory of the MCP tools, prompts, resources, and resource templates registered on this node. Publishes handler names + schema digests (not the full schemas) so ops teams can answer "what's running here?" without opening an MCP session.

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

## What's in a Name?

Mocapi is a made-up word that includes the letters MCP (Model Context Protocol). It's pronounced moh-cap-ee (/ˈmoʊˌkæpi/), like a friendly little robot who speaks protocol.
