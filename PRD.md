# PRD — mocapi

## What this is

mocapi is a modular Spring Boot framework for building Model Context Protocol
(MCP) servers with a clean, annotation-driven API. You declare tools with
`@McpTool` and prompts with `@McpPrompt` on Spring components; the framework
handles JSON-RPC dispatch, JSON-Schema generation/validation, the Streamable
HTTP and stdio transports, and Spring Boot auto-configuration.

mocapi targets the **MCP 2026-07-28** revision and is **stateless** — no
sessions, no handshake, no server-initiated request channel.

## Where to look

This PRD is the stable "what & why." Volatile detail lives in the docs that are
kept in lockstep with the code — don't duplicate them here:

- **Direction (where we're going):** [`docs/roadmap.md`](docs/roadmap.md)
- **Architectural invariants (non-negotiable):** [`docs/constitution.md`](docs/constitution.md)
- **Architecture & module layout:** [`docs/design/architecture-overview.md`](docs/design/architecture-overview.md)
- **How we work (spec → plan → implement):** [`docs/superpowers/README.md`](docs/superpowers/README.md)
- **Decisions (history):** [`docs/adr/`](docs/adr/)
- **Contributor/agent conduct rules:** [`CLAUDE.md`](CLAUDE.md)

## Tech stack

- Java 25 (Liberica JDK 25)
- Spring Boot 4.0.5
- Maven 3.9+
- JUnit 5 + Mockito + AssertJ (unit); Spring Boot Test + Failsafe (integration)
- Spotless (Google Java Format); Apache-2.0 headers via `license-maven-plugin`
- Jackson, VicTools JSON Schema, Everit JSON Schema

## Build, test, run

```bash
mvn clean install    # build all modules
mvn verify           # full build + all tests + Spotless/license checks
mvn test             # unit tests only
```

Example servers live under [`examples/`](examples/): `examples/http`
(`mocapi-example-http`) and `examples/stdio` (`mocapi-example-stdio`). Run the
HTTP example with `mvn spring-boot:run -pl examples/http`; `mcp-example-requests.http`
drives it. `mocapi-conformance` is a runnable Spring Boot app exercised by the
external `@modelcontextprotocol/conformance` tool.

## Conventions & guardrails

Coding conventions, the "never suppress warnings" rule (and its one
spec-mandated `@SuppressWarnings("deprecation")` exception for
`LegacyTitledEnumSchema`), the no-star-imports rule, and the ADR + design-doc
discipline are defined in [`CLAUDE.md`](CLAUDE.md). The load-bearing
architectural invariants are in [`docs/constitution.md`](docs/constitution.md).
Those are the source of truth — this file does not restate them.

## Environment

- No runtime environment variables required for development or testing.
- `SONAR_TOKEN` — CI only (SonarCloud analysis).
- Java 25 and Maven 3.9+ installed locally.
