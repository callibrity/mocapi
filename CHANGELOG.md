# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Per-handler customizer SPI in `mocapi-server`. Eight new interfaces —
  `CallToolHandlerConfig` / `CallToolHandlerCustomizer`,
  `GetPromptHandlerConfig` / `GetPromptHandlerCustomizer`,
  `ReadResourceHandlerConfig` / `ReadResourceHandlerCustomizer`, and
  `ReadResourceTemplateHandlerConfig` / `ReadResourceTemplateHandlerCustomizer` —
  let starter authors (observability, entitlements, rate-limiting) attach a
  `MethodInterceptor` to an individual handler while reading that handler's
  descriptor, target method, and target bean. Each `*HandlersAutoConfiguration`
  autowires the matching `List<*HandlerCustomizer>` (optional) and runs every
  customizer once per handler before the `MethodInvoker` is built.
  Customizer-added interceptors run after any bean-level interceptors and before
  any kind-specific trailing interceptors (e.g. the tool input-schema
  validator). The existing autowired
  `List<MethodInterceptor<? super JsonNode>>` (and prompt / resource / template
  analogs) path is unchanged for this release; a follow-up will migrate
  `mocapi-logging`'s MDC interceptor onto the new SPI.

- New module pair `mocapi-logging` + `mocapi-logging-spring-boot-starter`:
  an SLF4J MDC correlation interceptor for MCP handler invocations. Adding
  the starter registers a single `McpMdcInterceptor` methodical interceptor
  that stamps the following MDC attributes for the duration of every
  `@McpTool` / `@McpPrompt` / `@McpResource` / `@McpResourceTemplate`
  invocation and removes them on the way out — pre-existing MDC state
  from upstream filters is preserved.

  | Key                | Value                                                                   |
  |--------------------|-------------------------------------------------------------------------|
  | `mcp.session`      | Current MCP session id (only set when a session is bound).              |
  | `mcp.handler.kind` | One of `tool`, `prompt`, `resource`, `resource_template`.               |
  | `mcp.handler.name` | Tool / prompt name, or resource URI / URI template.                     |
  | `mcp.request`      | Reserved for JSON-RPC request id; wired by a follow-up spec.            |

  Drop the starter on the classpath and every log line emitted during a
  handler call — including lines from user handler code — carries the
  correlation keys automatically. Remove the starter and the keys stop
  appearing. There is no mocapi API addition either way. See
  [`docs/logging.md`](docs/logging.md) for the logback pattern snippet and
  the virtual-threads caveat.

- New `HandlerKinds` utility in `mocapi-api`
  (`com.callibrity.mocapi.api.handlers`). Tiny annotation-introspection
  helper shared by the observability starters (logging today, metrics and
  tracing next) so every starter tags handlers with the same
  `tool` / `prompt` / `resource` / `resource_template` strings.

### Changed

- `mocapi-logging` MDC interceptor now attaches per-handler via the
  customizer SPI introduced in the previous entry, instead of riding
  the global `MethodInterceptor<? super ...>` bean-autowiring path.
  `MocapiLoggingAutoConfiguration` exposes four beans
  (`CallToolHandlerCustomizer`, `GetPromptHandlerCustomizer`,
  `ReadResourceHandlerCustomizer`,
  `ReadResourceTemplateHandlerCustomizer`); each reads the handler's
  descriptor name / uri / uriTemplate and attaches an
  `McpMdcInterceptor` with the kind and name baked in at startup.
  Same keys (`mcp.handler.kind`, `mcp.handler.name`, `mcp.session`),
  same scope (from handler-chain entry to exit), no user-visible
  behavior change. Side effect: the ripcurl JSON-RPC dispatch layer
  no longer runs MDC code — previously a `MethodInterceptor<Object>`
  bean was double-picked-up there and produced stale or null MDC
  values on ripcurl-internal log lines; those log lines no longer
  show MDC keys at all. The hot path also drops a per-call
  `HandlerKinds.kindOf` / `nameOf` reflective lookup.
