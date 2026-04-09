# Move conformance server to compat module and mirror npx tests

## What to build

Move all conformance tools from `mocapi-example` to `mocapi-compat`. The compat
module gets a `src/main/java` application that the `npx @modelcontextprotocol/conformance`
tool can run against directly. The IT tests in `src/test/java` mirror the npx
scenarios so we catch regressions without needing the external tool.

### Move conformance tools

Move `ConformanceTools.java` from `mocapi-example` to `mocapi-compat/src/main/java`.
Move `McpCompletionMethods` (if example-specific) similarly.

Clean up `mocapi-example` — it should only have `HelloTool`, `Rot13Tool`, and
`CountdownTool` as developer reference examples.

### Compat application in src/main/java

Create a runnable Spring Boot application in `mocapi-compat/src/main/java` that:
- Boots on port 8081 (or configurable)
- Registers all conformance tools
- Has a random master key generated at startup
- Can be started with `mvn spring-boot:run -pl mocapi-compat`

This is the server that `npx @modelcontextprotocol/conformance server --url http://localhost:8081/mcp`
tests against.

### Mirror npx conformance scenarios in ITs

For each npx conformance scenario that passes, create a corresponding IT test
in `mocapi-compat`. Name the test class and method to match the npx scenario:

- `ServerInitializeIT` — mirrors `server-initialize`
- `LoggingSetLevelIT` — mirrors `logging-set-level`
- `PingIT` — mirrors `ping`
- `CompletionCompleteIT` — mirrors `completion-complete`
- `ToolsListIT` — mirrors `tools-list`
- `ToolsCallSimpleTextIT` — mirrors `tools-call-simple-text`
- `ToolsCallImageIT` — mirrors `tools-call-image`
- `ToolsCallAudioIT` — mirrors `tools-call-audio`
- `ToolsCallEmbeddedResourceIT` — mirrors `tools-call-embedded-resource`
- `ToolsCallMixedContentIT` — mirrors `tools-call-mixed-content`
- `ToolsCallWithLoggingIT` — mirrors `tools-call-with-logging`
- `ToolsCallErrorIT` — mirrors `tools-call-error`
- `ToolsCallWithProgressIT` — mirrors `tools-call-with-progress`
- `ToolsCallSamplingIT` — mirrors `tools-call-sampling`
- `ToolsCallElicitationIT` — mirrors `tools-call-elicitation`
- `ElicitationSep1034DefaultsIT` — mirrors `elicitation-sep1034-defaults`
- `ElicitationSep1330EnumsIT` — mirrors `elicitation-sep1330-enums`
- `ServerSseMultipleStreamsIT` — mirrors `server-sse-multiple-streams`
- `DnsRebindingProtectionIT` — mirrors `dns-rebinding-protection`

Each IT should verify the same thing the npx scenario verifies. The existing
IT tests (from specs 043-050) should be kept — they cover additional cases
beyond what the npx suite tests.

### Keep existing ITs

The existing spec-section-based ITs (`PostEndpointIT`, `SessionManagementIT`,
`InitializationIT`, etc.) test things the npx suite doesn't cover (like
specific error codes, header validation, session lifecycle). Keep them.

## Acceptance criteria

- [ ] `ConformanceTools` moved from `mocapi-example` to `mocapi-compat/src/main/java`
- [ ] `mocapi-example` only has HelloTool, Rot13Tool, CountdownTool
- [ ] Compat module has a runnable Spring Boot application in `src/main/java`
- [ ] `npx @modelcontextprotocol/conformance` can run against the compat server
- [ ] IT tests mirror each passing npx scenario
- [ ] Existing spec-section ITs preserved
- [ ] `mvn verify` passes
