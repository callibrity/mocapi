# mocapi-actuator-spring-boot-starter: `/actuator/mcp` introspection endpoint

## What to build

Ship `mocapi-actuator-spring-boot-starter`: a Spring Boot Actuator
endpoint at `/actuator/mcp` that returns a read-only snapshot of
what the running mocapi instance exposes. Lets ops / platform teams
see "what tools, prompts, and resources does this node ship?"
without guessing from logs or poking the MCP protocol itself.

### Why

- **Discoverability.** Operations teams inheriting an mocapi
  deployment want a straight answer to "what's running on this
  node?" — a built-in actuator endpoint is the standard shape for
  that in Spring Boot land.
- **Debugging production.** When a tool call fails, the first
  question is "is that tool even registered?" A quick
  `curl https://host/actuator/mcp` answers it without hitting the
  MCP endpoint (which may require an authenticated MCP session).
- **Non-invasive.** Purely introspective. Doesn't touch sessions,
  doesn't call handlers, doesn't mutate anything. Safe to expose
  to any caller the actuator security config already trusts.
- **The customizer SPI (spec 180) made the handler inventory
  trivially available** — each autoconfig already has a list of
  `CallToolHandler` / `GetPromptHandler` / `ReadResourceHandler` /
  `ReadResourceTemplateHandler` instances with descriptor, method,
  and bean readers.

### Endpoint shape

`GET /actuator/mcp` returns a JSON document:

```json
{
  "server": {
    "name": "mocapi",
    "version": "0.11.0",
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
      "outputSchemaDigest": "sha256:9c41..."
    }
  ],
  "prompts": [
    {
      "name": "summarize",
      "title": "Summarize",
      "description": "Summarize arbitrary text",
      "arguments": [
        { "name": "text", "required": true }
      ]
    }
  ],
  "resources": [
    {
      "uri": "docs://readme",
      "name": "README",
      "description": "Project README",
      "mimeType": "text/markdown"
    }
  ],
  "resourceTemplates": [
    {
      "uriTemplate": "docs://pages/{slug}",
      "name": "Page",
      "description": "Documentation page by slug",
      "mimeType": "text/markdown"
    }
  ]
}
```

**Schema digest, not full schema.** Tool input/output schemas can
be large; the endpoint publishes a SHA-256 digest of the generated
JSON Schema so deployments / clients can detect drift without
bloating the response. If users want the full schema, it's already
available via MCP protocol (`tools/list` → `inputSchema`).

**No session state.** Mocapi is multi-node; sessions live in the
backing store (Redis / PG / NATS / Hazelcast) and no single node
has the global picture. Querying the store to assemble a
cluster-wide session count is out of scope for this endpoint
(would need a clustered store query on every hit — wrong shape for
an actuator snapshot). If individual session introspection becomes
a real ask later, that's a separate endpoint.

**No metrics snapshot fold-in.** The observability roadmap draft
mentioned folding metrics into the endpoint when
`mocapi-o11y-spring-boot-starter` is present. Skipping that —
metrics are already exposed on `/actuator/metrics` + `/actuator/prometheus`,
and tools like Prometheus / Grafana want them there, not
hand-assembled into our custom endpoint. Keep `/actuator/mcp`
focused on inventory.

### Module layout