- `mocapi-jakarta-validation-spring-boot-starter` and
  `mocapi-logging-spring-boot-starter` now depend on `mocapi-server`
  directly instead of pulling in
  `mocapi-streamable-http-spring-boot-starter` transitively. These
  feature starters are transport-agnostic — a stdio-only (or
  future-transport-only) consumer adding validation or MDC logging
  no longer drags the HTTP stack in for the ride. Migration: users
  who relied on these starters implicitly bringing the HTTP transport
  must now declare `mocapi-streamable-http-spring-boot-starter`
  explicitly. In practice most consumers already declare a transport
  starter, so this is a no-op for them.
  `mocapi-oauth2-spring-boot-starter` still depends on the HTTP
  starter — OAuth2 resource-server validation is HTTP-specific.

- Bumped Methodical to `0.6.0` and ripcurl to `2.7.0`. Methodical 0.6
  replaces the old stateful `MethodInvokerFactory` (which carried a
  per-factory resolver list) with a stateless factory plus a
  `Consumer<MethodInvokerConfig<A>>` customizer passed on each
  `create(...)` call; resolvers and interceptors are supplied per
  invoker. Every handler-discovery helper
  (`CallToolHandlers#discover`, `GetPromptHandlers#discover`,
  `ReadResourceHandlers#discover`,
  `ReadResourceTemplateHandlers#discover`) now threads the new
  customizer API and accepts a typed
  `List<MethodInterceptor<? super T>>` that each handler's invoker
  wraps around the reflective call. The four handler autoconfigs
  autowire that list from the Spring context
  (`@Autowired(required = false)`), so a downstream starter can ship
  cross-cutting behavior (MDC, tracing, metrics, rate limiting,
  entitlements) as a plain `MethodInterceptor` bean with no addition
  to any mocapi API — drop the starter on the classpath and it
  auto-wires into every handler of the matching kind. Tool
  input-schema validation moved out of `McpToolsService` into a
  per-handler `InputSchemaValidatingInterceptor` appended innermost
  per `CallToolHandler`; the service's `validateInput`,
  `getInputSchema`, and `inputSchemas` cache are gone. Output
  schemas are still not validated — they remain descriptive metadata
  clients read from `tools/list`. Jackson's `Jackson3ParameterResolver`
  moved from Methodical's retired factory-level wiring into the
  per-invoker tool resolver list (first-match-wins ordering:
  `McpToolContextResolver` and `McpToolParamsResolver` run before
  the greedy Jackson fallback). Jakarta validation wiring now goes
  through Methodical's new `JakartaValidationInterceptor` bean
  instead of the retired `MethodValidatorFactory` /
  `JakartaMethodValidatorFactory` SPI; it rides in on the same
  per-kind interceptor autowiring. No end-to-end behavior change —
  constraint violations still surface as `-32602` for prompts and
  resources and as `CallToolResult { isError: true }` for tools.

### Breaking changes

- Removed the class-level `@ToolService`, `@PromptService`, and
  `@ResourceService` annotations from `mocapi-api`. Handler discovery
  no longer short-circuits through class-level markers: a single pass
  over every bean in the `ApplicationContext` (centralized in the new
  `HandlerMethodsCache`) groups `(bean, method)` pairs by which
  method-level annotation they carry (`@McpTool`, `@McpPrompt`,
  `@McpResource`, `@McpResourceTemplate`), and each kind's handler
  autoconfig consumes its slice through a per-method
  `CallToolHandlers.build(...)` / `GetPromptHandlers.build(...)` /
  `ReadResourceHandlers.build(...)` / `ReadResourceTemplateHandlers.build(...)`
  helper. The old `discover(bean, …)` helpers that walked a bean's
  methods themselves are gone. Measured startup cost of the all-bean
  scan is sub-100 ms even for context sizes in the thousands —
  negligible vs. Spring Boot's own startup work. Migration is a
  three-annotation delete:
  ```
  -@ToolService
   @Component
   public class MyTools { … }
  ```
  Users who registered their handler class as a `@Bean` (not
  `@Component`) don't need to change anything — the method annotation
  is the opt-in, and any bean-hood mechanism is fine.

