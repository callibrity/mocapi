# `/actuator/mcp` endpoint for operational visibility

## What to build

A Spring Boot Actuator endpoint that exposes the runtime state of the MCP
server — registered handlers, active sessions, recent invocations — so
operators can see what's going on without digging through logs.

### Two new modules, per project convention

- **`mocapi-actuator`** — the code. Depends on `mocapi-api`,
  `mocapi-server` (for `McpSessionService`, `McpToolsService`, etc.), and
  `spring-boot-actuator`. Contains the `@WebEndpoint(id = "mcp")` class
  and its autoconfig; conditional on actuator being present.
- **`mocapi-actuator-spring-boot-starter`** — dependency aggregator, no
  code. Depends on `mocapi-actuator` and
  `mocapi-streamable-http-spring-boot-starter`.

Most apps add only the starter.

### Top-level shape

`GET /actuator/mcp`:

```json
{
  "server": {
    "name": "cowork-connector-demo",
    "title": "Meridian service catalog demo",
    "version": "0.5.1",
    "mcpProtocolVersion": "2025-11-25"
  },
  "handlers": {
    "tools": 10,
    "prompts": 0,
    "resources": 0,
    "resourceTemplates": 0
  },
  "sessions": {
    "active": 2,
    "initialized": 2
  },
  "uptime": "PT4H32M11S"
}
```

Small, cheap — safe to poll.

### Sub-resources

- `GET /actuator/mcp/tools` — list of every registered tool with name,
  title, description, and input/output schema digest (SHA-256 of the schema
  JSON, 16 hex chars). Schema itself omitted by default to keep the payload
  small; users who want the full schema call `tools/list` over MCP.
- `GET /actuator/mcp/tools/{name}` — full descriptor for one tool including
  the expanded input/output schemas.
- `GET /actuator/mcp/prompts` / `GET /actuator/mcp/prompts/{name}` — same
  pattern for prompts.
- `GET /actuator/mcp/resources` / `GET /actuator/mcp/resources/{uri}` —
  same for fixed resources.
- `GET /actuator/mcp/resourceTemplates` — same for parameterized resources.
- `GET /actuator/mcp/sessions` — list active sessions with
  `{id, initialized, logLevel, createdAt, lastActivityAt}`. Does *not*
  include any content from those sessions — only metadata.

### Integration with metrics (spec 169)

When the metrics starter is also on the classpath, the endpoint includes
meter snapshots:

```json
{
  ...,
  "metrics": {
    "tools": {
      "blast-radius": {
        "invocations": {"success": 42, "error": 3},
        "p50": "PT0.004S",
        "p95": "PT0.018S",
        "active": 0
      }
    }
  }
}
```

If `MeterRegistry` isn't present, the `metrics` field is omitted.

### Security

- Does not leak session content, tool arguments, or LLM inputs/outputs.
- Endpoint runs behind whatever the app's actuator security policy is —
  mocapi doesn't override it. Most apps will put `/actuator/**` behind
  admin-only auth or a separate management port.

## Acceptance criteria

- [ ] Two new Maven modules: `mocapi-actuator` (code) and
      `mocapi-actuator-spring-boot-starter` (no-code aggregator).
      Endpoint autoconfig is in `mocapi-actuator`, conditional on the
      actuator autoconfig being present.
- [ ] `@WebEndpoint(id = "mcp")` with the sub-paths above.
- [ ] Response shape is stable JSON — covered by contract-style tests that
      fail if a field is renamed.
- [ ] Endpoint is discoverable at `/actuator/mcp` after adding the starter
      and setting `management.endpoints.web.exposure.include=mcp` (or
      `*`).
- [ ] No session content, tool input, tool output, or user-identifying
      data appears in any response.
- [ ] Metrics integration is wired only when `MeterRegistry` bean is
      present — otherwise the field is omitted and tests assert no
      `NoClassDefFoundError`.
- [ ] Documentation page explaining the endpoint, the JSON shape, and a
      warning about exposure (lock it down in prod).
- [ ] Both modules added to `mocapi-bom`.

## Implementation notes

- `@WebEndpoint` vs `@Endpoint`: use `@WebEndpoint` so the response content
  type and web-specific selectors work. Spring Boot actuator doc:
  https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.custom
- Session listing pulls from `McpSessionService` — add a read-only
  `Collection<McpSessionMetadata> listSessions()` method if one isn't
  already there. Metadata record is `{id, initialized, logLevel,
  createdAt, lastActivityAt}` — no payload access.
- Schema digest is `sha256(tool.descriptor().inputSchema().toString())`,
  truncated to 16 hex chars. Just for at-a-glance change detection.
- Depends on spec 169 for the metrics sub-section — order spec 169 first.
  If spec 169 isn't done yet, the endpoint still ships without the
  metrics field.
