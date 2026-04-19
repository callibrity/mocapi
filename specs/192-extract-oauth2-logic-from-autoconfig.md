# Extract OAuth2 logic from `MocapiOAuth2AutoConfiguration` into `mocapi-oauth2`

## What to build

Move the ~300 lines of logic currently inside
`MocapiOAuth2AutoConfiguration` out into dedicated classes in the
`mocapi-oauth2` feature module. Autoconfig keeps only what's
genuinely autoconfig — the `@AutoConfiguration` annotations, the
`@Bean` method declaration, and thin delegation to the extracted
classes. Target: autoconfig under 100 lines.

### Why

After spec 186's consolidation, every other feature has its code
in its own module and only its autoconfig in `mocapi-autoconfigure`:

| Feature | Feature module (code) | Autoconfig LOC |
|---|---|---|
| logging | `mocapi-logging` (3 classes) | ~60 |
| o11y | `mocapi-o11y` (3 classes) | ~85 |
| jakarta-validation | `mocapi-jakarta-validation` | ~80 |
| actuator | `mocapi-actuator` (after spec 189) | ~40 |
| **oauth2** | `mocapi-oauth2` (4 classes, 238 LOC) | **~360** |

OAuth2 is the outlier. The autoconfig is carrying compliance
validation, resource resolution, metadata customizer
construction, and filter chain assembly — all of which are pure
logic that happens to execute from an `@Bean` method. Moving
them into `mocapi-oauth2` aligns the module with every other
feature's shape and makes the logic:

- Unit-testable without `@SpringBootTest` (no
  `ApplicationContextRunner` needed to exercise a static
  resource-resolution helper).
- Reusable outside an autoconfig context (a user hand-building
  an MCP server in a non-Spring-Boot shape could still use the
  resource resolver or compliance validator).
- Easier to read — "what does this module do?" answered by
  scanning `mocapi-oauth2/src/main/java`, not by reading the
  autoconfig class.

### Extraction map

**Move from `MocapiOAuth2AutoConfiguration` → `mocapi-oauth2`:**