- Renamed the four method-level handler annotations to drop the
  `Method` suffix and adopt the `Mcp` domain prefix:
  `@ToolMethod` → `@McpTool`, `@PromptMethod` → `@McpPrompt`,
  `@ResourceMethod` → `@McpResource`,
  `@ResourceTemplateMethod` → `@McpResourceTemplate`. Packages are
  unchanged. The class-level `@ToolService` / `@PromptService` /
  `@ResourceService` annotations stay as-is. Purely cosmetic — no
  behavior change. Migration is a find-and-replace of four tokens:
  `sed -i '' -e 's/@ToolMethod/@McpTool/g' -e 's/@PromptMethod/@McpPrompt/g' -e 's/@ResourceTemplateMethod/@McpResourceTemplate/g' -e 's/@ResourceMethod/@McpResource/g'`
  (run the `ResourceTemplateMethod` replacement before
  `ResourceMethod` since the latter is a substring of the former).
- Removed the `McpResourceTemplate` and `McpResourceTemplateProvider`
  interfaces from `mocapi-api`. Resource-template discovery is purely
  annotation-driven: every `@ResourceTemplateMethod` on a
  `@ResourceService` bean produces a `ReadResourceTemplateHandler`
  (server-internal) that `McpResourcesService` dispatches to via a
  `Map<UriTemplate, ReadResourceTemplateHandler>` after fixed-URI
  lookup misses. No user code implemented these SPI types in
  practice — resource templates are declared with annotations, not
  by hand — so this change is source-invisible for typical
  applications. The internal `AnnotationMcpResourceTemplate` /
  `ResourceServiceMcpResourceTemplateProvider` classes are gone;
  their logic moved to
  `com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlers#discover`
  and `MocapiServerResourcesAutoConfiguration`. After this change
  mocapi has no public handler-SPI interfaces — tools, prompts,
  resources, and resource templates are all annotation-driven.

- Removed the `McpResource` and `McpResourceProvider` interfaces from
  `mocapi-api`. Fixed-URI resource discovery is purely
  annotation-driven: every `@ResourceMethod` on a `@ResourceService`
  bean produces a `ReadResourceHandler` (server-internal) that
  `McpResourcesService` dispatches to via a `Map<String,
  ReadResourceHandler>` keyed by URI. No user code implemented these
  SPI types in practice — resources are declared with annotations, not
  by hand — so this change is source-invisible for typical
  applications. The internal `AnnotationMcpResource` /
  `ResourceServiceMcpResourceProvider` classes are gone; their logic
  moved to
  `com.callibrity.mocapi.server.resources.ReadResourceHandlers#discover`
  and `MocapiServerResourcesAutoConfiguration`.

- Removed the `McpPrompt` and `McpPromptProvider` interfaces from
  `mocapi-api`. Prompt discovery is purely annotation-driven: every
  `@PromptMethod` on a `@PromptService` bean produces a
  `GetPromptHandler` (server-internal) that `McpPromptsService`
  dispatches to directly. No user code implemented these SPI types
  in practice — prompts are declared with annotations, not by hand
  — so this change is source-invisible for typical applications.
  The internal `AnnotationMcpPrompt` /
  `PromptServiceMcpPromptProvider` classes are gone; their logic
  moved to
  `com.callibrity.mocapi.server.prompts.GetPromptHandlers#discover`.

- Removed the `McpTool` and `McpToolProvider` interfaces from
  `mocapi-api`. Tool discovery is purely annotation-driven: every
  `@ToolMethod` on a `@ToolService` bean produces a
  `CallToolHandler` (server-internal) that `McpToolsService`
  dispatches to directly. No user code implemented these SPI types
  in practice — tools are declared with annotations, not by hand —
  so this change is source-invisible for typical applications. The
  internal `AnnotationMcpTool` / `ToolServiceMcpToolProvider`
  classes are gone; their logic moved to
  `com.callibrity.mocapi.server.tools.CallToolHandlers#discover`.

