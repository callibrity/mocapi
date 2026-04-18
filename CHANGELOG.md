# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-04-17

### Added

- New `mocapi-transport-stdio` module — MCP transport over newline-delimited
  JSON-RPC on stdin/stdout, for clients that launch the server as a
  subprocess (Claude Desktop, Cursor, MCP Inspector). Enabled by
  `mocapi.stdio.enabled=true`; transport is off by default so non-stdio
  apps can't accidentally corrupt stdout.
- New `examples/stdio` example — minimal echo-tool server launchable by any
  MCP stdio client. Ships with a `logback-spring.xml` that routes all
  logging to stderr (stdout is reserved for protocol traffic) and a README
  covering Claude Desktop setup, native-image builds, and a curl-style
  smoke test.
- Project-wide test readability: every test class now uses
  `@DisplayNameGeneration(ReplaceUnderscores.class)` with snake_case method
  names and `Capitalized_with_underscores` `@Nested` groupings. IDE test
  output and CI reports read as natural sentences.

### Documentation

- Root README and `docs/architecture.md` rewritten to describe both
  transports (HTTP + stdio) rather than just HTTP. New "Transports"
  section in architecture.md compares when to use each.

## [0.5.0] - 2026-04-17

### Breaking changes

- `StreamableHttpController` constructor now takes `SseStreamFactory` instead
  of `Odyssey` and `masterKey` (it still takes `ObjectMapper` for error
  bodies). Applications providing a custom `StreamableHttpController` bean
  must switch to the new constructor signature; default auto-configuration
  continues to wire everything automatically.
- `StreamableHttpController.SESSION_ID_HEADER` moved to
  `StreamableHttpTransport.SESSION_ID_HEADER`. Callers referencing the
  constant by its fully qualified name must update the import.
- `SynchronousTransport` and `OdysseyTransport` are removed; both are
  subsumed by the new lazy `StreamableHttpTransport` (package-private).
- The SSE priming event (`PRIMING` event ID) previously sent on GET stream
  subscribe is gone. Clients that relied on a `Last-Event-ID` anchor
  appearing before any real events must switch to reconnecting with the
  `Last-Event-ID` of the most recent real event they received.

### Added

- Lazy HTTP transport: every `JsonRpcCall` POST now runs through a single
  transport that chooses JSON vs SSE based on the first outbound message.
  Tools that only return a response receive `Content-Type: application/json`;
  tools that emit progress, logging, elicitation, or sampling upgrade to
  `text/event-stream` on the first non-response `send()`.
- New `com.callibrity.mocapi.transport.http.writer` package with a sealed
  `MessageWriter` state machine — `DirectMessageWriter` (initial),
  `SseMessageWriter`, `ClosedMessageWriter`. Transitions are explicit; new
  response shapes can be added by introducing another `MessageWriter`.
- New `com.callibrity.mocapi.transport.http.sse` package exposing
  `SseStream`, `SseStreamFactory`, and `DefaultSseStreamFactory`. All SSE
  plumbing (stream creation, subscription, encrypting event mapper, resume)
  now lives behind the factory.
- TRACE-level logging on every writer transition
  (`Direct → Closed`, `Direct → SSE`, `SSE → SSE`, `SSE → Closed`, plus a
  `WARN` when a write is rejected against a closed writer). Enable with
  `logging.level.com.callibrity.mocapi.transport.http.writer=TRACE`.

### Changed

- `POST /mcp` handler now returns `CompletableFuture<ResponseEntity<Object>>`
  and runs every `JsonRpcCall` on a virtual thread. Spring MVC releases the
  servlet thread until the future resolves. Notification and response POSTs
  still 202 ACCEPTED synchronously.
- `Ciphers` utility moved from `com.callibrity.mocapi.transport.http` to
  `com.callibrity.mocapi.transport.http.sse`. Internal utility — the move is
  documented here for completeness.

### Documentation

- `docs/architecture.md` rewritten to describe the lazy transport and
  `MessageWriter` state machine.

## [0.4.1] - 2026-04-16

### Fixed

- Native-image builds failed at runtime when json-sKema tried to load its
  JSON Schema draft meta-schemas from the classpath, because the library
  does not yet ship `META-INF/native-image/reachability-metadata.json`.
  `MocapiRuntimeHints` now registers the `json-meta-schemas/**` resource
  patterns so those files survive a `native-image` build. Belt-and-
  suspenders once json-sKema publishes its own metadata.

## [0.4.0] - 2026-04-16

### Added

- Spring AOT hints so Mocapi apps can be compiled with GraalVM
  `native-image` without hand-maintaining a reflection config.
  `MocapiServicesAotProcessor` (a `BeanRegistrationAotProcessor`)
  walks every `@ToolService`, `@PromptService`, and `@ResourceService`
  bean, registering `ExecutableMode.INVOKE` hints on each
  `@ToolMethod`, `@PromptMethod`, `@ResourceMethod`, and
  `@ResourceTemplateMethod` plus Jackson binding hints on every
  parameter type and non-void return type. `MocapiRuntimeHints` (a
  `RuntimeHintsRegistrar`) registers binding hints for `McpSession`
  and scans `com.callibrity.mocapi.model` at AOT build time so every
  wire-envelope type, sealed-hierarchy permit, and future model
  addition is covered automatically. Hooks wired via
  `META-INF/spring/aot.factories`; no-op for JIT builds.
- `docs/native-image-hints.md` documents what Mocapi ships, which
  surfaces Spring AOT handles on its own, and the cowork-connector
  verification recipe under the GraalVM tracing agent.

### Requirements

