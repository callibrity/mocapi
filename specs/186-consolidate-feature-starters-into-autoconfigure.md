# Consolidate feature `-spring-boot-starter` modules into `mocapi-autoconfigure`

## What to build

Collapse the five feature-level Spring Boot starters
(`mocapi-oauth2-spring-boot-starter`,
`mocapi-jakarta-validation-spring-boot-starter`,
`mocapi-logging-spring-boot-starter`,
`mocapi-o11y-spring-boot-starter`,
`mocapi-actuator-spring-boot-starter`) into a single
`mocapi-autoconfigure` module with optional dependencies on the
feature code modules, and keep only the two transport starters as
user-facing choices (`mocapi-streamable-http-spring-boot-starter`,
`mocapi-stdio-spring-boot-starter`).

### Why

- **Current shape isn't idiomatic Spring Boot.** Spring Boot's own
  pattern is "one autoconfigure module with many
  `@ConditionalOnClass`-gated autoconfigs, plus a starter that
  curates runtime deps." We ship a starter per feature, which
  multiplies artifacts, docs, CHANGELOG entries, and the "which
  starter do I need?" question users have to answer before writing
  a single line of code.
- **Users just want to add a dependency and have the feature come
  alive.** That's the Spring Boot experience for OAuth2
  (`spring-boot-starter-oauth2-resource-server`), validation
  (`spring-boot-starter-validation`), actuator
  (`spring-boot-starter-actuator`), and so on. We should match it:
  add `mocapi-oauth2` to your pom → our OAuth2 autoconfig lights
  up, no mocapi-specific starter name to remember.
- **Pre-1.0 is the right time.** Breaking the artifact layout is
  cheap now; it won't be at 1.0.

### Target shape

```
mocapi-autoconfigure                     ← NEW
  optional deps:
    - mocapi-oauth2
    - mocapi-logging
    - mocapi-o11y
    - mocapi-jakarta-validation          ← NEW (extracted from the starter)
    - spring-boot-starter-actuator
  autoconfigs (all @ConditionalOnClass):
    - MocapiOAuth2AutoConfiguration          → triggered by mocapi-oauth2 classes
    - MocapiLoggingAutoConfiguration         → triggered by mocapi-logging classes
    - MocapiO11yAutoConfiguration            → triggered by mocapi-o11y classes + ObservationRegistry bean
    - MocapiValidationAutoConfiguration      → triggered by mocapi-jakarta-validation classes
    - MocapiActuatorAutoConfiguration        → triggered by spring-boot-actuator + mcp endpoint class
  AutoConfiguration.imports lists all of the above.

mocapi-streamable-http-spring-boot-starter
  deps: mocapi-autoconfigure + mocapi-streamable-http-transport + spring-boot-starter-web
  autoconfigs: MocapiStreamableHttpAutoConfiguration (already exists, moves into mocapi-autoconfigure)

mocapi-stdio-spring-boot-starter
  deps: mocapi-autoconfigure + mocapi-stdio-transport
  autoconfigs: MocapiStdioAutoConfiguration (already exists, moves into mocapi-autoconfigure)
```

### User experience after landing

```xml
<!-- minimal HTTP server -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-streamable-http-spring-boot-starter</artifactId>
</dependency>

<!-- add OAuth2 by dropping in our oauth2 module -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-oauth2</artifactId>
</dependency>

<!-- add validation -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-jakarta-validation</artifactId>
</dependency>

<!-- add metrics + tracing -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-o11y</artifactId>
</dependency>
<!-- plus whichever Micrometer registry / tracing bridge user picks -->

<!-- add /actuator/mcp -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- MDC logging: always on when mocapi-logging is on classpath (SLF4J is universal) -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-logging</artifactId>
</dependency>
```

No `*-spring-boot-starter-*` names for features. Users pick what
they want by adding the feature module, and the autoconfig in
`mocapi-autoconfigure` picks it up on classpath.

### Module-by-module work

**Create `mocapi-autoconfigure`:**