- `McpToolContext` no longer exposes the eight per-level log convenience
  methods (`debug`, `info`, `notice`, `warning`, `error`, `critical`,
  `alert`, `emergency`). Tools now obtain an SLF4J-shaped `McpLogger`
  via `ctx.logger(name)` (or `ctx.logger()` for one named after the
  current handler). The new logger supports `{}`-style parameterized
  formatting through SLF4J's `MessageFormatter`.

  Migration:

  ```java
  // Before
  ctx.info("catalog", "took " + ms + "ms");

  // After
  ctx.logger("catalog").info("took {}ms", ms);
  // or, to reuse the handler name:
  ctx.logger().info("took {}ms", ms);
  ```

  The underlying `ctx.log(LoggingLevel, String, String)` method is
  unchanged and remains available for callers that want to pick a
  level dynamically.

### Changed

- `CallToolHandlers#discover` and `GetPromptHandlers#discover` are
  now pure Java helpers with zero Spring imports. They take a
  single `@ToolService` / `@PromptService` bean directly and a
  `UnaryOperator<String>` value resolver instead of an
  `ApplicationContext` and Spring's `StringValueResolver`. The
  `MocapiServer*AutoConfiguration` classes own the bean scan and
  adapt the Spring resolver via `::resolveStringValue`. The helpers
  are server-internal — no downstream-facing API surface change.

## [0.10.0] - 2026-04-18

### Added

- Auto-derivation of `mocapi.oauth2.resource` from the Spring Boot
  `spring.security.oauth2.resourceserver.jwt.audiences` property.
  When `audiences` has exactly one entry and `mocapi.oauth2.resource`
  is unset, mocapi publishes that single audience as the RFC 9728
  protected-resource metadata `resource` field. Common case for
  Auth0 / Okta / Keycloak / Entra setups with one logical API.
  Removes the duplicate-string-in-two-properties ceremony that the
  0.9.0 configuration required.

### Changed

- **Startup-time invariant**: `mocapi.oauth2.resource` (whether
  explicitly set or auto-derived) must be a member of the configured
  `spring.security.oauth2.resourceserver.jwt.audiences` list. A
  mismatch fails at application start with a descriptive error
  naming both the rejected resource and the accepted audiences.
  Rationale: clients that follow the protected-resource metadata
  document request tokens bound to the advertised `resource`
  identifier; if that identifier isn't in the server's accepted
  audiences, every token the client obtains would be rejected
  during validation. Catching that at startup is cheaper than a
  silently-broken deployment where every MCP request returns 401.

### Notes

- `@NotBlank` is no longer enforced on `mocapi.oauth2.resource` —
  the property becomes optional with the auto-derivation handling
  the common case. Applications that were already setting the
  property with a matching audience keep working unchanged.
- Documentation (`docs/authorization.md`) updated with the
  minimum-configuration example (two Spring Boot properties, no
  `mocapi.oauth2.*` needed), the recommended `jwk-set-uri` pattern
  to skip Spring's OIDC discovery HTTP call at startup, and an
  explicit note about the resource-must-be-in-audiences invariant.

## [0.9.0] - 2026-04-18

### Added

