# mocapi-postgresql-spring-boot-starter (all-in-one PostgreSQL starter)

## What to build

Create a new Maven module `mocapi-postgresql-spring-boot-starter`
that is a pure aggregation pom, following the exact same pattern
as the Redis (spec 122) and Hazelcast (spec 123) all-in-one
starters. Bundles every mocapi and substrate module that has a
PostgreSQL-backed implementation of a pluggable SPI so operators
get the full mocapi stack on a single database with a single
dependency.

### Why PostgreSQL as a full-stack platform

After scraping substrate's published artifacts on Maven Central
(`org.jwcarman.substrate`), PostgreSQL turns out to have
**full-stack support** — not just a session store. The
substrate artifacts are:

- `substrate-mailbox-postgresql` — mailbox backend, likely
  using a combination of an `items` table + `SKIP LOCKED`
  polling for consumer checkout
- `substrate-journal-postgresql` — append-only journal, likely
  using an `events` table with serial primary key for
  monotonic ordering
- `substrate-notifier-postgresql` — notifier backend, almost
  certainly using PostgreSQL's native `LISTEN` / `NOTIFY`
  pub/sub feature for fan-out

Combined with `mocapi-session-store-jdbc` (spec 115, already
landed), PostgreSQL can serve as a **complete** mocapi platform
without any in-memory fallbacks. Every pluggable SPI has a
PostgreSQL-backed implementation.

**Important correction to earlier framing**: I wrote specs 115
and 129 stating that PostgreSQL was "session-store only" and
that mailbox/journal/notifier would fall back to in-memory. That
was **wrong** — substrate has all three. The PostgreSQL example
app (spec 129) should be updated when this starter lands to use
this all-in-one starter instead of depending on
`mocapi-session-store-jdbc` directly. Its README's "session
store only" disclaimer becomes incorrect and should be removed.
Flag this as a follow-up in the commit message and in the
acceptance criteria below.

### Module structure

```
mocapi-postgresql-spring-boot-starter/
└── pom.xml   (no src/, no Java — pure aggregation pom)
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

  <artifactId>mocapi-postgresql-spring-boot-starter</artifactId>
  <name>Mocapi - PostgreSQL Spring Boot Starter</name>
  <description>
    All-in-one PostgreSQL starter: bundles the mocapi Spring
    Boot starter plus PostgreSQL implementations of
    McpSessionStore (via JDBC), Substrate Mailbox, Substrate
    Journal, and Substrate Notifier. Operators get a complete
    mocapi stack on a single PostgreSQL instance with a single
    dependency.
  </description>

  <dependencies>
    <!-- Core mocapi starter -->
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-spring-boot-starter</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- JDBC-backed McpSessionStore (spec 115). Works against
         PostgreSQL via the PostgreSQL dialect + schema. -->
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-session-store-jdbc</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- Substrate PostgreSQL backends -->
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-mailbox-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-journal-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-notifier-postgresql</artifactId>
    </dependency>

    <!-- Spring Boot JDBC starter — provides DataSource
         auto-config, connection pool, and JdbcClient / JdbcTemplate -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- PostgreSQL JDBC driver — required at runtime -->
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
    </dependency>
  </dependencies>
</project>
```

**Notes on dependency scopes:**

- `spring-boot-starter-jdbc` is non-optional: consumers get it
  whether they wanted it or not. That's the point of an
  all-in-one starter.
- The PostgreSQL JDBC driver (`org.postgresql:postgresql`) is a
  regular dependency, not `runtime` scope, so that the
  substrate modules can compile-time-link against any types
  they need from the driver (should be none, but being
  explicit avoids surprises).
- The individual mocapi/substrate modules keep their
  PostgreSQL-specific deps as `optional` to avoid forcing
  PostgreSQL on non-PostgreSQL users. This starter is the
  place where we commit to PostgreSQL, so the deps become
  regular.