```
mocapi-actuator-spring-boot-starter/
  pom.xml
  src/main/java/com/callibrity/mocapi/actuator/
    McpActuatorEndpoint.java            — the @Endpoint class
    McpActuatorSnapshot.java            — top-level response record
    McpActuatorSnapshots.java           — builder/pure helpers
  src/main/java/com/callibrity/mocapi/actuator/autoconfigure/
    MocapiActuatorAutoConfiguration.java
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

No separate non-starter module — the endpoint is tightly coupled to
Spring Boot Actuator infrastructure (`@Endpoint`,
`@ReadOperation`), so there's no "pure-java" variant to extract.

### Dependencies

- `com.callibrity.mocapi:mocapi-server` (per spec 179 pattern —
  not a transport starter)
- `org.springframework.boot:spring-boot-starter-actuator`
- `com.callibrity.mocapi:mocapi-model` (transitive via mocapi-server)

### Activation rule

```java
@AutoConfiguration
@ConditionalOnClass(Endpoint.class)
@ConditionalOnAvailableEndpoint(endpoint = McpActuatorEndpoint.class)
public class MocapiActuatorAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public McpActuatorEndpoint mcpActuatorEndpoint(
            McpToolsService tools,
            McpPromptsService prompts,
            McpResourcesService resources,
            McpResourceTemplatesService resourceTemplates,
            BuildProperties buildProperties /* optional */) {
        ...
    }
}
```

- `@ConditionalOnAvailableEndpoint` respects the standard
  `management.endpoints.web.exposure.include` configuration —
  users still choose to expose it.
- `BuildProperties` is optional; if absent, the `server.version`
  field falls back to "unknown" (or is omitted).

### Response assembly

`McpActuatorEndpoint` constructor takes the four paginated
services (already in `mocapi-server`: `McpToolsService`,
`McpPromptsService`, etc.). Each exposes
`allDescriptors()` (already public via `PaginatedService`) —
the endpoint iterates the descriptor lists, maps to the actuator
response shape, computes schema digests for tools.

Schema digest helper: SHA-256 of the canonicalized JSON text
(`ObjectMapper.writeValueAsBytes(schema)`), hex-encoded, prefixed
`sha256:`. Canonicalization = whatever Jackson's default ordering
produces; good enough for "same schema" detection.

### Security

The endpoint exposes handler names + descriptions only (plus
schema digests, not schemas). Everything in there is *also*
available via the MCP protocol itself (anyone with a valid MCP
session can call `tools/list` and see the same info). So
`/actuator/mcp` doesn't leak anything that isn't already
protocol-public.

Users who want to restrict who can hit the actuator endpoint
configure that via standard Spring Security +
`management.endpoints.web.exposure.include` / actuator security —
mocapi neither loosens nor tightens that.

## Acceptance criteria

- [ ] New module `mocapi-actuator-spring-boot-starter` under root
      `pom.xml` modules list.
- [ ] `mocapi-bom` entry for the new starter.
- [ ] `@Endpoint(id = "mcp")` with a single `@ReadOperation`
      method returning `McpActuatorSnapshot`.
- [ ] Snapshot includes: server info block, counts block, per-kind
      descriptor lists (tools, prompts, resources, resource
      templates) — exact shape matches the JSON example above.
- [ ] Tool entries carry `inputSchemaDigest` and (when present)
      `outputSchemaDigest` — SHA-256 hex prefixed `sha256:`.
- [ ] `MocapiActuatorAutoConfiguration` guarded by
      `@ConditionalOnClass(Endpoint.class)` and
      `@ConditionalOnAvailableEndpoint`.
- [ ] `BuildProperties` is an optional dependency —
      `server.version` field populates when it's present,
      falls back to a sensible string otherwise.
- [ ] Unit test: pass stub service beans with known descriptors,
      invoke the endpoint, assert the response shape and counts.
- [ ] Integration test with `ApplicationContextRunner`: context
      with + without actuator autoconfig, assert the bean exists
      when expected.
- [ ] MockMvc test: hit `/actuator/mcp` with exposure set to
      include `mcp`, assert 200 + JSON shape; with exposure not
      including `mcp`, assert 404.
- [ ] `README.md` "Modules" section gains a bullet for the new
      starter with a one-line description.
- [ ] `docs/observability-roadmap.md`: move the actuator bullet
      into the shipped section with a pointer to this spec's
      implementation.
- [ ] `CHANGELOG.md` `[Unreleased]` / `### Added`: entry for the
      new starter with a note on the `/actuator/mcp` endpoint
      shape.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Per-handler metrics snapshot inline in the response.** Users
  already have `/actuator/metrics` and `/actuator/prometheus` for
  that; duplicating metrics into our endpoint creates two sources
  of truth and clashes with standard observability tooling.
- **Session count / session state introspection.** Out of scope
  for this endpoint — would need clustered-store queries and
  doesn't fit the actuator-snapshot shape.
- **Write operations** (reload handlers, clear cache, etc.). This
  is a read-only introspection endpoint. Hot-reloading is a much
  bigger question that doesn't belong here.
- **Full schema bodies.** Only digests. MCP protocol already
  exposes full schemas via `tools/list`.
- **Non-Spring-Boot deployment shape.** The whole endpoint is
  Spring Boot Actuator-specific. No `mocapi-actuator` sibling
  module.

## Implementation notes

- `PaginatedService.allDescriptors()` is already public and
  returns the sorted descriptor list — direct fit for endpoint
  assembly.
- Schema digest helper is a handful of lines; keep it in
  `McpActuatorSnapshots` (package-private static methods).
- Consider exposing `McpActuatorSnapshot` as a public record so
  tests in downstream projects can assert against the shape by
  type rather than by JSON. Same for the per-kind detail records.
- This starter is the first consumer of `McpResourcesService` /
  `McpResourceTemplatesService` / `McpPromptsService` outside
  `mocapi-server` itself — double-check their visibility (they
  should already be public since they're autowired as beans).
