# OpenTelemetry tracing for tool / prompt / resource invocations

## What to build

Automatic OpenTelemetry spans around every MCP handler dispatch, so apps
running under an OTel-instrumented environment (OTel Java Agent, Spring Boot
OTel starter, etc.) get one span per tool call / prompt resolution /
resource read with the right attributes and no extra code.

### Two new modules, per project convention

- **`mocapi-tracing`** — the code. Depends on `mocapi-api` (for the
  observer SPI) and `opentelemetry-api`. Contains the `McpHandlerObserver`
  impl and its autoconfig, conditional on `Tracer.class` and
  `OpenTelemetry` bean presence.
- **`mocapi-tracing-spring-boot-starter`** — dependency aggregator, no
  code. Depends on `mocapi-tracing` and the streamable-http starter.

Users supply their own exporter / SDK via whatever OTel setup they
already have — `mocapi-tracing` only pulls in `opentelemetry-api`.

### Span per invocation

One span per dispatched handler, sibling to whatever HTTP / messaging span
the upstream transport already emits.

- **Tool call:** span name `mcp.tool <tool-name>`, e.g. `mcp.tool blast-radius`
- **Prompt get:** `mcp.prompt <prompt-name>`
- **Resource read:** `mcp.resource <resource-name-or-uri>`

Span kind: `INTERNAL` (the HTTP transport already has its own SERVER span).

### Attributes

Every span:

- `mcp.handler.kind` — one of `tool`, `prompt`, `resource`, `resourceTemplate`
- `mcp.handler.name` — the tool / prompt / resource name
- `mcp.session.id` — current MCP session id (when available)
- `mcp.request.id` — JSON-RPC request id (when available)

Tools specifically add:

- `mcp.tool.is_error` — boolean, set when the tool returned
  `CallToolResult.isError=true`

On exception:

- Span status = ERROR
- Exception recorded via `span.recordException(ex)`

### Propagation

No change. The OTel context propagates via `Context.current()` / SDK
instrumentation of the HTTP inbound — mocapi's spans just nest under
whatever parent is already there.

### Where it hooks in

Same SPI-driven hooks from spec 169 (`McpHandlerObserver` or equivalent).
Tracing is another observer implementation registered conditionally by the
tracing starter. This keeps the server module free of OTel dependency.

### No manual instrumentation API

This spec does not add a public `Tracer` on `McpToolContext`. Authors who
want hand-rolled spans inside their tool use their own `Tracer` / `@WithSpan`.
Keep the surface area minimal.

## Acceptance criteria

- [ ] Two new Maven modules: `mocapi-tracing` (code) and
      `mocapi-tracing-spring-boot-starter` (no-code aggregator).
      Autoconfig in `mocapi-tracing` conditional on `Tracer.class` and an
      `OpenTelemetry` bean (or equivalent Spring Boot trigger).
- [ ] `McpHandlerObserver` implementation creates one span per invocation
      with the exact span names and attributes above.
- [ ] Span kind is `INTERNAL`.
- [ ] Exception paths record the exception and set span status to ERROR.
- [ ] `isError=true` tool results do *not* set span status to ERROR (the
      call itself was successful) but do tag `mcp.tool.is_error=true`.
- [ ] Tests use OTel's `InMemorySpanExporter` to assert emitted spans —
      both happy path and exception path.
- [ ] No hard dependency on a specific OTel SDK distribution in POM — just
      `opentelemetry-api`.
- [ ] Both modules added to `mocapi-bom`.
- [ ] README doc with the span names, attribute list, and the Spring Boot
      OTel starter setup snippet users typically already have.

## Implementation notes

- Reuse the observer SPI introduced in spec 169 (`McpHandlerObserver`).
  Order: land 169 first, then this.
- `Tracer tracer = openTelemetry.getTracer("com.callibrity.mocapi", version);`
  with version pulled from the module's `build-info` or a constant.
- Use `span.makeCurrent()` in a try-with-resources so downstream code (tool
  handler, any nested HTTP calls) inherits the span context.
- Explicitly *not* in scope: OpenTelemetry metrics. Metrics stay on
  Micrometer via spec 169; OTel export of Micrometer is done by users via
  the OTel Micrometer bridge.