### Consumer usage

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-postgresql-spring-boot-starter</artifactId>
  <version>...</version>
</dependency>
```

And in `application.yml`:

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY:ChangeMeForProduction1234567890abcd}

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/mocapi}
    username: ${DB_USER:mocapi}
    password: ${DB_PASSWORD:mocapi}
  sql:
    init:
      mode: always
      schema-locations:
        - classpath:schema/mocapi-session-store-postgresql.sql
        # - additional substrate-postgresql schema files if they exist
```

Everything else auto-configures. The consumer gets:
- Mocapi HTTP controller and dispatcher (from the core starter)
- PostgreSQL-backed `McpSessionStore` (from
  `mocapi-session-store-jdbc` + the PostgreSQL dialect)
- PostgreSQL-backed substrate mailbox, journal, notifier (from
  the substrate-*-postgresql modules)
- A pooled `DataSource` pointing at PostgreSQL
- All without a single explicit `@Bean` in the consumer's code.

### Schema initialization

The JDBC session store ships
`classpath:schema/mocapi-session-store-postgresql.sql` with the
`mocapi_sessions` table DDL. Substrate's PostgreSQL modules may
also ship their own schema files for the tables they use
(mailbox queue, journal events, notifier channel state).

**Verification step during implementation**: open each
substrate-*-postgresql jar and look for `classpath:schema/*`
resources. If each module ships its own SQL, document the full
set of files operators should reference in
`spring.sql.init.schema-locations`. If substrate has its own
schema-initialization mechanism (flyway integration, an
auto-init bean, etc.), document that instead.

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-postgresql-spring-boot-starter` exists
      as a child of the parent pom.
- [ ] Module is listed in the parent pom's `<modules>` list.
- [ ] The starter has **no Java source** — it's a pure
      aggregation pom with only a `pom.xml`.
- [ ] Apache 2.0 license header in the `pom.xml` comment
      block matches the rest of the project's pom files.

### Dependencies

- [ ] The pom declares transitive dependencies on:
  - `mocapi-spring-boot-starter`
  - `mocapi-session-store-jdbc`
  - `substrate-mailbox-postgresql`
  - `substrate-journal-postgresql`
  - `substrate-notifier-postgresql`
  - `spring-boot-starter-jdbc`
  - `org.postgresql:postgresql`
- [ ] None of the above are marked `optional` or `provided` —
      all regular compile-scope dependencies, so consumers get
      the full stack transitively.
- [ ] The substrate module versions are resolved via the parent
      pom's `<dependencyManagement>` section. If substrate
      versions aren't managed centrally yet, add them there as
      part of this spec.

### Integration test

- [ ] An integration test uses `@SpringBootTest` with
      Testcontainers PostgreSQL (`postgres:16-alpine` image
      or similar) and verifies that:
  - A `McpSessionStore` bean is present and is NOT the
    `InMemoryMcpSessionStore` fallback.
  - A substrate `Mailbox` bean is present and is the
    PostgreSQL-backed implementation.
  - A substrate `Journal` bean is present and is the
    PostgreSQL-backed implementation.
  - A substrate `Notifier` bean is present and is the
    PostgreSQL-backed implementation.
  - An end-to-end save/find round-trip on the session store
    works against the Testcontainers PG instance.
- [ ] The integration test uses the schema init mechanism
      documented in the starter's `application.yml` example
      to set up any required tables.
- [ ] The test lives either in a new dedicated test module or
      inside the existing `mocapi-session-store-jdbc`
      (alongside the other JDBC session store tests). Pick
      whichever feels natural.

### Documentation

- [ ] The starter's pom `<description>` clearly states that
      it's an all-in-one PostgreSQL stack (session store +
      mailbox + journal + notifier).
- [ ] If this module ships a README (optional but
      encouraged), it documents:
  - The one-dependency consumer usage pattern
  - Required PostgreSQL version (whatever substrate's
    PostgreSQL modules support; check their docs)
  - Sample `application.yml` with `spring.datasource.*`
    properties
  - Sample `spring.sql.init.schema-locations` configuration
    covering all schema files (session store + substrate)
  - IAM/connection-pool tuning hints for production

### Build and reactor

- [ ] `mvn verify` passes across the full reactor.
- [ ] The new module's build produces only a `pom.xml`
      artifact, no jar.

### Follow-up: update spec 129 (PostgreSQL example app)

- [ ] Document in the commit message (or flag in a follow-up
      issue/spec) that spec 129 (`mocapi-example-postgresql`)
      should be updated to use this all-in-one starter
      instead of depending on `mocapi-session-store-jdbc`
      directly.
- [ ] The "session store only — mailbox/journal/notifier
      fall back to in-memory" disclaimer in spec 129's
      README becomes incorrect once this starter lands and
      should be removed.
- [ ] Do NOT update spec 129's files as part of this spec.
      The example-app update is a separate, follow-on change
      that Ralph can pick up after this starter lands. Flag
      it clearly.

## Implementation notes

- **Spring Boot naming convention**: this starter must be
  named `mocapi-postgresql-spring-boot-starter`, NOT
  `mocapi-spring-boot-starter-postgresql`. The
  `<name>-spring-boot-starter` form is the documented
  third-party convention; `spring-boot-starter-<name>` is
  reserved for Spring Boot's own starters. See specs 122 and
  123 for the same rationale.
- **Dependency on spec 115** — the JDBC session store module
  must be in place before this starter can be built. If spec
  115 isn't merged yet when Ralph gets to this spec, block
  and escalate.
- **Dependency on spec 134** — spec 134 migrates the JDBC
  session store to `JdbcClient` and validates the table name.
  It's not strictly a prerequisite (this starter just
  aggregates modules), but landing 134 before this spec means
  integration tests exercise the modernized code path.
- **Substrate module coordinates** — verify on Maven Central
  (already confirmed to exist as of this spec's writing):
  - `org.jwcarman.substrate:substrate-mailbox-postgresql`
  - `org.jwcarman.substrate:substrate-journal-postgresql`
  - `org.jwcarman.substrate:substrate-notifier-postgresql`
  If the exact artifactIds differ from what's assumed here,
  update the pom accordingly.
- **Substrate schema files** — as noted in the "Schema
  initialization" section above, each substrate PostgreSQL
  module may ship its own `classpath:schema/*.sql` files. Do
  a thorough check during implementation and document the
  full set in the starter's README. Don't leave operators
  guessing which schema files they need.
- **Substrate version management** — if the parent pom's
  `<dependencyManagement>` doesn't currently manage
  substrate versions, add a `<substrate.version>` property
  and a BOM import (`substrate-bom` is published on Central
  per the earlier scraping). This keeps the starter's pom
  version-free and lets future substrate upgrades land in
  one place.
- **Connection pooling**: `spring-boot-starter-jdbc`
  transitively pulls in HikariCP as the default connection
  pool. This starter inherits that behavior — operators who
  want a different pool (Tomcat JDBC, C3P0, etc.) can
  exclude HikariCP and declare their own. No configuration
  needed in this starter.
- **LISTEN/NOTIFY caveats for the substrate notifier**:
  PostgreSQL's `LISTEN`/`NOTIFY` has a message size limit
  (typically 8KB) and a per-channel queue that can overflow
  under high fan-out. Substrate's PostgreSQL notifier
  presumably handles this gracefully. The starter's
  documentation should note "for very high notification
  rates, consider a purpose-built broker (Redis pub/sub,
  NATS) via the Redis or NATS starter."
- **No Java code in the starter**. Pure aggregation pom.
  Same as specs 122 and 123.
- **Commit granularity**: one commit for the new module +
  parent pom update + any integration test. If the
  integration test needs to go in a separate module (to
  keep dependency scopes clean), that's fine — just keep
  it in the same commit.
