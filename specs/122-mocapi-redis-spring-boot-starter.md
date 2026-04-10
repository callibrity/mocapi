# mocapi-spring-boot-starter-redis (all-in-one Redis starter)

## What to build

Create a new Maven module `mocapi-spring-boot-starter-redis` that
is a pure aggregation pom. It declares dependencies on every
mocapi and substrate module that has a Redis-backed implementation
of a pluggable SPI — session store, mailbox, journal, notifier —
so that a consumer pulls in the full Redis-backed stack with a
single dependency.

### Why

Today, an operator who wants to run mocapi on Redis has to declare
**four separate dependencies** (session store, substrate mailbox,
substrate journal, substrate notifier), each versioned
independently, each with its own auto-configuration. That's
error-prone — miss one and you silently fall back to in-memory
for that capability. The all-in-one starter makes "run mocapi on
Redis" a single coordinate.

### Module structure

```
mocapi-spring-boot-starter-redis/
└── pom.xml   (no src/, no Java — aggregation only)
```

Add to the parent pom's `<modules>` list.

### pom.xml

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-parent</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-spring-boot-starter-redis</artifactId>
  <name>Mocapi - Spring Boot Starter (Redis)</name>
  <description>
    All-in-one Redis starter: bundles the mocapi Spring Boot
    starter plus Redis implementations of McpSessionStore,
    Substrate Mailbox, Substrate Journal, and Substrate Notifier.
  </description>

  <dependencies>
    <!-- Core mocapi starter -->
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-spring-boot-starter</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Redis-backed McpSessionStore (spec 113) -->
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-session-store-redis</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Substrate Redis backends -->
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-mailbox-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-journal-redis</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-notifier-redis</artifactId>
    </dependency>

    <!-- Spring Boot Redis starter — pulls in Lettuce and the
         auto-configured RedisConnectionFactory -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
  </dependencies>
</project>
```

**Important**: this starter declares `spring-boot-starter-data-redis`
as a regular (non-optional) dependency. Consumers should NOT have
to add it themselves — pulling in the starter gives you everything.
The individual mocapi modules keep the Redis starter as `optional`
to avoid forcing it on non-Redis users; the starter is the place
where we commit to Redis.

### What the consumer does

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-spring-boot-starter-redis</artifactId>
  <version>...</version>
</dependency>
```

And in `application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

Everything else auto-configures. The consumer gets:
- Mocapi HTTP controller and dispatcher (from core starter)
- Redis-backed `McpSessionStore` (from session-store-redis)
- Redis-backed substrate mailbox, journal, notifier (from
  substrate-*-redis modules)
- All without a single explicit `@Bean` in the consumer's code.

## Acceptance criteria

- [ ] New module `mocapi-spring-boot-starter-redis` exists as a
      child of the parent pom.
- [ ] Module is listed in the parent pom's `<modules>` list.
- [ ] The starter has no Java source — it's a pure aggregation
      pom.
- [ ] The pom declares transitive dependencies on:
  - `mocapi-spring-boot-starter`
  - `mocapi-session-store-redis`
  - `substrate-mailbox-redis`
  - `substrate-journal-redis`
  - `substrate-notifier-redis`
  - `spring-boot-starter-data-redis`
- [ ] A downstream integration test (in
      `mocapi-session-store-redis` or a new dedicated test
      module) uses `@SpringBootTest` with the starter on the
      classpath and Testcontainers Redis, verifies that all four
      beans (`McpSessionStore`, `Mailbox`, `Journal`, `Notifier`)
      are the Redis-backed implementations and not the in-memory
      fallbacks.
- [ ] Documentation (README section or starter's pom description)
      explains the one-dependency usage pattern.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Dependency on specs 113 and any substrate prerequisites**.
  Spec 113 introduces `mocapi-session-store-redis`; this starter
  can't be built until 113 has landed. If substrate's Redis
  implementations for mailbox/journal/notifier don't yet exist
  as separate modules, this spec is blocked on them too —
  verify before starting.
- **Substrate module coordinates**: verify the exact groupId /
  artifactId of each substrate Redis module before committing.
  This spec assumes `org.jwcarman.substrate:substrate-*-redis`
  following the existing substrate naming convention. If the
  coordinates differ, update the pom accordingly.
- **No Java code in the starter**. This is strictly an aggregation
  pom. If the starter needs any glue code (e.g., a composite
  `@AutoConfiguration` that ensures a specific bean ordering),
  that code belongs in one of the individual modules (most
  likely `mocapi-session-store-redis`), not in the aggregator.
- **Version management**: the starter's pom should inherit
  versions for substrate modules from the parent pom's
  `<dependencyManagement>` section. If substrate versions aren't
  already in the parent pom's dependency management, add them
  there as part of this spec.
- **Commit suggestion**: one commit for the new module +
  parent pom update + any integration test.
