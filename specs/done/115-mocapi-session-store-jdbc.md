# JDBC-backed McpSessionStore module (mocapi-session-store-jdbc)

## What to build

Create a new Maven module `mocapi-session-store-jdbc` that provides
a relational-database-backed implementation of `McpSessionStore`,
using Spring Boot's `JdbcTemplate` (NOT JPA — this module avoids
the JPA dependency tree intentionally). Same drop-in pattern as
the Redis and Hazelcast modules: adding the jar to a Spring Boot
application's classpath automatically replaces the in-memory fallback
with a JDBC-backed store, as long as a `DataSource` bean is already
present.

### Why JDBC (not JPA)

- **Simplicity**: six simple CRUD operations don't justify JPA's
  complexity or its dependency tree (Hibernate, byte-buddy, etc.).
- **Portability**: `JdbcTemplate` works with any JDBC-compatible
  database without entity mapping or dialect-specific code for
  this use case.
- **Lightweight**: the module adds only Spring Boot's JDBC starter,
  not JPA. Consumers who are already using JPA can still use this
  module without conflict — `JdbcTemplate` is always available when
  a `DataSource` is.
- **Predictable SQL**: every query is explicit in the source, so
  debugging and profiling are straightforward.

### Module structure

```
mocapi-session-store-jdbc/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/callibrity/mocapi/session/jdbc/
    │   │   ├── JdbcMcpSessionStore.java
    │   │   └── JdbcMcpSessionStoreAutoConfiguration.java
    │   └── resources/
    │       ├── META-INF/spring/
    │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       └── schema/
    │           ├── mocapi-session-store-h2.sql
    │           ├── mocapi-session-store-postgresql.sql
    │           └── mocapi-session-store-mysql.sql
    └── test/
        └── java/com/callibrity/mocapi/session/jdbc/
            ├── JdbcMcpSessionStoreTest.java         (H2 in-memory)
            ├── JdbcMcpSessionStorePostgresIT.java   (Testcontainers)
            └── JdbcMcpSessionStoreAutoConfigurationTest.java
```

Add the module to the parent pom's `<modules>` list.

### Schema

Single table, dialect-specific SQL files shipped in the jar so
applications can initialize the schema via
`spring.sql.init.schema-locations=classpath:schema/mocapi-session-store-postgresql.sql`
(or similar) if they want automatic DDL:

```sql
-- PostgreSQL
CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  payload JSONB NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mocapi_sessions_expires_at
  ON mocapi_sessions (expires_at);

-- MySQL
CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  payload JSON NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  INDEX idx_mocapi_sessions_expires_at (expires_at)
);

-- H2
CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id VARCHAR(64) PRIMARY KEY,
  payload CLOB NOT NULL,
  expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_mocapi_sessions_expires_at
  ON mocapi_sessions (expires_at);
```

The `payload` column is JSON (or CLOB/TEXT fallback) containing the
serialized `McpSession`. The `expires_at` column is indexed so the
periodic cleanup query can efficiently find expired rows.

**Table name is configurable** via
`mocapi.session.jdbc.table-name` (default `mocapi_sessions`). Any
dialect-specific handling uses the standard `JdbcTemplate` with
parameterized queries.

### `JdbcMcpSessionStore` implementation

Constructor dependencies:
- `JdbcTemplate` (auto-wired from Spring Boot)
- `ObjectMapper` (for JSON serialization of `McpSession`)
- Configuration properties (table name, cleanup interval)

**SQL statements** (using `mocapi_sessions` as the default table name):

```java
private static final String SQL_SAVE =
    "INSERT INTO mocapi_sessions (session_id, payload, expires_at) " +
    "VALUES (?, ?, ?) " +
    "ON CONFLICT (session_id) DO UPDATE " +
    "SET payload = EXCLUDED.payload, expires_at = EXCLUDED.expires_at";

private static final String SQL_UPDATE_PRESERVING_TTL =
    "UPDATE mocapi_sessions SET payload = ? WHERE session_id = ?";

private static final String SQL_FIND =
    "SELECT payload FROM mocapi_sessions " +
    "WHERE session_id = ? AND expires_at > ?";

private static final String SQL_TOUCH =
    "UPDATE mocapi_sessions SET expires_at = ? WHERE session_id = ?";

private static final String SQL_DELETE =
    "DELETE FROM mocapi_sessions WHERE session_id = ?";

private static final String SQL_CLEANUP_EXPIRED =
    "DELETE FROM mocapi_sessions WHERE expires_at <= ?";
```

