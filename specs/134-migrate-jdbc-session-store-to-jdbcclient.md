# Migrate JdbcMcpSessionStore to JdbcClient + validate table name

## What to build

Two complementary changes to `mocapi-session-store-jdbc`, bundled
into a single spec because they touch the same file and are
cohesive:

1. **Migrate from `JdbcTemplate` to `JdbcClient`** — Spring
   Framework 6.1's modern fluent JDBC API. `JdbcClient` is a
   facade over `JdbcTemplate` + `NamedParameterJdbcTemplate` and
   is Spring's recommended choice for new code. No new
   dependencies required — `spring-boot-starter-jdbc` already
   provides it, and Spring Boot auto-configures a `JdbcClient`
   bean whenever a `DataSource` is present.
2. **Validate `tableName` at construction time** — SonarCloud
   flagged 5 security hotspots (`java:S2077` /
   `javasecurity:S3649`) because `tableName` is string-
   concatenated into SQL. Fix by rejecting anything that
   doesn't match a strict SQL-identifier regex. The
   concatenation itself remains (table names can't be
   parameterized via JDBC `?` placeholders), but the input is
   guaranteed safe.

### Why bundle them?

Both changes rewrite the same six methods in
`JdbcMcpSessionStore` (`save`, `update`, `find`, `touch`,
`delete`, `cleanupExpired`) and its constructor. Splitting would
mean editing the same lines twice in sequence, producing a
noisier git history. Bundling keeps the diff focused on "the
JDBC session store got a modernization pass."

### `JdbcClient` migration patterns

**Before (JdbcTemplate):**
```java
jdbcTemplate.update(
    "UPDATE " + tableName + " SET payload = ? WHERE session_id = ?",
    json, sessionId);
```

**After (JdbcClient, named parameters):**
```java
jdbcClient
    .sql("UPDATE " + tableName + " SET payload = :payload WHERE session_id = :sid")
    .param("payload", json)
    .param("sid", sessionId)
    .update();
```

**For single-row query (after):**
```java
Optional<McpSession> result = jdbcClient
    .sql("SELECT payload FROM " + tableName + " WHERE session_id = :sid AND expires_at > :now")
    .param("sid", sessionId)
    .param("now", Timestamp.from(Instant.now()))
    .query((rs, rowNum) -> objectMapper.readValue(rs.getString("payload"), McpSession.class))
    .optional();
```

Named parameters (`:payload`, `:sid`, `:now`) are preferred for
readability. Positional `?` placeholders also work via
`.param(value)`.

### Constructor update — replace `JdbcTemplate` field with `JdbcClient`

```java
public class JdbcMcpSessionStore implements McpSessionStore, AutoCloseable {

  private static final java.util.regex.Pattern SAFE_IDENTIFIER =
      java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;
  private final JdbcDialect dialect;
  private final String tableName;
  private final ScheduledExecutorService cleanupExecutor;

  JdbcMcpSessionStore(
      JdbcClient jdbcClient,
      ObjectMapper objectMapper,
      JdbcDialect dialect,
      String tableName,
      boolean cleanupEnabled,
      Duration cleanupInterval) {
    if (tableName == null || !SAFE_IDENTIFIER.matcher(tableName).matches()) {
      throw new IllegalArgumentException(
          "Invalid table name '"
              + tableName
              + "'. Table names must match "
              + SAFE_IDENTIFIER.pattern()
              + " — only letters, digits, and underscores are allowed, "
              + "and the name must not start with a digit.");
    }
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;
    this.dialect = dialect;
    this.tableName = tableName;
    // ... scheduled cleanup setup unchanged
  }
```

### Six method rewrites

Each method migrates from `jdbcTemplate.update/query` to the
corresponding `jdbcClient.sql(...).param(...).update()` /
`.query(...).list()/.optional()`. Preserve exact semantics:
same SQL statements, same parameter values, same return
semantics.

### `JdbcDialect.upsertSql(String tableName)` — unchanged signature, documented invariant

The dialect strategy interface still takes a `String tableName`
and returns dialect-specific upsert SQL. Since the store
validates `tableName` before calling any dialect method, the
dialect implementations can safely assume the input is a valid
bare SQL identifier. Add a javadoc invariant:

```java
/**
 * Returns the dialect-specific upsert SQL for the given table.
 * <strong>The implementer may assume the {@code tableName} is a
 * validated bare SQL identifier</strong> — callers must validate
 * before invoking this method.
 */
String upsertSql(String tableName);
```

No code changes to the dialect implementations themselves —
they were already correct under the implicit assumption that
the caller validates.

### Auto-configuration update

`JdbcMcpSessionStoreAutoConfiguration` currently injects
`JdbcTemplate`. Change it to inject `JdbcClient`:

```java
@Bean
@ConditionalOnMissingBean(McpSessionStore.class)
McpSessionStore jdbcMcpSessionStore(
    JdbcClient jdbcClient,
    ObjectMapper objectMapper,
    JdbcDialect dialect,
    JdbcSessionStoreProperties properties) {
  return new JdbcMcpSessionStore(
      jdbcClient,
      objectMapper,
      dialect,
      properties.getTableName(),
      properties.isCleanupEnabled(),
      properties.getCleanupInterval());
}
```

`@ConditionalOnBean(...)` clauses may also need updating from
`JdbcTemplate.class` to `JdbcClient.class`. Verify.

