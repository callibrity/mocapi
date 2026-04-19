# mocapi-actuator: /actuator/mcp endpoint core (handlers only)

## What to build

A Spring Boot Actuator endpoint at `/actuator/mcp` that reports server
info and the handler catalog. This spec covers the endpoint shell and
tools/prompts/resources sub-resources; metrics integration is in spec
174.

### Scope note — no session data

Mocapi is a multi-node architecture: `McpSessionService` is backed by
Substrate (Redis / NATS / etc.) in production. A single node reporting
"sessions" via its own actuator is either expensive (cross-node
fan-out), inconsistent (each node sees a subset), or a cross-tenant
leak. **This endpoint does not expose session listings or counts.**
Operators that need session visibility build that separately from
their backing store's native tooling.

### Module layout

```
mocapi-actuator/
  pom.xml
  src/main/java/com/callibrity/mocapi/actuator/McpEndpoint.java
  src/main/java/com/callibrity/mocapi/actuator/McpActuatorAutoConfiguration.java
  src/main/java/com/callibrity/mocapi/actuator/dto/EndpointSnapshot.java
  src/main/java/com/callibrity/mocapi/actuator/dto/ServerInfo.java
  src/main/java/com/callibrity/mocapi/actuator/dto/HandlerSummary.java
  src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/...
```

### pom.xml

- `artifactId`: `mocapi-actuator`
- Dependencies: `mocapi-api`, `mocapi-server`,
  `spring-boot-starter-actuator`.
- Add to parent `<modules>` and `mocapi-bom`.

### Class: `com.callibrity.mocapi.actuator.McpEndpoint`

```java
@Component
@WebEndpoint(id = "mcp")
public class McpEndpoint {

  private final McpToolsService toolsService;
  private final McpPromptsService promptsService;
  private final McpResourcesService resourcesService;
  private final McpResourceTemplatesService resourceTemplatesService;
  private final BuildProperties buildProperties; // optional — @Nullable

  @ReadOperation
  public EndpointSnapshot top() {
    return new EndpointSnapshot(
        serverInfo(),
        handlerCounts(),
        uptime()
    );
  }

  @ReadOperation
  public List<HandlerSummary> tools(@Selector String path) {
    // path == "tools" → all tools; path starting "tools/" → single by name
    // (or use a separate @ReadOperation method with a nested path selector)
  }
}
```

Sub-operations: `tools`, `tools/{name}`, `prompts`, `prompts/{name}`,
`resources`, `resources/{uriOrEncodedUri}`, `resourceTemplates`,
`resourceTemplates/{name}`. Follow whatever selector syntax
`@WebEndpoint` supports cleanly — multiple `@ReadOperation` methods
with distinct selectors is fine.

### DTO: `EndpointSnapshot`

```java
public record EndpointSnapshot(
    ServerInfo server,
    Map<String, Integer> handlers,   // {tools, prompts, resources, resourceTemplates}
    String uptime                    // ISO-8601 Duration
) {}
```

### DTO: `ServerInfo`

```java
public record ServerInfo(
    String name,
    String title,
    String version,
    String mcpProtocolVersion
) {}
```

Values come from the configured `mocapi.server-*` properties and the
compiled MCP protocol version constant (`McpProtocol.VERSION` or
equivalent).

### DTO: `HandlerSummary`

```java
public record HandlerSummary(
    String name,
    String title,
    String description,
    String inputSchemaDigest,   // 16-char hex of SHA-256(inputSchema)
    String outputSchemaDigest   // 16-char hex; null if no output schema
) {}
```

For single-handler detail views (`/tools/{name}`), a separate
`HandlerDetail` DTO adds the expanded schemas:

```java
public record HandlerDetail(
    HandlerSummary summary,
    JsonNode inputSchema,
    JsonNode outputSchema
) {}
```

### Service additions

- Each paginated service (`McpToolsService`, `McpPromptsService`,
  `McpResourcesService`, `McpResourceTemplatesService`) already has
  `allDescriptors()` (added in an earlier change) — use it.
- `McpSessionService` is **not** touched by this spec; no session data
  is exposed (see the scope note at the top).

### Autoconfig

```java
@AutoConfiguration
@ConditionalOnClass(Endpoint.class)
@ConditionalOnAvailableEndpoint(endpoint = McpEndpoint.class)
class McpActuatorAutoConfiguration {
  @Bean
  McpEndpoint mcpEndpoint(...) { return new McpEndpoint(...); }
}
```

Registered via `AutoConfiguration.imports`.

### Tests

- Unit tests for schema-digest calculation (deterministic for a given
  schema, differs when schema differs).
- Unit test for `McpEndpoint.top()` with mocked services — asserts the
  exact JSON shape.
- Integration test booting a minimal Spring app with one tool, one
  session, hitting `/actuator/mcp` via `TestRestTemplate` — asserts the
  response JSON matches a golden fixture.
- Test that session tool-input content *never* appears in any response
  body (assertion on `SessionSummary` fields only).

## Acceptance criteria

- [ ] `mocapi-actuator` module exists with the layout above.
- [ ] `McpEndpoint` exposes the seven sub-operations.
- [ ] DTOs are immutable records; never leak `McpTool` / `McpPrompt` /
      `McpResource` impl references.
- [ ] Response shape matches the spec's JSON examples exactly (golden
      test).
- [ ] Session listing contains only metadata — no tool inputs, outputs,
      or other user data.
- [ ] Autoconfig triggered only when actuator + the mcp endpoint are
      exposed.
- [ ] `mvn verify` green.
- [ ] Module in `mocapi-bom`.

## Docs

- [ ] `CHANGELOG.md` `## [Unreleased]` / `### Added`: entry describing
      the new `/actuator/mcp` endpoint and what it surfaces.
- [ ] `docs/actuator.md` (new) describing the endpoint shape and a
      production-security warning (expose only to admin auth / separate
      management port).

## Commit

Suggested commit message:

```
Add /actuator/mcp endpoint exposing handler catalog + sessions

New mocapi-actuator module contributes an @WebEndpoint(id = "mcp")
that reports server info, handler counts with schema digests, and
active session metadata. Never leaks session content or tool
arguments. Metrics integration is the next spec.
```
