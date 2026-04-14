# Mocapi

Mocapi is a Spring Boot framework for building [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-11-25) servers in Java. Define tools, prompts, and resources as annotated Spring beans and let Mocapi handle the protocol, transport, and session management.

![Maven Central Version](https://img.shields.io/maven-central/v/com.callibrity.mocapi/mocapi-spring-boot-starter)
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
    <artifactId>mocapi-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

Define a tool:

```java
import com.callibrity.mocapi.api.tools.ToolService;
import com.callibrity.mocapi.api.tools.ToolMethod;
import org.springframework.stereotype.Component;

@Component
@ToolService
public class GreetingTool {

    @ToolMethod(name = "greet", description = "Returns a greeting message")
    public GreetingResponse greet(String name) {
        return new GreetingResponse("Hello, " + name + "!");
    }

    public record GreetingResponse(String message) {}
}
```

Run your Spring Boot application. Mocapi exposes a Streamable HTTP endpoint at `/mcp` by default.

## Documentation

- [Writing Tools](docs/tools-guide.md) -- defining tools, parameters, return values, and error handling
- [Interactive Features](docs/interactive-guide.md) -- progress notifications, logging, elicitation, and sampling
- [Configuration Reference](docs/configuration.md) -- all `mocapi.*` properties
- [Architecture](docs/architecture.md) -- server/transport separation, session lifecycle, module structure
- [Backend Integration](docs/backends.md) -- using Redis, PostgreSQL, Hazelcast, or other session stores

## Modules

| Module | Description |
|--------|-------------|
| `mocapi-api` | User-facing API: `@ToolService`, `@ToolMethod`, `McpToolContext`, provider interfaces |
| `mocapi-model` | MCP protocol types (Tool, CallToolResult, ElicitResult, etc.) |
| `mocapi-server` | Stateful MCP server: session management, JSON-RPC dispatch, tool invocation |
| `mocapi-transport-streamable-http` | Streamable HTTP transport with SSE, encrypted event IDs |
| `mocapi-spring-boot-starter` | All-in-one starter for Spring Boot applications |

## Examples

Working examples are in the [`examples/`](examples/) directory:

| Example | Backend | Description |
|---------|---------|-------------|
| [In-Memory](examples/example-in-memory) | In-memory | Simplest setup, no external dependencies |
| [Redis](examples/redis) | Redis | Clustered sessions via Redis |
| [PostgreSQL](examples/postgresql) | PostgreSQL | Durable sessions via PostgreSQL |

To run the in-memory example:

```bash
cd examples/example-in-memory
mvn spring-boot:run
```

Then connect with the [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector):

```bash
npx @modelcontextprotocol/inspector
```

Enter `http://localhost:8080/mcp` and select "Streamable HTTP" transport.

## MCP Conformance

Mocapi targets the [MCP 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25) specification. Conformance is validated against the official test suite:

```bash
# Start the conformance server
cd mocapi-compat
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