- New Maven module.
- Absorbs the `*AutoConfiguration` classes + `AutoConfiguration.imports`
  from each of the five feature starters, plus the autoconfig
  classes that currently live inside `mocapi-server`
  (`MocapiServerAutoConfiguration`,
  `MocapiServerToolsAutoConfiguration`,
  `MocapiServerPromptsAutoConfiguration`,
  `MocapiServerResourcesAutoConfiguration`). Those move too —
  `mocapi-server` stays pure-Java (no Spring Boot autoconfig),
  which is how it should have been all along.
- Absorbs the autoconfig classes from the two transport starters
  (`MocapiStreamableHttpAutoConfiguration`,
  `MocapiStdioAutoConfiguration`). The transport starters become
  packaging-only (no code), just curating runtime deps.
- Optional deps on each feature code module
  (`<optional>true</optional>`). Compile-time references resolve;
  runtime activation depends on the user bringing the feature
  module onto their own classpath.
- One `AutoConfiguration.imports` lists every autoconfig class.
- Every autoconfig's `@ConditionalOnClass` trigger should reference
  a class *from the feature code module it configures*, so the
  autoconfig is dormant when that module is absent.

**Create `mocapi-jakarta-validation` code module:**

- Currently the Jakarta Validation starter is a single module
  containing the autoconfig + the `MethodInterceptor` / violation
  mapping code.
- Split: code goes into `mocapi-jakarta-validation`, autoconfig
  moves to `mocapi-autoconfigure`.

**Delete:**

- `mocapi-oauth2-spring-boot-starter`
- `mocapi-jakarta-validation-spring-boot-starter`
- `mocapi-logging-spring-boot-starter`
- `mocapi-o11y-spring-boot-starter`
- `mocapi-actuator-spring-boot-starter`

(Delete the modules from the root `pom.xml`; delete their
directories from the working tree.)

**Update:**

- `mocapi-streamable-http-spring-boot-starter/pom.xml`: depend on
  `mocapi-autoconfigure` + `mocapi-streamable-http-transport`.
  Remove its own autoconfig classes (moved).
- `mocapi-stdio-spring-boot-starter/pom.xml`: same shape.
- `mocapi-bom/pom.xml`: drop entries for the five deleted
  starters; add entries for `mocapi-autoconfigure` and
  `mocapi-jakarta-validation`.
- `README.md`: rewrite the "Modules" section. Headline pattern:
  "add a transport starter + feature modules as you need them."
  Show the pom shape in "User experience after landing" above.
- All `examples/*` poms: update to match the new pattern.
- `docs/authorization.md`, `docs/validation.md`,
  `docs/logging.md`, `docs/observability-roadmap.md`: update
  dependency snippets.
- `CHANGELOG.md` `[Unreleased]`: `### Breaking changes` entry
  spelling out the old-to-new artifact mapping.

### Pattern for each autoconfig's conditional

Every autoconfig in `mocapi-autoconfigure` needs one
`@ConditionalOnClass` whose trigger class lives in the feature
code module. Picking the trigger:

| Feature module | Trigger class (example) |
|---|---|
| `mocapi-oauth2` | `OAuth2ProtectedResourceMetadataCustomizer` |
| `mocapi-logging` | `McpMdcInterceptor` |
| `mocapi-o11y` | `McpObservationInterceptor` |
| `mocapi-jakarta-validation` | (to be named during extraction, e.g. `JakartaValidationMethodInterceptor`) |
| Actuator endpoint | `org.springframework.boot.actuate.endpoint.annotation.Endpoint` + our `McpActuatorEndpoint` |

If the user hasn't added the feature module, the trigger class is
absent, the autoconfig never runs, compile-time references never
resolve at runtime — clean.

### Transport autoconfigs stay classpath-triggered too

`MocapiStreamableHttpAutoConfiguration` triggers on the transport's
class (e.g., `StreamableHttpController`), which comes from
`mocapi-streamable-http-transport`. The transport starter pulls in
the transport module; the autoconfig activates. Same for stdio.

This means users who somehow add BOTH transport modules would get
both autoconfigs — probably fine (HTTP + stdio can coexist in
theory) but call it out as "not a supported config" if we don't
want to guarantee it.

## Acceptance criteria

- [ ] New module `mocapi-autoconfigure` exists under root
      `pom.xml` modules list.
