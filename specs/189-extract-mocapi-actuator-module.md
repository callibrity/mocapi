# Extract `mocapi-actuator` module from `mocapi-autoconfigure`

## What to build

Move the actuator endpoint code (`McpActuatorEndpoint`,
`McpActuatorSnapshot`, `McpActuatorSnapshots`) out of
`mocapi-autoconfigure` and into a new `mocapi-actuator` code
module. Leave only `MocapiActuatorAutoConfiguration` in
`mocapi-autoconfigure`, with its `@ConditionalOnClass` trigger
referencing `McpActuatorEndpoint` from the new module (same
pattern as every other feature).

### Why

Spec 186's consolidation followed a clean rule: feature
*code* lives in a dedicated module (`mocapi-oauth2`,
`mocapi-logging`, `mocapi-o11y`, `mocapi-jakarta-validation`);
feature *autoconfig* lives in `mocapi-autoconfigure`. Actuator
broke the rule — endpoint class + snapshot records + snapshot
builders all ended up inside `mocapi-autoconfigure` alongside
the autoconfig. That was an argument-from-"it's Spring Boot
Actuator-specific," but by the same reasoning OAuth2 is Spring
Security-specific and we didn't bundle it. Uniformity wins.

Side benefit: a user who wants just the snapshot-building
machinery (for a custom admin endpoint, ops debug dump, health
probe detail, etc.) can depend on `mocapi-actuator` without
pulling in the autoconfig.

### Target shape

```
mocapi-actuator/                         ← NEW
  src/main/java/com/callibrity/mocapi/actuator/
    McpActuatorEndpoint.java            — @Endpoint(id = "mcp")
    McpActuatorSnapshot.java            — response records
    McpActuatorSnapshots.java           — snapshot / digest helpers

mocapi-autoconfigure/
  src/main/java/com/callibrity/mocapi/actuator/
    MocapiActuatorAutoConfiguration.java    ← only the autoconfig stays
```

`mocapi-actuator` depends on `mocapi-server` (for service beans
it reads descriptors from) and `spring-boot-actuator` (for the
`@Endpoint` / `@ReadOperation` annotations). Not on Spring Boot
Autoconfigure — that's the autoconfig module's concern.

`mocapi-autoconfigure` gains an `<optional>true</optional>`
dependency on `mocapi-actuator` (same treatment as the other
feature code modules). The actuator autoconfig's
`@ConditionalOnClass` gets `McpActuatorEndpoint.class` added
alongside the existing Spring Boot Actuator `Endpoint.class`
check — so the autoconfig stays dormant when
`mocapi-actuator` isn't on the classpath.

### User experience

Unchanged in spirit, just one more explicit dep:

```xml
<!-- to enable /actuator/mcp -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Matches the opt-in shape for OAuth2 / o11y / validation /
logging exactly.

### Scope of changes

- **Create** `mocapi-actuator/` module (pom + `src/main/java`).
- **Move** three source files:
  `McpActuatorEndpoint.java`,
  `McpActuatorSnapshot.java`,
  `McpActuatorSnapshots.java` from
  `mocapi-autoconfigure/src/main/java/.../actuator/` to the new
  module. Keep the package `com.callibrity.mocapi.actuator`.
- **Move** any test fixtures that test the *code* (not the
  autoconfig) to the new module's `src/test`. Autoconfig tests
  stay in `mocapi-autoconfigure`.
- **Update** `MocapiActuatorAutoConfiguration`'s
  `@ConditionalOnClass` to include `McpActuatorEndpoint.class`.
- **Add** `mocapi-autoconfigure` pom: `mocapi-actuator` as
  `<optional>true</optional>` dependency.
- **Add** `mocapi-bom`: entry for `mocapi-actuator`.
- **Add** root `pom.xml`: new module listing.
- **Update** `README.md` "Modules" section: new bullet for
  `mocapi-actuator`.
- **Update** `docs/observability-roadmap.md` / any doc
  referencing actuator setup to reflect the explicit
  `mocapi-actuator` dep.

## Acceptance criteria

- [ ] New module `mocapi-actuator` exists under root
      `pom.xml` modules list.
- [ ] `McpActuatorEndpoint`, `McpActuatorSnapshot`,
      `McpActuatorSnapshots` live in `mocapi-actuator/src/main/java`
      under package `com.callibrity.mocapi.actuator`.
- [ ] `mocapi-autoconfigure` no longer contains the three
      endpoint-related classes; only
      `MocapiActuatorAutoConfiguration` remains in its
      `actuator` package.
- [ ] `MocapiActuatorAutoConfiguration`'s `@ConditionalOnClass`
      includes `McpActuatorEndpoint.class`.
- [ ] `mocapi-autoconfigure/pom.xml` declares `mocapi-actuator`
      as `<optional>true</optional>`.
- [ ] `mocapi-bom` pom has an entry for `mocapi-actuator`.
- [ ] `README.md` "Modules" section lists `mocapi-actuator`
      with a one-line description.
- [ ] Integration / autoconfig test: without `mocapi-actuator`
      on the classpath, `MocapiActuatorAutoConfiguration` is
      dormant (condition fails). With it on the classpath +
      actuator starter, the endpoint bean is registered.
- [ ] Existing actuator tests still pass, relocated to the
      appropriate module.
- [ ] `mvn verify` green across all modules.
- [ ] `mvn -P release javadoc:jar -DskipTests` green.
- [ ] `mvn spotless:check` green.

## Non-goals

- **Changing the endpoint shape / response body.** Pure
  relocation; endpoint behavior is identical on both sides.
- **Adding the actuator endpoint for users who don't opt in.**
  Still requires both `mocapi-actuator` and
  `spring-boot-starter-actuator` to activate.
- **Splitting snapshot building into a further sub-module.**
  `McpActuatorSnapshots` stays co-located with
  `McpActuatorEndpoint` — they're a pair.

## Implementation notes

- `mocapi-actuator` compiles against Spring Boot Actuator API
  (`@Endpoint`, `@ReadOperation`) but doesn't pull in the whole
  actuator runtime — that comes transitively through user's
  `spring-boot-starter-actuator` dep at app level.
- The pattern to copy for pom shape:
  `mocapi-o11y/pom.xml` (declares `mocapi-server` + its
  framework-specific dep, nothing else).
- Test fixtures that were added for the MockMvc-based actuator
  tests may reference Spring Boot's `@SpringBootTest` / actuator
  machinery — those stay in `mocapi-autoconfigure`'s tests since
  they exercise the autoconfig end-to-end. Tests that just
  construct `McpActuatorEndpoint` directly and assert on the
  snapshot shape move to `mocapi-actuator`'s tests.
