# Compat module setup and test harness

## What to build

Create the `mocapi-compat` Maven module and the shared test infrastructure that
all subsequent compatibility specs will build on. No actual compliance tests yet —
just the scaffolding.

### Module setup

Create `mocapi-compat` as a new Maven module under the parent pom.

- `<maven.deploy.skip>true</maven.deploy.skip>` — never publishes
- Depends on `mocapi-core` (compile scope)
- Depends on `spring-boot-starter-test` and `spring-boot-starter-web` (test scope)
- No Substrate backend dependencies — in-memory fallbacks auto-wire
- Add to parent pom `<modules>` list

### Test application

A minimal `@SpringBootApplication` in `src/test/java` with:

- An echo tool (`@ToolService`) that returns its input as structured content:
  ```java
  @Tool("echo")
  public Map<String, Object> echo(String message) {
      return Map.of("message", message);
  }
  ```
- A tool that always throws a `JsonRpcException`
- Generate a random 32-byte master key at test startup. Use an
  `ApplicationContextInitializer` or `@DynamicPropertySource` to set
  `mocapi.master-key` to a random hex string before the context loads.
  Do NOT hardcode a key in config files.

### Test helper: `McpClient`

A plain Java utility class wrapping MockMvc that speaks the MCP protocol.
Every subsequent compat spec will use this helper.

Methods:

- `McpClient(MockMvc mockMvc)` — constructor
- `String initialize()` — sends initialize with default client info, returns session ID
- `ResultActions post(String sessionId, String method, ObjectNode params, JsonNode id)` — sends a JSON-RPC call with correct headers
- `ResultActions notify(String sessionId, String method, ObjectNode params)` — sends a notification with correct headers
- `ResultActions delete(String sessionId)` — sends DELETE with session header
- `ResultActions get(String sessionId)` — sends GET for SSE stream
- `ResultActions postRaw(String accept, String sessionId, String body)` — raw POST for testing malformed requests

All methods set `Accept: application/json, text/event-stream`, `Content-Type: application/json`,
and `MCP-Protocol-Version: 2025-11-25` by default (except `postRaw` which takes explicit headers).

### Smoke test

One test that verifies the harness works: initialize a session, ping it, delete it.
This proves the in-memory stack wires correctly and `McpClient` works end-to-end.

## Acceptance criteria

- [ ] `mocapi-compat` module exists with `deploy.skip=true`
- [ ] Module is in parent pom `<modules>`
- [ ] Test application boots with in-memory Substrate
- [ ] Echo tool and error tool are registered
- [ ] `McpClient` helper class exists with all methods listed above
- [ ] Smoke test: initialize → ping → delete works end-to-end
- [ ] `mvn verify` passes (all modules including mocapi-compat)

## Implementation notes

- Use `@SpringBootTest` + `@AutoConfigureMockMvc` on test classes
- The Substrate in-memory fallbacks log warnings at startup — that's expected
- The random master key ensures tests don't depend on a shared secret
- The echo tool return type should match the `CallToolResponse` shape that
  `ToolsRegistry.callTool()` expects
- Look at `mocapi-example` for reference on how tools are structured
