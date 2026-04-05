# Upgrade to Java 25, Spring Boot 4.0.5, and add Spotless

## What to build

Upgrade the project to Java 25, Spring Boot 4.0.5 (Spring Framework 7), and add Spotless
with Google Java Format. This is a major version upgrade that includes Jackson 3.0 migration
and starter POM renames.

### Java version

- Change `<java.version>24</java.version>` to `<java.version>25</java.version>` in parent `pom.xml`

### Spring Boot version

- Change `<spring-boot.version>3.5.3</spring-boot.version>` to `<spring-boot.version>4.0.5</spring-boot.version>`

### Jackson 3.0 migration

Spring Boot 4.0 adopts Jackson 3.0 with new Maven coordinates and packages:

**Maven coordinates:**
- Group ID changes from `com.fasterxml.jackson` to `tools.jackson` (except `jackson-annotations`)
- Update any explicit Jackson dependencies in module POMs accordingly

**Import changes across all source and test files:**
- `com.fasterxml.jackson.databind.*` → `tools.jackson.databind.*`
- `com.fasterxml.jackson.core.*` → `tools.jackson.core.*`
- `com.fasterxml.jackson.annotation.*` → `tools.jackson.annotation.*`

**Spring Boot class renames:**
- `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`
- `@JsonComponent` → `@JacksonComponent` (if used)
- `@JsonMixin` → `@JacksonMixin` (if used)

### Starter POM renames

- `spring-boot-starter-web` → `spring-boot-starter-webmvc` in `mocapi-autoconfigure/pom.xml`
- `spring-boot-starter-test` — check if rename is needed

### Spotless plugin

Add the `com.diffplug.spotless:spotless-maven-plugin` to the parent POM `<pluginManagement>`
and `<plugins>` sections:

- Formatter: Google Java Format (latest version)
- Bind `spotless:check` to the `validate` phase
- Configure for `src/main/java/**/*.java` and `src/test/java/**/*.java`

### License plugin phase binding

Bind `license:check` to the `validate` phase so it also fails fast before compilation.

### Dependency upgrades

Upgrade all dependency versions to their latest stable releases:
- `everit-json-schema.version`
- `victools.version`
- `swagger-annotations.version`
- All Maven plugin versions (`maven-compiler-plugin`, `maven-surefire-plugin`, etc.)

### Format all source files

After Spotless is configured, run `mvn spotless:apply` to reformat all existing source
files to Google Java Format. This will be a large but mechanical change.

## Acceptance criteria

- [ ] `java.version` property is `25`
- [ ] `spring-boot.version` property is `4.0.5`
- [ ] No `com.fasterxml.jackson` imports remain in any Java source file
- [ ] `Jackson2ObjectMapperBuilderCustomizer` is replaced with `JsonMapperBuilderCustomizer` (or equivalent)
- [ ] Spotless plugin is configured with Google Java Format and bound to `validate` phase
- [ ] License check is bound to `validate` phase
- [ ] `mvn spotless:check` passes (all files formatted)
- [ ] `mvn verify` passes (full build including tests, lint, license)
- [ ] No deprecated Spring Boot 3.x starter artifact IDs remain

## Implementation notes

- This spec depends on spec 002 being complete (RipCurl fully removed) so there are
  no transitive dependency conflicts.
- The Jackson 3 migration is the highest-risk change. `ObjectMapper`, `JsonNode`,
  `ObjectNode` all move packages. Do a project-wide search-and-replace of imports.
- Spring Boot 4.0 may change how `AutoConfiguration.imports` works — verify the
  existing file at `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  still functions. Check the Spring Boot 4.0 migration guide.
- `@MockBean`/`@SpyBean` are deprecated in Spring Boot 4.0 in favor of
  `@MockitoBean`/`@MockitoSpyBean` — update any test files that use them.
- The VicTools JSON schema library and Everit JSON schema library must be compatible
  with Jackson 3.0. If not, find alternatives or check for updated versions.
- Run `mvn spotless:apply` as the last step to format everything consistently,
  then verify with `mvn verify`.