| Current location | Target class / method | Kind |
|---|---|---|
| `validateComplianceMode` (static, ~28 LOC) | new `MocapiOAuth2Compliance` class, static `validate(...)` | Pure logic |
| `resolveResource` (static, ~33 LOC) | new `MocapiOAuth2ResourceResolver` class, static `resolve(...)` | Pure logic |
| `authorizationServersFor` (static, ~13 LOC) | `MocapiOAuth2ResourceResolver.authorizationServers(...)` | Pure logic |
| `resourceNameFor` (static, ~13 LOC) | `MocapiOAuth2ResourceResolver.resourceName(...)` | Pure logic |
| `metadataBuilderCustomizer` (static, ~26 LOC) | new `McpMetadataBuilderCustomizerFactory` class, static `create(...)` | Pure logic (uses Spring's `OAuth2ProtectedResourceMetadata.Builder`) |
| `configureTokenMode` (static, ~23 LOC) | new `McpOAuth2TokenModeConfigurer` class, static `apply(...)` | Spring-aware helper |
| `mcpOAuth2SecurityFilterChain` `@Bean` body (~43 LOC fluent `http.*` calls) | new `McpOAuth2SecurityFilterChainBuilder` class with instance method `build(HttpSecurity)` | Spring-aware builder |

**Stays in `MocapiOAuth2AutoConfiguration`:**

- `@AutoConfiguration(after = ...)`, `@ConditionalOnClass`,
  `@EnableConfigurationProperties`, `@PropertySource` annotations.
- `METADATA_PATH` constant (shared between autoconfig and filter
  chain; keep it addressable via the autoconfig class since
  that's the natural documentation home).
- Constructor invoking `MocapiOAuth2Compliance.validate(...)`
  for fail-fast startup.
- Single `@Bean` method that delegates to
  `McpOAuth2SecurityFilterChainBuilder.build(http)`.

After extraction, the autoconfig reads top-to-bottom as:
"annotate, fail fast, delegate." That's it.

### New class sketches

```java
// mocapi-oauth2 — com.callibrity.mocapi.oauth2
public final class MocapiOAuth2Compliance {

    private MocapiOAuth2Compliance() {}

    public static void validate(
            JwtDecoder jwtDecoder,                      // nullable
            OpaqueTokenIntrospector opaqueTokenIntrospector,  // nullable
            List<String> audiences) {
        if (jwtDecoder == null && opaqueTokenIntrospector == null) {
            throw new IllegalStateException("""
                mocapi-oauth2 is on the classpath but neither JwtDecoder nor
                OpaqueTokenIntrospector is registered — ...""");
        }
        if (audiences.isEmpty()) {
            throw new IllegalStateException("""
                spring.security.oauth2.resourceserver.jwt.audiences is empty.
                The MCP 2025-11-25 authorization spec mandates audience
                validation ...""");
        }
    }
}
```

Autoconfig constructor shrinks to:

```java
public MocapiOAuth2AutoConfiguration(
        MocapiOAuth2Properties properties,
        ObjectProvider<JwtDecoder> jwtDecoder,
        ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospector,
        ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties) {
    Objects.requireNonNull(properties);
    MocapiOAuth2Compliance.validate(
            jwtDecoder.getIfAvailable(),
            opaqueTokenIntrospector.getIfAvailable(),
            audiencesFrom(springResourceServerProperties));
    MocapiOAuth2ResourceResolver.resolve(
            properties,
            audiencesFrom(springResourceServerProperties));  // fail-fast on misconfigured resource
}
```

`audiencesFrom(...)` is a tiny private helper in the autoconfig
class that collapses `ObjectProvider<OAuth2ResourceServerProperties>`
to a `List<String>`. Keeping that bit of Spring-DI translation in
the autoconfig is fine — it's the glue that hands off to the
pure-logic layer.

Similarly, the `@Bean` method shrinks to something like:

```java
@Bean
@ConditionalOnMissingBean(name = "mcpOAuth2SecurityFilterChain")
@Order(Ordered.HIGHEST_PRECEDENCE)
public SecurityFilterChain mcpOAuth2SecurityFilterChain(
        HttpSecurity http,
        MocapiOAuth2Properties properties,
        ObjectProvider<JwtDecoder> jwtDecoder,
        ObjectProvider<OpaqueTokenIntrospector> opaqueTokenIntrospector,
        ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties,
        ObjectProvider<Implementation> mcpServerInfo,
        ObjectProvider<OAuth2ProtectedResourceMetadataCustomizer> metadataCustomizers,
        ObjectProvider<MocapiOAuth2SecurityFilterChainCustomizer> chainCustomizers,
        @Value("${mocapi.endpoint:/mcp}") String mcpEndpoint) throws Exception {

    return new McpOAuth2SecurityFilterChainBuilder(
            properties,
            jwtDecoder.getIfAvailable(),
            opaqueTokenIntrospector.getIfAvailable(),
            audiencesFrom(springResourceServerProperties),
            mcpServerInfo.getIfAvailable(),
            metadataCustomizers.orderedStream().toList(),
            chainCustomizers.orderedStream().toList(),
            mcpEndpoint,
            METADATA_PATH)
        .build(http);
}
```

The builder is a plain class — constructor takes everything it
needs, `build(HttpSecurity)` returns the filter chain.
Instantiable and testable without any Spring Boot machinery.

### Scope of changes

**New files in `mocapi-oauth2/src/main/java/com/callibrity/mocapi/oauth2/`:**

- `MocapiOAuth2Compliance.java`
- `MocapiOAuth2ResourceResolver.java`
- `McpMetadataBuilderCustomizerFactory.java`
- `McpOAuth2TokenModeConfigurer.java`
- `McpOAuth2SecurityFilterChainBuilder.java`

**Modified:**

- `mocapi-autoconfigure/.../MocapiOAuth2AutoConfiguration.java` — strip logic, keep annotations + constructor + one `@Bean`.

**Moved tests:**

- Pure-logic tests (`MocapiOAuth2ValidationTest`,
  `MocapiOAuth2ResourceResolutionTest`,
  `MocapiOAuth2ResourceNameTest`,
  `MocapiOAuth2AuthorizationServersTest`,
  `MocapiOAuth2ComplianceValidationTest`) move from
  `mocapi-autoconfigure/src/test` → `mocapi-oauth2/src/test`.
  They're asserting on pure-logic methods; they don't need the
  Spring context anymore.
- Integration / autoconfig tests (`MocapiOAuth2IntegrationTest`,
  `MocapiOAuth2AutoConfigurationTest`,
  `MocapiOAuth2OpaqueTokenConfigTest`) stay in
  `mocapi-autoconfigure/src/test` — they exercise the autoconfig
  wiring end-to-end.

**Dependencies:**

- `mocapi-oauth2/pom.xml` may need to add
  `spring-security-oauth2-resource-server` as a `compile`-scoped
  dependency (it's currently pulled transitively via the
  autoconfig's dep graph; after extraction, the feature module
  directly references Spring Security types like
  `OAuth2ProtectedResourceMetadata.Builder`,
  `OpaqueTokenIntrospector`, `JwtDecoder`). Verify during
  implementation.
- `mocapi-autoconfigure/pom.xml` — no change. Already has
  `mocapi-oauth2` as `<optional>true</optional>`.

### Non-goals

- **Changing any OAuth2 behavior.** Pure refactor. Every
  fail-fast message, every metadata document field, every
  filter chain rule is identical on both sides of this change.
- **Reshaping the `MocapiOAuth2Properties` class.** It already
  lives in `mocapi-oauth2` and stays there unchanged.
- **Replacing Spring's `OAuth2ResourceServerConfigurer` with
  custom plumbing.** We still compose on top of Spring
  Security's OAuth2 resource-server machinery.
- **Further splitting the new `McpOAuth2SecurityFilterChainBuilder`.**
  It's a cohesive unit; splitting further would produce
  anemic classes. Keep it as one class unless it grows.

## Acceptance criteria

- [ ] `MocapiOAuth2AutoConfiguration` is under 100 lines
      (including Javadoc and annotations) and contains only
      autoconfig annotations, one constructor, and one `@Bean`
      method that delegates to the builder.
- [ ] New classes exist in `mocapi-oauth2`:
      `MocapiOAuth2Compliance`,
      `MocapiOAuth2ResourceResolver`,
      `McpMetadataBuilderCustomizerFactory`,
      `McpOAuth2TokenModeConfigurer`,
      `McpOAuth2SecurityFilterChainBuilder`.
- [ ] Pure-logic unit tests moved to `mocapi-oauth2/src/test`
      and exercise the extracted classes directly (no Spring
      context).
- [ ] Integration / autoconfig tests remain in
      `mocapi-autoconfigure/src/test` and still pass — they
      exercise the autoconfig delegating to the builder.
- [ ] All existing oauth2 behavior preserved: same fail-fast
      messages (verbatim), same metadata document contents,
      same filter chain rules, same CSRF-disable note.
- [ ] `mocapi-oauth2/pom.xml` declares whatever additional
      Spring Security deps the extracted classes now reference
      directly (verify during implementation — most are likely
      already transitively available).
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.
- [ ] No change to `mocapi-autoconfigure/pom.xml` (still
      optionally depends on `mocapi-oauth2`).
- [ ] No change to user-facing API — `MocapiOAuth2Properties`
      unchanged, `OAuth2ProtectedResourceMetadataCustomizer`
      unchanged, `MocapiOAuth2SecurityFilterChainCustomizer`
      unchanged.

## Implementation notes

- Keep method signatures concrete: `List<String> audiences`
  rather than `ObjectProvider<OAuth2ResourceServerProperties>`.
  The autoconfig collapses `ObjectProvider`s to concrete values
  (null or the bean) before calling into the extracted classes.
  That keeps `mocapi-oauth2` free of Spring-DI-specific types.
- `Optional<String>` return from `resourceName` is fine; it's
  a pure-logic helper, not a DI-facing boundary.
- `METADATA_PATH` constant: keep on the autoconfig class. It's
  referenced by both the autoconfig and by the filter chain
  builder — the autoconfig passes it through the builder
  constructor, avoiding a duplicate constant.
- After the refactor, the autoconfig should read as:
  1. Annotations (top).
  2. `METADATA_PATH` constant.
  3. Constructor — delegates to compliance + resource resolution for fail-fast.
  4. `mcpOAuth2SecurityFilterChain` `@Bean` — constructs the builder, calls `build(http)`.
  5. `audiencesFrom(...)` private helper.
  That's the whole file.
- Test migration is mostly mechanical: move the file, swap
  `@SpringBootTest` / `ApplicationContextRunner` assertions
  for direct method calls. If a test was primarily exercising
  the Spring context's ability to instantiate the autoconfig,
  leave it in `mocapi-autoconfigure/src/test`; if it was
  exercising the pure logic, move it.
- The one non-trivial extraction: `McpOAuth2SecurityFilterChainBuilder`
  receives `HttpSecurity` as a `build(HttpSecurity)` parameter
  rather than at construction — matches Spring's own
  `HttpSecurityBuilder` pattern and keeps the instance
  constructable without a live `HttpSecurity`.