- New `mocapi-jakarta-validation-spring-boot-starter` pom-only starter
  that turns on Jakarta Bean Validation across mocapi's reflective-
  dispatch surface. Bundles `spring-boot-starter-validation`,
  `methodical-jakarta-validation`, and `ripcurl-jakarta-validation`.
  Once on the classpath, `@NotBlank`/`@Size`/`@Pattern`/etc. on user
  `@ToolMethod` / `@PromptMethod` / `@ResourceTemplateMethod`
  parameters surface as MCP-spec-idiomatic errors per handler type:
  `tools/call` violations produce `CallToolResult.isError=true` (the
  spec's "Input validation errors" path for LLM self-correction),
  while `prompts/get` and `resources/read` violations produce JSON-RPC
  `-32602 Invalid params` with per-violation `{field, message}` detail
  in the response's `data` field. Mocapi's internal protocol handlers
  deliberately keep their hand-rolled checks so mocapi's own contract
  is always enforced regardless of consumer classpath — this starter
  is user-code-only. See `docs/validation.md` for setup and
  `examples/jakarta-validation` for a runnable app.
- New `mocapi-oauth2` module and `mocapi-oauth2-spring-boot-starter`
  that wire OAuth2 resource-server protection for the Streamable HTTP
  transport per MCP 2025-11-25 authorization. Adds bearer-token
  validation on the MCP endpoint, a `401 WWW-Authenticate` challenge
  with `resource_metadata="..."` (RFC 8707), and an RFC 9728
  protected-resource metadata document served at
  `/.well-known/oauth-protected-resource`. Token validation (decoder,
  issuer, audience) uses the standard Spring Boot
  `spring.security.oauth2.resourceserver.jwt.*` properties; mocapi
  fills in the metadata document from `mocapi.oauth2.*` with
  fall-back to the configured `issuer-uri`. Two extension hooks —
  `OAuth2ProtectedResourceMetadataCustomizer` and
  `MocapiOAuth2SecurityFilterChainCustomizer` — let apps append
  claims or layer authorization rules without redeclaring the chain.
  End-to-end integration test bundles Spring Authorization Server
  in-process to verify the full `client_credentials` → bearer → `/mcp`
  flow. See `docs/authorization.md` for setup.
- Opaque (non-JWT) access token support in `mocapi-oauth2`. When
  `spring.security.oauth2.resourceserver.opaquetoken.*` is configured
  instead of `jwt.*`, the chain switches to RFC 7662 introspection.
  Mocapi wraps Spring's `OpaqueTokenIntrospector` with an
  audience-enforcing decorator so the MCP-mandated `aud` check still
  runs (Spring's opaque path does not include an audience validator
  by default). JWT and opaque modes are mutually exclusive; JWT wins
  if both happen to be configured.

## [0.8.0] - 2026-04-18

### Added

- MCP `completion/complete` support. Mocapi now advertises the
  `completions` server capability and responds to
  `completion/complete` with prefix-filtered suggestions drawn from
  the argument metadata it already has. A `@PromptMethod` parameter
  typed as a Java `enum`, or a `String` parameter annotated with
  `@Schema(allowableValues = {...})`, automatically surfaces its
  candidate values as completions. The same applies to
  `@ResourceTemplateMethod` URI-template variables. Prompts that
  take a whole `Map<String, String>` and plain `String`/number
  arguments contribute no candidates — the response is simply empty,
  matching the MCP spec's "best effort" wording. Up to 100 values
  per response. No new user-facing API: existing enum-typed
  parameters get completion for free. Covers the `completion-complete`
  conformance scenario that previously failed; conformance is now
  37/39 (the remaining failures are `resources-subscribe` and
  `resources-unsubscribe`).
- New `mocapi-bom` module — a Bill of Materials that pins every
  mocapi artifact version in one place. Import it into your own
  `dependencyManagement` with `<scope>import</scope>` and then omit
  `<version>` on individual mocapi dependencies. Useful once you depend
  on more than one coordinate (e.g., a starter plus `mocapi-prompts-mustache`).

### Fixed

- `central-publishing-maven-plugin` ignores the standard
  `maven.deploy.skip` property, which meant the 0.7.0 release
  accidentally pushed every example module and `mocapi-conformance`
  to Maven Central. Added release-profile `<skipPublishing>true</skipPublishing>`
  overrides in `examples/pom.xml` (cascades to every example) and in
  `mocapi-conformance/pom.xml`. From 0.8.0 forward only the real
  library artifacts (plus the new `mocapi-bom`) will publish.
  0.7.0's example coordinates on Central are immutable and will
  remain but should be ignored — there are no consumers.
- `McpCompletionsService.complete` would throw NPE on a request whose
  `argument` record itself was absent (the MCP spec says it's required
  but a malformed request could still arrive). Pulled the null guard
  to the top of `complete` so both candidate lookup and prefix filter
  always see a non-null argument, and return a well-formed empty
  `CompleteResult` for the missing-argument case.

### Changed

- `examples/example-autoconfigure` — the shared module every backend
  example depends on — now registers a `@PromptService` (template-backed
  `summarize` with an enum detail argument) and a `@ResourceService`
  (`docs://readme` fixed resource plus `env://{stage}/config` template
  with enum stage variable) alongside the existing tool beans. Running
  any example now shows the full trio of MCP capabilities, and both
  enum-completion paths are exercised at startup.
- Annotation-discovered methods (`@ToolMethod`, `@PromptMethod`,
  `@ResourceMethod`, `@ResourceTemplateMethod`) are now sorted by
  method name inside each service bean before registration. The JVM
  makes no ordering guarantee for `Class.getDeclaredMethods` / the
  Apache Commons `MethodUtils` wrapper, so startup logs could flap
  across unrelated refactors. Sorting makes startup output
  deterministic.

### Documentation

- `mocapi-conformance/README.md`: the pending-scenarios table
  referenced resources and prompts as "not yet implemented" — both
  are fully wired up in the harness. Rewrote to list the actual
  scenarios being exercised. Also dropped a stale `mocapi-compat`
  reference after the module was renamed to `mocapi-conformance`
  (directory, artifact, and Java package).
- `docs/architecture.md` gains a new "Startup Logging" section
  cataloging the log line format for every provider (tool, prompt,
  resource, plus the nested completion-registration sub-lines).
  Each of the tool, prompt, and resource guides gains a pointer to
  the new section.
- `docs/prompts-guide.md` and `docs/resources-guide.md` gain
  dedicated "Argument Completions" / "Path Variable Completions"
  sections documenting how enum-typed parameters and
  `@Schema(allowableValues = {...})` surface as completions.
- Dropped the stale "Completions" entry from the
  "What Mocapi Does Not Implement" list in `docs/architecture.md`.

## [0.7.0] - 2026-04-17

### Breaking changes

- Starter and transport artifacts renamed for symmetry. Update your
  `pom.xml` coordinates:
  - `mocapi-spring-boot-starter` → `mocapi-streamable-http-spring-boot-starter`
  - `mocapi-transport-streamable-http` → `mocapi-streamable-http-transport`
  - `mocapi-transport-stdio` → `mocapi-stdio-transport`
  The new names put each starter and its transport adjacent in Maven
  Central listings and `mvn dependency:tree` output, and make the
  transport an explicit part of every starter coordinate.

### Added

- New `mocapi-stdio-spring-boot-starter` — mirrors
  `mocapi-streamable-http-spring-boot-starter` for stdio deployments.
  Pulls in `mocapi-server` + `mocapi-stdio-transport` +
  `methodical-jackson3`. Stdio apps can now depend on one coordinate
  instead of wiring the transport and supporting libraries by hand.
- Spring property placeholder (`${...}`) resolution in annotation
  strings. Any `name`, `title`, `description`, `uri`, `uriTemplate`, or
  `mimeType` on `@ToolMethod`, `@PromptMethod`, `@ResourceMethod`, or
  `@ResourceTemplateMethod` now flows through the Spring environment at
  registration time, so long descriptions and URIs can live in
  `application.yml` instead of inline on the annotation. Missing
  placeholders fail fast at startup with a
  `PlaceholderResolutionException` rather than leaking the literal
  `${...}` text into MCP responses.

### Documentation

- README, `docs/architecture.md`, `CONTRIBUTING.md`, and `PRD.md`
  updated for the new starter/transport coordinates.

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

[Unreleased]: https://github.com/callibrity/mocapi/compare/0.10.0...HEAD
[0.10.0]: https://github.com/callibrity/mocapi/releases/tag/0.10.0
[0.9.0]: https://github.com/callibrity/mocapi/releases/tag/0.9.0
[0.8.0]: https://github.com/callibrity/mocapi/releases/tag/0.8.0
[0.7.0]: https://github.com/callibrity/mocapi/releases/tag/0.7.0
[0.6.0]: https://github.com/callibrity/mocapi/releases/tag/0.6.0
[0.5.0]: https://github.com/callibrity/mocapi/releases/tag/0.5.0
[0.4.1]: https://github.com/callibrity/mocapi/releases/tag/0.4.1
[0.4.0]: https://github.com/callibrity/mocapi/releases/tag/0.4.0
[0.3.0]: https://github.com/callibrity/mocapi/releases/tag/0.3.0
[0.2.0]: https://github.com/callibrity/mocapi/releases/tag/0.2.0
[0.1.0]: https://github.com/callibrity/mocapi/releases/tag/0.1.0
