# `/actuator/mcp` Endpoint

`mocapi-actuator` adds a Spring Boot Actuator endpoint at
`/actuator/mcp` that returns a read-only snapshot of what the
running mocapi instance exposes: server info, handler counts, and
per-handler metadata.

Ops / platform teams use it to answer "what does this node ship?"
without hitting the MCP protocol itself. First question to a new
deployment, last question before a restart.

## Enabling

Add the module, pull in Spring Boot Actuator, and expose the
endpoint:

```xml
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```properties
management.endpoints.web.exposure.include=health,info,mcp
```

That's all. The endpoint activates when both `mocapi-actuator`
and `spring-boot-starter-actuator` are on the classpath.

## Response shape

```bash
curl -s localhost:8080/actuator/mcp | jq
```

```json
{
  "server": {
    "name": "mocapi",
    "version": "0.14.0",
    "protocolVersion": "2025-11-25"
  },
  "counts": {
    "tools": 12,
    "prompts": 4,
    "resources": 3,
    "resourceTemplates": 2
  },
  "tools": [
    {
      "name": "get_weather",
      "title": "Get Weather",
      "description": "Returns current weather for a ZIP code",
      "inputSchemaDigest": "sha256:a3f2...",
      "outputSchemaDigest": "sha256:9c41...",
      "handler": {
        "kind": "TOOL",
        "declaringClassName": "com.example.tools.WeatherTool",
        "methodName": "getWeather",
        "interceptors": [
          "Stamps SLF4J MDC correlation keys for tool 'get_weather'",
          "Records Micrometer 'mcp.handler.execution' observations (OpenTelemetry MCP semconv) for tool 'get_weather'",
          "Evaluates guards [RequiresScope(weather:read)] and rejects denied calls with JSON-RPC -32003 Forbidden",
          "Validates tool arguments against the tool's input JSON schema",
          "Validates method parameters and return value against Jakarta Bean Validation constraints"
        ]
      }
    }
  ],
  "prompts": [
    {
      "name": "summarize",
      "title": "Summarize",
      "description": "Summarize arbitrary text",
      "arguments": [ { "name": "text", "required": true } ],
      "handler": {
        "kind": "PROMPT",
        "declaringClassName": "com.example.prompts.SummarizePrompt",
        "methodName": "summarize",
        "interceptors": []
      }
    }
  ],
  "resources": [
    {
      "uri": "docs://readme",
      "name": "README",
      "description": "Project README",
      "mimeType": "text/markdown",
      "handler": {
        "kind": "RESOURCE",
        "declaringClassName": "com.example.docs.DocsResource",
        "methodName": "readme",
        "interceptors": []
      }
    }
  ],
  "resourceTemplates": [
    {
      "uriTemplate": "docs://pages/{slug}",
      "name": "Page",
      "description": "Documentation page by slug",
      "mimeType": "text/markdown",
      "handler": {
        "kind": "RESOURCE_TEMPLATE",
        "declaringClassName": "com.example.docs.DocsResource",
        "methodName": "page",
        "interceptors": []
      }
    }
  ]
}
```

### What's in there — and what isn't

**Included:**

- Server `name` / `version` / `protocolVersion`.
- Per-kind descriptor counts. **The counts reflect every registered
  handler, ignoring per-caller guard visibility** — this endpoint is
  an operator view. MCP clients see only handlers their guards allow
  via `tools/list` etc.
- Per-handler name, title, description.
- Tool `inputSchemaDigest` / `outputSchemaDigest` (SHA-256 of the
  canonical JSON schema, hex-encoded, prefixed `sha256:`).
- Prompt `arguments` list (name + required flag per arg).
- Resource `mimeType`.
- Per-handler `handler` block: `kind`, `declaringClassName`,
  `methodName`, and `interceptors` — the outer-to-inner toString
  sequence of every interceptor wrapping the reflective call. Reading
  the list top to bottom reconstructs the stratum chain (CORRELATION
  → OBSERVATION → AUDIT → AUTHORIZATION → VALIDATION → INVOCATION).
  See [`customizers.md`](customizers.md#strata) for the stratum model.

**Deliberately not included:**

- **Full input / output JSON schemas.** Only digests. Schemas can
  be large; MCP clients already get them via `tools/list` on the
  protocol endpoint. The digest is enough to detect drift across
  deployments ("did this tool's schema change?").
- **Session state.** Mocapi is multi-node; sessions live in the
  backing store (Redis / PostgreSQL / NATS / Hazelcast), not on
  any single node. Querying the store to count sessions cluster-
  wide would be a different endpoint with a different shape.
- **Metrics snapshot.** Metrics are already at
  `/actuator/metrics` and `/actuator/prometheus`. Duplicating
  them here would create two sources of truth.

## Schema-digest comparisons

The digest is a stable SHA-256 of the generated JSON schema
serialized with sorted keys. Two servers expose the same tool name
but different schemas? Compare the digests.

```bash
# Production
curl -s https://mocapi-prod/actuator/mcp | jq -r '.tools[] | "\(.name) \(.inputSchemaDigest)"' | sort

# Staging
curl -s https://mocapi-staging/actuator/mcp | jq -r '.tools[] | "\(.name) \(.inputSchemaDigest)"' | sort

# Diff
diff <(curl -s https://mocapi-prod/... | jq ...) <(curl -s https://mocapi-staging/... | jq ...)
```

Different digest → schema drift → one of the deployments is out of
sync.

## Security

The endpoint returns only what MCP clients can discover anyway via
`tools/list` etc. It doesn't expose internal implementation
details, wiring, or secrets. But the usual Actuator protections
apply:

- Control exposure via
  `management.endpoints.web.exposure.include` — only expose what
  you want discoverable.
- If you run Spring Security, consider adding a
  `SecurityFilterChain` rule for `/actuator/**` (requireAuthenticated
  or IP-range-restrict as you prefer). Mocapi's OAuth2 filter
  chain is scoped to the MCP endpoint and doesn't touch
  `/actuator` by default.

## Operational checks

Five-second health rituals:

```bash
# What's on this server?
curl -s localhost:8080/actuator/mcp | jq '.counts'

# Did my new tool register?
curl -s localhost:8080/actuator/mcp | jq '.tools[] | select(.name == "get_weather")'

# Schema drift vs. staging?
curl -s localhost:8080/actuator/mcp | jq '.tools[].inputSchemaDigest' | sort

# Is this server answering at all?
curl -s localhost:8080/actuator/health
```

## Related

- [`observability.md`](observability.md) — metrics + tracing +
  audit stack overview.
- [`configuration.md`](configuration.md) — all `mocapi.*` +
  `management.*` property references.
- [`perf/benchmarking.md`](perf/benchmarking.md) — periodic soak
  testing that uses `/actuator/metrics/mcp.tool*` to track
  throughput and latency trends.
