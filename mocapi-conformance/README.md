# mocapi-conformance

MCP protocol conformance server for Mocapi.

This module is a runnable Spring Boot application wired up with a set
of tools, prompts, and resources that cover every scenario in the
official [`@modelcontextprotocol/conformance`](https://www.npmjs.com/package/@modelcontextprotocol/conformance)
npx tool. It is not published to Maven Central — its only purpose is to
serve as a target for conformance runs against a known-good set of
annotated beans.

## Running the conformance suite

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
| `resources-list` | — (protocol-level) |
| `resources-read-text` | `Static Text Resource` |
| `resources-read-binary` | `Static Binary Resource` |
| `resources-templates-list` | — (protocol-level) |
| `resources-read-template` | `Template Resource` |
| `resources-subscribe` | `Watched Resource` |
| `prompts-list` | — (protocol-level) |
| `prompts-get-simple` | `test_simple_prompt` |
| `prompts-get-with-arguments` | `test_prompt_with_arguments` |
| `prompts-get-with-embedded-resource` | `test_prompt_with_embedded_resource` |
| `prompts-get-with-image` | `test_prompt_with_image` |

## Adding new conformance scenarios

1. Add a method to the appropriate `Compatibility*.java` bean (tool, prompt, or resource) with the right annotation.
2. Add Javadoc referencing the npx scenario name and linking to the relevant MCP spec section:
   ```java
   /**
    * Conformance tool for the {@code scenario-name} scenario.
    *
    * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools Specification</a>
    */
   ```
3. Re-run the npx conformance suite locally to confirm it passes and add the scenario to the table above.
