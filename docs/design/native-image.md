# GraalVM native-image hints for mocapi

## Context

Exercised against `cowork-connector-example` (Spring Boot 4.0.5, Java 25) using the GraalVM tracing agent (`-agentlib:native-image-agent`). This document records what mocapi ships to make consuming apps native-image-ready and why — so the setup stays coherent as new model types land.

Reference companions in this family: `ripcurl/docs/native-image-hints.md`, `methodical/docs/native-image-hints.md`, `codec/docs/native-image-hints.md`.

## Agent-captured surface (82 entries)

When the cowork-connector-example was run under the tracing agent with `/mcp/**` opened up and every tool/prompt exercised, mocapi surfaced:

- `mocapi.server.autoconfigure.*` — 13 (auto-configs + `@ConfigurationProperties`)
- `mocapi.server.*` non-autoconfigure — 19 (framework service beans)
- `mocapi.transport.http.*` — 3 (controller, validator, auto-config)
- `mocapi.prompts.spring.*` — 2 (template factory + auto-config)
- `mocapi.api.*` annotations + SPI ifaces — 3 (`@McpTool`, `@McpPrompt`, `PromptTemplateFactory`)
- `mocapi.server.exchange.McpExchange` — 1
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

For every Spring bean whose class declares at least one `@McpTool`, `@McpPrompt`, `@McpResource`, or `@McpResourceTemplate` method, walks its declared methods. On each annotated method:

