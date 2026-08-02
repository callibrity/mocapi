# Mocapi Guides

How to use mocapi as a library consumer. If you're building an MCP
server, start here.

## Getting started
- [Writing Tools](tools.md)
- [Writing Prompts](prompts.md)
- [Writing Resources](resources.md)
- [Configuration Reference](configuration.md)

## Interactivity
- [Interactive Tools — progress, MRTR elicitation](interactive-tools.md)
- [Externalizing Metadata](externalizing-metadata.md)
- [MCP Apps](apps.md) — declare a `ui://` resource, link it to a tool, and hand off to the official in-iframe JS SDK
- [MCP Tasks](tasks.md) — poll long-running tools with `@McpTask`, plus deployment topology and custom `TaskStore` guidance

## Extending mocapi
- [**Extending mocapi**](extending-mocapi.md) — start here: the seam taxonomy, one worked example per seam, the dispatch-interceptor contract
- [Customizers](customizers.md)
- [Custom Parameter Resolvers](parameter-resolvers.md)
- [Guards](guards.md)
- [Bean Validation](validation.md)

## Security & Authorization
- [**Securing your MCP server**](security.md) — start here: the pre-production hardening checklist
- [OAuth2 Resource Server](authorization.md)

## Operations
- [Observability Overview](observability.md) — Logging, OTel, Audit, Actuator
- [Logging / MDC Correlation](logging.md)
- [OpenTelemetry](opentelemetry.md)
- [Audit](audit.md)
- [`/actuator/mcp` Endpoint](actuator.md)
- [Throughput & Saturation Testing](performance/throughput-testing.md) — find the request-throughput ceiling (k6, two-box)
- [Performance Benchmarking](performance/benchmarking.md)
- [Performance History](performance/history.md)