## Acceptance criteria

### JdbcClient migration

- [ ] `JdbcMcpSessionStore` constructor takes a `JdbcClient`
      parameter instead of `JdbcTemplate`.
- [ ] All six methods (`save`, `update`, `find`, `touch`,
      `delete`, `cleanupExpired`) use the `JdbcClient` fluent
      API. No `jdbcTemplate` references remain in the class.
- [ ] Named parameters (`:name`) are used throughout for
      readability. Positional `?` is acceptable only where the
      existing JDBC statement already used it and the rewrite
      would be equally clear.
- [ ] `JdbcMcpSessionStoreAutoConfiguration` injects
      `JdbcClient` instead of `JdbcTemplate`.
- [ ] `@ConditionalOnBean` checks reference `JdbcClient.class`
      (or the common prerequisite `DataSource.class` — both
      approaches work).

### Table name validation

- [ ] `JdbcMcpSessionStore` has a static
      `Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$")`.
- [ ] The constructor validates `tableName` against this
      pattern and throws `IllegalArgumentException` on
      mismatch or null.
- [ ] The error message includes the pattern and a short
      explanation ("letters, digits, underscores; must not
      start with a digit").
- [ ] `JdbcDialect.upsertSql(String tableName)` javadoc
      documents the invariant that the caller must validate
      the table name.

### Sonar hotspots

- [ ] After this spec lands and CI re-runs the Sonar scan,
      the 5 `java:S2077` / `javasecurity:S3649` hotspots in
      `JdbcMcpSessionStore` are either:
  - Automatically cleared because Sonar's taint analysis
    recognizes the upstream validation, OR
  - Manually marked "Safe" on SonarCloud with a comment
    pointing at the constructor validation.
- [ ] No security hotspots remain open in the JDBC session
      store.

### Tests

- [ ] Existing `JdbcMcpSessionStoreTest` tests are updated to
      construct the store with a `JdbcClient` bean. Spring's
      `JdbcClient.create(dataSource)` static factory builds
      one from a `DataSource` without needing a full Spring
      context.
- [ ] A new test case: **rejects invalid table names** —
      parameterized over at least:
  - `"table with space"`
  - `"'quoted'"`
  - `"--comment"`
  - `";DROP"`
  - `"1startswithdigit"`
  - `""` (empty)
  - `null`
  Each must throw `IllegalArgumentException` at construction.
- [ ] A new test case: **accepts valid table names** —
      constructs with `"my_custom_sessions"` and round-trips a
      save/find.
- [ ] Existing TTL expiration, update-preserves-TTL, and
      scheduled cleanup tests continue to pass with the
      `JdbcClient` migration.

### Integration test (PostgreSQL)

- [ ] `JdbcMcpSessionStorePostgresIT` (Testcontainers) still
      passes. The `JdbcClient` migration shouldn't change
      behavior against a real database — this test is the
      regression guard.

### Build

- [ ] `mvn verify` passes across the full reactor.
- [ ] The `mocapi-compat` conformance suite still passes 39/39.
- [ ] No new warnings from the compiler or Spotless.

## Implementation notes

- **`JdbcClient` bean autowiring**: Spring Boot 3.2+ (which
  ships with Spring Framework 6.1+) auto-configures a
  `JdbcClient` bean via `JdbcClientAutoConfiguration` whenever
  a `DataSource` and a `NamedParameterJdbcTemplate` are
  present. No explicit bean definition needed — just inject
  `JdbcClient` directly into the store.
- **Spring Framework version**: mocapi uses Spring Boot 4.0.x
  (confirmed from the parent pom's `spring-boot.version =
  4.0.5`). Spring Boot 4 uses Spring Framework 6.2+ which
  includes `JdbcClient`. No version bumps needed.
- **`NamedParameterJdbcTemplate` underneath**: `JdbcClient`
  delegates to `NamedParameterJdbcTemplate` for named
  parameters and `JdbcTemplate` for positional parameters.
  Performance characteristics are identical to the current
  implementation.
- **`optional()` vs `single()` for query results**: use
  `.optional()` for `find(sessionId)` because a missing
  session returns `Optional.empty()` — it's the common case,
  not an error. Use `.single()` only when a missing row
  should throw.
- **`RowMapper` vs `BeanPropertyRowMapper`**: the store's
  `find` method maps the `payload` column through the
  application's `ObjectMapper`. This is a manual
  transformation, not a bean property match. Use the
  `(rs, rowNum) -> { ... }` lambda form, not
  `BeanPropertyRowMapper`.
- **Don't split into two commits**. The migration and the
  validation are tightly coupled — every rewritten method
  also needs the constructor's validated-identifier
  assumption. Single focused commit.
- **Documentation**: the class-level javadoc should mention
  that the store uses `JdbcClient` and validates the table
  name at construction. No need to go into detail about the
  security rationale — a brief mention is sufficient.
- **SonarCloud hotspot review**: after the CI scan runs with
  the new code, check SonarCloud. If the 5 hotspots remain
  in "Review" state (Sonar's taint analysis is conservative),
  manually mark each as "Safe" with a comment. One-time
  action.
- **Ralph status**: as of writing, Ralph is still working
  through spec 120 (remaining minor Sonar issues). He's a
  good distance away from this spec in the queue, so there's
  no urgency on implementation — but the spec should be in
  place so it's ready when he reaches it.