- `ExecutableMode.INVOKE` hint on the method itself (so the dispatcher's reflective call is legal in native).
- `BindingReflectionHints` on every parameter type (picks up enums, records, nested generics via Spring's registrar walker).
- `BindingReflectionHints` on the non-`void` return type.

Non-matching beans are skipped. No-op for JIT builds.

This handles **user code automatically** — downstream apps don't write hints for their own result records, arg records, or enums. The cowork example's `HelloResult`, `TodoItem`, `ListTodosResponse`, etc. all get covered through this processor.

### `MocapiRuntimeHints`

Registers binding hints for the mocapi-owned types that cross a Jackson codec boundary without appearing in a `@...Method` signature:

1. **Explicit non-model registrations** — types Jackson serializes at runtime that live *outside* `mocapi-model`, so the package scan below does not reach them:
   - **`McpExchange`** — the per-request protocol context record bound during dispatch.
   - **`RequestStatePayload`** — the MRTR `requestState` token payload. `RequestStateCodec` serializes it into the opaque, AES-256-GCM-encrypted `requestState` string and reads it back on replay; its `ResponseLedgerEntry` → `ElicitResult` graph is pulled in transitively. It lives in `...server.mrtr`, **not** `mocapi-model`, because the spec treats `requestState` as an opaque server-owned string (ADR-0021) — so it is deliberately not a wire type, yet still needs a hint or native-image elicitation replay breaks.
2. **Every class in `com.callibrity.mocapi.model` (and any future subpackage)** — scanned at AOT build time via Spring's `ClassPathScanningCandidateComponentProvider`. Covers tool/prompt/resource results (`CallToolResult`, `GetPromptResult`, `ListToolsResult`, …), descriptors (`Tool`, `Prompt`, `Resource`), sealed hierarchies (`ContentBlock`, `ResourceContents`), enums (`Role`, `LoggingLevel`), and arrays (`PromptArgument[]`, `Tool[]`, `Resource[]`) — ~92 types, no enumeration required.

The scanner is configured with `useDefaultFilters=false`, `isCandidateComponent` overridden to `return true`, and a pass-through include filter. That combination surfaces every class under the package — sealed interfaces, abstract classes, records, enums, and anything introduced in a subpackage in the future — without any per-release curation. New mocapi-model types are picked up automatically.

### What Spring AOT handles (no explicit hints needed)

- Every auto-config class and `@ConfigurationProperties` record — Spring Boot's AOT generates the binding code.
- Every framework Spring bean (`DefaultMcpServer`, `McpToolsService`, `StreamableHttpController`, etc.) — Spring AOT replaces reflective bean instantiation with generated factory code.
- Spring-owned reflective annotation discovery on method-level annotations (`@McpTool`, `@McpPrompt`, `@McpResource`, `@McpResourceTemplate`) — handled via merged-annotation pre-computation at AOT time.

## Tests

`mocapi-autoconfigure/src/test/java/.../aot/MocapiRuntimeHintsTest.java` asserts coverage on representative types:

- `McpExchange`
- `RequestStatePayload` — the MRTR `requestState` payload; it lives outside `mocapi-model`, so it needs an explicit assertion (the package scan would not catch its regression)
- Envelope results (`CallToolResult`, `GetPromptResult`, `ReadResourceResult`, `ListToolsResult`)
- Descriptors (`Tool`, `Prompt`, `Resource`, `ServerCapabilities`)
- Sealed hierarchies — `ContentBlock` + `TextContent`, `ResourceContents` + `TextResourceContents` + `BlobResourceContents`
- Nested (`PromptMessage`)

`mocapi-autoconfigure/src/test/java/.../aot/MocapiServicesAotProcessorTest.java` covers the per-bean processor.

When new model types land, these tests currently pass automatically because of the package scan — but it's worth adding an assertion for anything with a non-trivial shape (new sealed hierarchies especially) to catch regressions if the scan filters ever change.

## Extension modules own their hints

`MocapiRuntimeHints` only scans `com.callibrity.mocapi.model` — core has no
reason to know about extension-owned packages, and widening the scan to reach
into `mocapi-tasks` or `mocapi-apps` would leak extension knowledge into core
(a layering violation the same way a new SPI or transport dependency would
be). Each extension that introduces its own wire types crossing the Jackson
codec boundary is responsible for registering its own hints, following the
same `RuntimeHintsRegistrar` + `META-INF/spring/aot.factories` contribution
pattern `MocapiRuntimeHints` uses.

This gap was found empirically: a native-image build of `examples/tasks`
returned HTTP 500 on any `tools/call` that dispatched as a task, with
`com.oracle.svm.core.jdk.UnsupportedFeatureError: Record components not
available for record class com.callibrity.mocapi.tasks.model.CreateTaskResult`
in the server log. `mocapi-tasks`' wire records lived entirely outside
`MocapiRuntimeHints`' scan, so Jackson's record introspection had no
reflection metadata for them at runtime.

### `TasksRuntimeHints` (`mocapi-tasks`)

`com.callibrity.mocapi.tasks.aot.TasksRuntimeHints` scans
`com.callibrity.mocapi.tasks.model` — a dedicated model package, mirroring
core's `com.callibrity.mocapi.model` — using the same
`ClassPathScanningCandidateComponentProvider` configuration as
`MocapiRuntimeHints` (`useDefaultFilters=false`, `isCandidateComponent`
overridden to `true`, pass-through include filter). The scanner is copied
locally rather than shared from core, keeping core extension-agnostic.
Covers `CreateTaskResult`, `GetTaskResult`, `UpdateTaskResult`,
`CancelTaskResult`, their `*Params` counterparts, and the `TaskStatus` enum.

### `AppsRuntimeHints` (`mocapi-apps`)

`com.callibrity.mocapi.apps.aot.AppsRuntimeHints` takes a different shape
because `mocapi-apps` has no dedicated `.model` subpackage — its
`com.callibrity.mocapi.apps` package mixes wire records with annotations,
customizers, and services that never cross the Jackson codec boundary.
Rather than widen a package-wide scan to cover a handful of types, it
explicitly registers the three records `AppsToolUiMetaCustomizer` and
`AppsResourceUiMetaCustomizer` hand to `ObjectMapper#valueToTree`:
`McpUiToolMeta`, `UiResourceMeta`, and its nested `McpUiResourceCsp` — the
same explicit-registration style `MocapiRuntimeHints` uses for `McpExchange`
and `RequestStatePayload`. `AppsRuntimeHints` was added by symmetry with the
tasks fix (structurally identical `_meta` exposure via `valueToTree`) but has
not itself been exercised against a native rebuild.

## Verification

The cowork-connector-example at `~/IdeaProjects/cowork-connector-example` is the reference consumer. After publishing a mocapi candidate:

1. Bump `mocapi.version` in its pom.
2. `mvn -Pnative spring-boot:build-image -DBP_NATIVE_IMAGE=true`.
3. Run the resulting image and exercise `server/discover`, `tools/list`, each `tools/call`, `prompts/list`, each `prompts/get`, and — critically — a full **elicitation round-trip** (a tool that calls `ctx.elicit(...)`, then the client retry carrying `requestState` + answers). Elicitation replay is the only path that exercises the MRTR `requestState` codec, and thus the only way to catch a missing `RequestStatePayload` hint. A discover/tools/prompts-only smoke check would have shipped that gap.

If any call errors with `MissingReflectionRegistrationError` or a Jackson `InvalidDefinitionException`, the offending class tells you whether it's mocapi's responsibility (extend `MocapiRuntimeHints` or `MocapiServicesAotProcessor`) or a consumer's (file bug in the appropriate repo).
