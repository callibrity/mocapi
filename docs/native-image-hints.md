# GraalVM native-image hints for mocapi

## Context

Exercised against `cowork-connector-example` (Spring Boot 4.0.5, Java 25) using the GraalVM tracing agent (`-agentlib:native-image-agent`). This document records what mocapi ships to make consuming apps native-image-ready and why — so the setup stays coherent as new model types land.

Reference companions in this family: `ripcurl/docs/native-image-hints.md`, `substrate/docs/native-image-hints.md`, `odyssey/docs/native-image-hints.md`, `methodical/docs/native-image-hints.md`, `codec/docs/native-image-hints.md`.

## Agent-captured surface (82 entries)

When the cowork-connector-example was run under the tracing agent with `/mcp/**` opened up and every tool/prompt exercised, mocapi surfaced:

- `mocapi.server.autoconfigure.*` — 13 (auto-configs + `@ConfigurationProperties`)
- `mocapi.server.*` non-autoconfigure — 19 (framework service beans)
- `mocapi.transport.http.*` — 3 (controller, validator, auto-config)
- `mocapi.prompts.spring.*` — 2 (template factory + auto-config)
- `mocapi.api.*` annotations + SPI ifaces — 7 (`@ToolService`, `@ToolMethod`, `@PromptService`, `@PromptMethod`, `McpResourceProvider`, `McpResourceTemplateProvider`, `PromptTemplateFactory`)
- `mocapi.server.session.McpSession` — 1
- `mocapi.model.*` wire types — 36

## How coverage works

Two contributions in `mocapi-server/src/main/resources/META-INF/spring/aot.factories`:

```
org.springframework.beans.factory.aot.BeanRegistrationAotProcessor=\
com.callibrity.mocapi.server.autoconfigure.aot.MocapiServicesAotProcessor

org.springframework.aot.hint.RuntimeHintsRegistrar=\
com.callibrity.mocapi.server.autoconfigure.aot.MocapiRuntimeHints
```

### `MocapiServicesAotProcessor`

For every Spring bean annotated with `@ToolService`, `@PromptService`, or `@ResourceService`, walks its declared methods. On each `@ToolMethod` / `@PromptMethod` / `@ResourceMethod` / `@ResourceTemplateMethod`:

- `ExecutableMode.INVOKE` hint on the method itself (so the dispatcher's reflective call is legal in native).
- `BindingReflectionHints` on every parameter type (picks up enums, records, nested generics via Spring's registrar walker).
- `BindingReflectionHints` on the non-`void` return type.

Non-matching beans are skipped. No-op for JIT builds.

This handles **user code automatically** — downstream apps don't write hints for their own result records, arg records, or enums. The cowork example's `HelloResult`, `TodoItem`, `ListTodosResponse`, etc. all get covered through this processor.

### `MocapiRuntimeHints`

Registers binding hints for two sets of mocapi-owned types that cross a codec boundary without appearing in a `@...Method` signature:

1. **`McpSession`** — written to the substrate atom store by `AtomMcpSessionStore`. Explicit single-type registration.
2. **Every class in `com.callibrity.mocapi.model` (and any future subpackage)** — scanned at AOT build time via Spring's `ClassPathScanningCandidateComponentProvider`. Covers tool/prompt/resource results (`CallToolResult`, `GetPromptResult`, `ListToolsResult`, …), descriptors (`Tool`, `Prompt`, `Resource`), sealed hierarchies (`ContentBlock`, `ResourceContents`), enums (`Role`, `LoggingLevel`), and arrays (`PromptArgument[]`, `Tool[]`, `Resource[]`) — ~92 types, no enumeration required.

The scanner is configured with `useDefaultFilters=false`, `isCandidateComponent` overridden to `return true`, and a pass-through include filter. That combination surfaces every class under the package — sealed interfaces, abstract classes, records, enums, and anything introduced in a subpackage in the future — without any per-release curation. New mocapi-model types are picked up automatically.

### What Spring AOT handles (no explicit hints needed)

- Every auto-config class and `@ConfigurationProperties` record — Spring Boot's AOT generates the binding code.
- Every framework Spring bean (`DefaultMcpServer`, `McpToolsService`, `AtomMcpSessionStore`, `StreamableHttpController`, etc.) — Spring AOT replaces reflective bean instantiation with generated factory code.
- Spring-owned reflective annotation discovery on `@ToolService` / `@PromptService` / `@ResourceService` beans — handled via merged-annotation pre-computation at AOT time.

## Tests

`mocapi-server/src/test/java/.../aot/MocapiRuntimeHintsTest.java` asserts coverage on representative types:

- `McpSession`
- Envelope results (`CallToolResult`, `GetPromptResult`, `ReadResourceResult`, `ListToolsResult`)
- Descriptors (`Tool`, `Prompt`, `Resource`, `ServerCapabilities`)
- Sealed hierarchies — `ContentBlock` + `TextContent`, `ResourceContents` + `TextResourceContents` + `BlobResourceContents`
- Nested (`PromptMessage`)

`mocapi-server/src/test/java/.../aot/MocapiServicesAotProcessorTest.java` covers the per-bean processor.

When new model types land, these tests currently pass automatically because of the package scan — but it's worth adding an assertion for anything with a non-trivial shape (new sealed hierarchies especially) to catch regressions if the scan filters ever change.

## Verification

The cowork-connector-example at `~/IdeaProjects/cowork-connector-example` is the reference consumer. After publishing a mocapi candidate:

1. Bump `mocapi.version` in its pom.
2. `mvn -Pnative spring-boot:build-image -DBP_NATIVE_IMAGE=true`.
3. Run the resulting image and exercise `initialize`, `tools/list`, each `tools/call`, `prompts/list`, each `prompts/get`, and the session TTL path.

If any call errors with `MissingReflectionRegistrationError` or a Jackson `InvalidDefinitionException`, the offending class tells you whether it's mocapi's responsibility (extend `MocapiRuntimeHints` or `MocapiServicesAotProcessor`) or a consumer's (file bug in the appropriate repo).
