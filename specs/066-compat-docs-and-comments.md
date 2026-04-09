# Compat module documentation and conformance tool comments

## What to build

### Comments on conformance tools

Every conformance test tool class and method should have a Javadoc comment
that references the `@modelcontextprotocol/conformance` npx suite and the
specific scenario it satisfies. For example:

```java
/**
 * Conformance tool for {@code tools-call-simple-text} scenario.
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-11-25/server/tools">MCP Tools Specification</a>
 */
@Tool(name = "test_simple_text", ...)
```

### README.md for mocapi-compat

Create a `README.md` in the `mocapi-compat` module root that explains:

1. **What this module is** — MCP protocol conformance/compatibility test suite
2. **How to run the internal IT tests** — `mvn verify -pl mocapi-compat`
3. **How to run the npx conformance suite against us**:
   - Start the compat server: `mvn spring-boot:run -pl mocapi-compat`
   - Run the conformance suite: `npx @modelcontextprotocol/conformance server --url http://localhost:8081/mcp`
4. **Current conformance status** — which scenarios pass, which are pending
   (resources, prompts)
5. **How to add new conformance tools** — follow the naming convention
   (`test_*`), add Javadoc referencing the scenario name

## Acceptance criteria

- [ ] Every conformance tool method has Javadoc referencing the npx scenario name
- [ ] Javadoc includes `@see` link to the relevant MCP spec section
- [ ] `README.md` exists in `mocapi-compat` module root
- [ ] README has instructions for running IT tests
- [ ] README has instructions for running npx conformance suite
- [ ] README lists current pass/fail status
- [ ] `mvn verify` passes