The `ON CONFLICT` upsert is PostgreSQL-specific. For MySQL use
`INSERT ... ON DUPLICATE KEY UPDATE`; for H2 use `MERGE INTO`. A
cleaner approach: pass the SQL statements via a
`Dialect`-like strategy interface, with three implementations
(`PostgreSqlDialect`, `MySqlDialect`, `H2Dialect`) and dialect
detection via `DatabaseMetaData.getDatabaseProductName()` at
construction time. Or ship three separate auto-config classes
gated on the dialect — pick whichever is cleaner.

### Periodic cleanup of expired rows

Unlike Redis and Hazelcast, JDBC has no native TTL mechanism — we
rely on the `expires_at` column being checked in the `SQL_FIND`
query, but rows are never automatically deleted. A scheduled task
runs periodically (every 5 minutes by default, configurable) to
delete rows where `expires_at <= now()`. This prevents unbounded
table growth.

The cleanup task uses Spring's `@Scheduled` annotation (or a
`ScheduledExecutorService`). Configurable via
`mocapi.session.jdbc.cleanup-interval` (default `5m`).

### Auto-configuration

`JdbcMcpSessionStoreAutoConfiguration` activates when:
- A `DataSource` bean is present
- A `JdbcTemplate` bean is present (Spring Boot's
  `JdbcTemplateAutoConfiguration` has already run)
- No user-provided `McpSessionStore` bean

`@AutoConfiguration(before = MocapiAutoConfiguration.class)` again.

### Dependencies

```xml
<dependencies>
  <dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-core</artifactId>
    <version>${project.version}</version>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
    <optional>true</optional>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <!-- Test deps -->
  <dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-jdbc` exists with correct
      parent reference.
- [ ] Module is listed in the parent pom's `<modules>` list.
- [ ] Package is `com.callibrity.mocapi.session.jdbc`.
- [ ] License headers, spotless formatting, and pom conventions
      match the rest of the mocapi project.

### Schema scripts

- [ ] Three SQL schema scripts ship in `src/main/resources/schema/`:
      `mocapi-session-store-postgresql.sql`,
      `mocapi-session-store-mysql.sql`, `mocapi-session-store-h2.sql`.
- [ ] Each creates a `mocapi_sessions` table with columns
      `session_id`, `payload`, `expires_at` and an index on
      `expires_at`.

### Implementation

- [ ] `JdbcMcpSessionStore` implements all six `McpSessionStore`
      methods using parameterized JDBC queries.
- [ ] `save(session, ttl)` performs an upsert (INSERT with
      ON CONFLICT / MERGE / INSERT ... ON DUPLICATE KEY UPDATE,
      depending on dialect) and sets `expires_at = now + ttl`.
- [ ] `update(sessionId, session)` updates only the `payload`
      column, preserving `expires_at` — this is the critical
      "preserve TTL" requirement.
- [ ] `find(sessionId)` SELECTs the payload WHERE `session_id = ?
      AND expires_at > now()`, returning `Optional.empty()` for
      missing or expired rows. **Expired rows are filtered at read
      time, not at write time.**
- [ ] `touch(sessionId, ttl)` updates only the `expires_at` column
      without touching `payload`.
- [ ] `delete(sessionId)` issues a DELETE and does not fail on
      missing rows.
- [ ] A scheduled task periodically deletes expired rows. Default
      interval is 5 minutes, configurable via
      `mocapi.session.jdbc.cleanup-interval`.
- [ ] The scheduled cleanup task can be disabled entirely via
      `mocapi.session.jdbc.cleanup-enabled=false` for deployments
      that use a separate cleanup mechanism (e.g., database TTL
      features, cron jobs).
- [ ] Table name is configurable via
      `mocapi.session.jdbc.table-name` (default `mocapi_sessions`).
- [ ] JSON serialization uses the application's `ObjectMapper`.

### Dialect handling

- [ ] The module supports PostgreSQL, MySQL, and H2 at minimum.
- [ ] Dialect-specific SQL (upsert syntax) is isolated into a
      small strategy object or per-dialect implementations; the
      rest of the code is dialect-agnostic.
- [ ] Dialect auto-detection uses
      `DatabaseMetaData.getDatabaseProductName()` at construction
      time, or an explicit configuration property
      `mocapi.session.jdbc.dialect` (values: `postgresql`, `mysql`,
      `h2`) to override.
- [ ] If neither auto-detection nor explicit config identifies a
      supported dialect, construction fails with a clear error
      message listing supported dialects.