- [ ] New module `mocapi-jakarta-validation` exists (extracted
      from the deleted `mocapi-jakarta-validation-spring-boot-starter`).
- [ ] Five feature starter modules deleted from root `pom.xml`
      and from the working tree:
      `mocapi-oauth2-spring-boot-starter`,
      `mocapi-jakarta-validation-spring-boot-starter`,
      `mocapi-logging-spring-boot-starter`,
      `mocapi-o11y-spring-boot-starter`,
      `mocapi-actuator-spring-boot-starter`.
- [ ] `mocapi-autoconfigure` contains every autoconfig class and
      a single `AutoConfiguration.imports` listing them all.
- [ ] Each autoconfig is guarded by a `@ConditionalOnClass`
      referencing a class from the feature code module it
      autoconfigures, so the autoconfig is dormant when the
      feature module isn't on the classpath.
- [ ] Feature code modules (`mocapi-oauth2`, `mocapi-logging`,
      `mocapi-o11y`, `mocapi-jakarta-validation`) are declared
      `<optional>true</optional>` dependencies of
      `mocapi-autoconfigure`.
- [ ] Transport starters have no code of their own — just poms
      pulling `mocapi-autoconfigure` + their transport module
      + (HTTP only) `spring-boot-starter-web`.
- [ ] `mocapi-bom` updated: five old starters removed, two new
      modules added.
- [ ] `README.md` "Modules" section rewritten to match the new
      shape.
- [ ] All `examples/*/pom.xml` files updated to use the new
      dependency pattern.
- [ ] All `docs/*.md` files with dependency snippets updated.
- [ ] `CHANGELOG.md` `[Unreleased]` has a `### Breaking changes`
      entry listing the old-artifact → new-artifact mapping.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.
- [ ] Existing integration tests still pass: starting a Spring
      context with the new artifact shape reproduces the same
      behavior for OAuth2, validation, logging, o11y, actuator,
      and both transports.

## Non-goals

- **Preserving backwards compatibility with the old artifact
  names.** We're pre-1.0; old starter IDs are dropped, full stop.
  The migration note in CHANGELOG is the whole compat story.
- **Changing the feature implementations themselves.** Code
  inside `mocapi-oauth2`, `mocapi-logging`, `mocapi-o11y`, etc.
  is unchanged by this spec — only its autoconfig moves.
- **Auto-activating MDC logging without `mocapi-logging` on the
  classpath.** SLF4J is universal, but the interceptor class
  lives in `mocapi-logging` and the autoconfig triggers on it.
  Users who want MDC add `mocapi-logging`. Keeps the pattern
  uniform across all features.
- **Splitting `mocapi-server`'s existing autoconfigs into their
  own feature modules.** They move into `mocapi-autoconfigure`
  as-is; the server module becomes pure-Java.
- **Server and transport code reorganization beyond "move
  autoconfig out."** Any further slimming of the transport
  controller is tracked in
  `specs/backlog/streamable-http-controller-slim.md`.

## Implementation notes

- The trigger class for each autoconfig can be anything that
  lives *only* in the feature module. Prefer an existing
  public interceptor / customizer class over a made-up marker
  type — it's one class reference, not a whole new SPI.
- Spring Boot recognizes `<optional>true</optional>` dependencies
  exactly the way we need: the classes are on the compile
  classpath of `mocapi-autoconfigure` (so autoconfig compiles),
  but they don't transitively leak to users who just pulled in
  `mocapi-autoconfigure` via a transport starter. Users who want
  a feature add the feature module explicitly — same pattern
  Spring Boot's own `spring-boot-autoconfigure` uses for its
  150+ conditional autoconfigs.
- `mocapi-server` currently holds its own autoconfig classes
  (`MocapiServerToolsAutoConfiguration`, etc.). Those move into
  `mocapi-autoconfigure` as part of this spec. `mocapi-server`
  becomes pure Java + methodical + Spring-core (for
  `StringValueResolver`) — no Spring Boot autoconfig. That's the
  shape every other mocapi code module already has.
- After this spec lands, the only `*-spring-boot-starter`
  artifacts published are the two transport starters. Everything
  else is either `mocapi-*` (core or feature) or
  `mocapi-autoconfigure`.