- Ripcurl bumped from 2.3.0 to 2.5.0, which introduces Ripcurl's own
  AOT processor for `@JsonRpcService` beans and a `RuntimeHintsRegistrar`
  for the `JsonRpcMessage` sealed hierarchy. Mocapi's own hints rely on
  Ripcurl carrying its half of the AOT story.
- Odyssey bumped from 0.9.0 to 0.10.0, which ships AOT hints for its
  own journaling internals. No API changes required in Mocapi.
- Substrate bumped from 0.6.0 to 0.7.0 (transitive with Odyssey 0.10.0).
  No API changes required in Mocapi.

## [0.3.0] - 2026-04-15

### Changed

- Migrated to Odyssey 0.9.0. Per-stream operations are now expressed via
  an `OdysseyStream<T>` handle obtained from `Odyssey.stream(name, type)`;
  the old `Odyssey.publisher/subscribe/resume` facade methods are gone.
  `OdysseyTransport` now wraps an `OdysseyStream` and the controller uses
  one handle per request (publish and subscribe on the same handle).
- SSE priming event for tool-call streams is now emitted through the new
  `SubscriberConfig.onSubscribe` hook, which fires after the writer loop
  opens the connection and before any journal events. Closes a
  previously-latent race where the priming event could land after an
  early journal event.

### Requirements

- Odyssey bumped from 0.8.1 to 0.9.0.
- Substrate bumped from 0.4.0 to 0.6.0 (transitive with Odyssey 0.9.0).
  Substrate now throws a distinct `AtomNotFoundException` for operations
  targeting a never-existed or fully-evicted atom, separate from
  `AtomExpiredException`. `AtomMcpSessionStore` now catches both on
  `update`, `find`, `touch`, and `delete`, preserving the previous
  "session is gone, no-op" behavior.

## [0.2.0] - 2026-04-14

### Added

- `ReadResourceResult.ofText(uri, mimeType, text)` and
  `ReadResourceResult.ofBlob(uri, mimeType, ...)` static factories for the
  common single-entry case. The `byte[]` overload base64-encodes
  automatically.
- `BlobResourceContents.of(uri, mimeType, byte[] bytes)` static factory
  handling base64 encoding in one place, reusable when assembling
  multi-entry `ReadResourceResult`s directly.
- Compat server and resources guide updated to use the new factories.
- `PromptTemplate` and `PromptTemplateFactory` interfaces in `mocapi-api`
  (`com.callibrity.mocapi.api.prompts.template`) for engine-agnostic prompt
  templating. Factory takes raw `String` template source; implementations
  compile and cache.
- `mocapi-prompts-mustache` module providing a JMustache-backed
  `PromptTemplateFactory`. Uses the identity escaper (prompt text is LLM
  input, not browser output) and treats missing keys as empty strings by
  default. Ships an auto-config gated on `com.samskivert.mustache.Mustache`
  presence; user-supplied `PromptTemplateFactory` beans take precedence.
- `mocapi-prompts-spring` module providing a `PromptTemplateFactory` backed
  by Spring's own `PropertyPlaceholderHelper`. Syntax is `${name}` with
  `${name:default}` fallbacks and `\${name}` escaping. Zero new dependencies
  for Spring Boot apps — `spring-core` is already on the classpath.
- Annotation-driven prompt registration via `@PromptService` + `@PromptMethod`. Method
  parameters bind from the incoming argument map via Spring's `ConversionService`,
  supporting strings, primitives, enums, `java.time` types, and any custom
  `Converter<String, T>`. A `Map<String, String>` parameter receives the whole
  argument map.
- Annotation-driven resource registration via `@ResourceService` with two method-level
  annotations: `@ResourceMethod` for fixed URIs and `@ResourceTemplateMethod` for
  RFC 6570 URI templates. Path variables bind to method parameters by name using the
  same `StringMapArgResolver` as prompt arguments.
- `docs/prompts-guide.md` and `docs/resources-guide.md` documenting the new
  annotations with argument-binding, URI-template, and return-type coverage.
- README Quick Start now includes prompt and resource examples alongside tools.

### Changed

- Tool annotation scanning now builds a per-invoker Methodical resolver chain
  instead of relying on globally-registered `ParameterResolver` beans. Dropped the
  `@Bean`-exposed `McpToolContextResolver` and `McpToolParamsResolver` — the
  `ToolServiceMcpToolProvider` constructs them once and hands them to each
  `AnnotationMcpTool` invoker. No user-facing API change unless you were relying
  on those beans directly.
- Return-type and parameter-shape validation failures in the annotation scanners
  now throw `IllegalArgumentException` (previously `IllegalStateException`), since
  the violation is in the declared method signature.

### Documentation

- Configuration guide now includes commands (OpenSSL, Python, JShell) for
  generating a base64-encoded 32-byte AES-256 value suitable for
  `mocapi.session-encryption-master-key`.

### Requirements

- Methodical bumped from 0.3.0 to 0.4.0 for the per-invoker resolver API.

## [0.1.0] - 2026-04-14

Initial public release on Maven Central.

[Unreleased]: https://github.com/callibrity/mocapi/compare/0.6.0...HEAD
[0.6.0]: https://github.com/callibrity/mocapi/releases/tag/0.6.0
[0.5.0]: https://github.com/callibrity/mocapi/releases/tag/0.5.0
[0.4.1]: https://github.com/callibrity/mocapi/releases/tag/0.4.1
[0.4.0]: https://github.com/callibrity/mocapi/releases/tag/0.4.0
[0.3.0]: https://github.com/callibrity/mocapi/releases/tag/0.3.0
[0.2.0]: https://github.com/callibrity/mocapi/releases/tag/0.2.0
[0.1.0]: https://github.com/callibrity/mocapi/releases/tag/0.1.0