### Auto-configuration

- [ ] `JdbcMcpSessionStoreAutoConfiguration` is in the
      auto-configuration imports file.
- [ ] `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
- [ ] `@ConditionalOnBean({DataSource.class, JdbcTemplate.class})`
- [ ] `@ConditionalOnMissingBean(McpSessionStore.class)`
- [ ] Registers the store as a `McpSessionStore` bean.

### Tests

- [ ] `JdbcMcpSessionStoreTest` uses an embedded H2 database
      (in-memory, via `DriverManagerDataSource` or Spring's test
      `EmbeddedDatabaseBuilder`), runs the H2 schema SQL, and
      exercises all six methods.
- [ ] `JdbcMcpSessionStorePostgresIT` uses Testcontainers
      PostgreSQL (`postgres:16-alpine` image) and runs the
      PostgreSQL schema SQL. Exercises the same six methods against
      a real database to verify the dialect-specific upsert query
      works.
- [ ] Dedicated tests cover:
  - **TTL expiration on read**: save with 1s TTL, sleep 2s,
    `find` returns empty.
  - **TTL preservation on update**: save with 10s TTL, update
    payload, verify `expires_at` is within ~1s of the original
    (not reset).
  - **Cleanup task**: insert a row with `expires_at` in the past,
    run cleanup, verify the row is gone.
  - **Concurrent save**: two threads upserting the same session
    ID — both succeed, last writer wins. (Exercises the upsert
    semantics.)
- [ ] `JdbcMcpSessionStoreAutoConfigurationTest` uses
      `ApplicationContextRunner` with an in-memory H2 data source
      and verifies:
  - Store is registered when DataSource is present
  - Store is NOT registered when DataSource is absent
  - User-provided `McpSessionStore` wins
- [ ] `mvn verify` on the new module is green (the
      `Testcontainers` PostgreSQL test runs only if Docker is
      available — gate it via JUnit assumption if needed).
- [ ] `mvn verify` on the full reactor is green.

## Implementation notes

- **NO JPA**. This module intentionally uses plain JDBC. Do not add
  Hibernate, Spring Data JPA, or any entity-management
  dependencies. The `payload` column is a string / JSON blob, not
  an entity.
- **Dialect strategy**: the cleanest way to handle the upsert
  variance is a small `SessionStoreDialect` interface with methods
  like `String upsertSql()` and three implementations. The store
  holds a reference to one of them. Alternatively, use Spring's
  `Database` enum and `DatabaseIdProvider` pattern — but that's
  likely overkill.
- **Schema initialization**: ship the SQL files in the jar at
  `classpath:schema/*.sql` but do NOT auto-run them. Applications
  that want automatic schema init can use Spring Boot's
  `spring.sql.init.schema-locations` pointing at the appropriate
  file. This matches Spring Session's pattern and keeps the module
  non-invasive.
- **JSON column type**: PostgreSQL `JSONB` and MySQL `JSON` are
  the preferred types but the code should work with plain
  `VARCHAR`/`TEXT`/`CLOB` too — we don't use any JSON-specific SQL
  operators, just INSERT/UPDATE/SELECT of the whole column.
- **Cleanup task**: implement as a `ScheduledExecutorService`
  rather than `@Scheduled` to avoid forcing consumers to add
  `@EnableScheduling` to their application. The executor is
  constructor-owned and shut down in a `@PreDestroy` method.
- **Transactions**: each operation is a single statement, so no
  explicit transaction management is needed. `JdbcTemplate`'s
  default behavior (auto-commit or participate in an ambient
  transaction) is fine.
- **Testing with Testcontainers**: the PostgreSQL integration test
  should be annotated so it only runs when Docker is available
  (`@EnabledIf` or similar). H2 is the always-on test.
- **Don't** try to use Spring Session as a backend — this is a
  separate concern from HTTP session management. `McpSessionStore`
  and `HttpSession` are different data types with different
  lifecycles.
- **Don't** introduce a `DataSource` bean in the auto-config.
  Reuse whatever Spring Boot has already configured. This module
  is a consumer of `DataSource`, not a provider.
- **Commit granularity**: split into scaffolding → store
  implementation with H2 test → dialect strategy → PostgreSQL
  test → auto-config → done. Each leaves the tree green.
- **Documentation**: javadoc on `JdbcMcpSessionStore` must
  explain the expected schema, how TTL is enforced (at read time
  + periodic cleanup), and how to initialize the schema via
  `spring.sql.init.schema-locations`.
