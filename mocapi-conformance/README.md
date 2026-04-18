# mocapi-conformance

MCP protocol conformance test suite for Mocapi.

This module verifies that Mocapi correctly implements the
[MCP 2025-11-25 specification](https://modelcontextprotocol.io/specification/2025-11-25)
using two complementary approaches:

1. **Internal integration tests** — Spring Boot MockMvc tests that validate protocol
   behavior (initialization, ping, tool discovery, tool invocation, SSE streaming,
   session management, content negotiation, etc.)
2. **External conformance suite** — the official
   [`@modelcontextprotocol/conformance`](https://www.npmjs.com/package/@modelcontextprotocol/conformance)
   npx tool, run against a live Mocapi server

## Running internal IT tests

```bash
mvn verify -pl mocapi-conformance
```

This runs all `*IT.java` integration tests via Maven Failsafe.

## Running the npx conformance suite

### 1. Start the conformance server

```bash
mvn spring-boot:run -pl mocapi-conformance
```

The server starts on port 8081 with the MCP endpoint at `/mcp`.

### 2. Run the conformance suite

```bash
npx @modelcontextprotocol/conformance server --url http://localhost:8081/mcp
```

## Current conformance status

### Passing scenarios

| Scenario | Tool |
|---|---|
| `server-initialize` | — (protocol-level) |
| `ping` | — (protocol-level) |
| `tools-list` | — (protocol-level) |
| `tools-call-simple-text` | `test_simple_text` |
| `tools-call-image` | `test_image_content` |
| `tools-call-audio` | `test_audio_content` |
| `tools-call-embedded-resource` | `test_embedded_resource` |
| `tools-call-mixed-content` | `test_multiple_content_types` |
| `tools-call-error` | `test_error_handling` |
| `tools-call-with-logging` | `test_tool_with_logging` |
| `tools-call-with-progress` | `test_tool_with_progress` |
| `tools-call-sampling` | `test_sampling` |
| `tools-call-elicitation` | `test_elicitation` |
| `logging-set-level` | — (protocol-level) |
| `completion-complete` | — (protocol-level) |
| `server-sse-multiple-streams` | — (protocol-level) |
| `dns-rebinding-protection` | — (protocol-level) |
| `elicitation-sep1034-defaults` | `test_elicitation_sep1034_defaults` |
| `elicitation-sep1330-enums` | `test_elicitation_sep1330_enums` |

### Pending scenarios

| Capability | Status |
|---|---|
| Resources (`resources/list`, `resources/read`) | Not yet implemented |
| Prompts (`prompts/list`, `prompts/get`) | Not yet implemented |

## Adding new conformance tools

1. Add a method to `ConformanceTools.java` annotated with `@Tool(name = "test_*", ...)`
2. Add Javadoc referencing the npx scenario name and linking to the relevant MCP spec section:
   ```java
   /**
    * Conformance tool for the {@code scenario-name} scenario.
    *
    * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools Specification</a>
    */
   ```
3. Add a corresponding `*IT.java` integration test in the `conformance` package
4. Run `mvn verify -pl mocapi-conformance` to confirm the test passes
