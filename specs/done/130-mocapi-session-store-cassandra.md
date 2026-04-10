# Cassandra-backed McpSessionStore module (mocapi-session-store-cassandra)

## What to build

Create a new Maven module `mocapi-session-store-cassandra` that
provides an Apache Cassandra-backed implementation of
`McpSessionStore`. Follows the same drop-in pattern as the
Redis (spec 113), Hazelcast (114), and JDBC (115) modules: adding
the jar to a Spring Boot application's classpath automatically
replaces the default `InMemoryMcpSessionStore` fallback with a
Cassandra-backed store, as long as a `CqlSession` bean is present
(Spring Boot's `CassandraAutoConfiguration` provides this).

### Why Cassandra

Cassandra is an excellent fit for session storage:

1. **Native TTL** — Cassandra's `INSERT ... USING TTL <seconds>`
   sets per-row expiration directly in the storage engine. No
   cleanup task, no periodic DELETE query, no secondary index on
   `expires_at`. This is a significant operational win over the
   JDBC implementation.
2. **Write-optimized** — session save / update / touch operations
   are almost purely writes. Cassandra's LSM-tree architecture
   gives it very high write throughput with minimal contention,
   even under heavy concurrent session creation.
3. **Horizontally scalable from day one** — multi-node Cassandra
   clusters don't need leader election, master failover, or
   manual sharding. For teams that need regional session
   replication, Cassandra's `NetworkTopologyStrategy` handles it
   natively.
4. **Tunable consistency** — session data is usually eventual-
   consistency tolerant. Read `LOCAL_ONE` and write `LOCAL_QUORUM`
   gives a good balance for most deployments.

### Important caveat — session store only

Like the JDBC module, this spec adds **only** the session store
implementation. Cassandra isn't a pub/sub broker, so substrate
Mailbox and Notifier backends don't belong here (Cassandra's
"lightweight transactions" aren't a good fit for mailbox
semantics).

Substrate Journal could conceivably be backed by Cassandra (it's
an append-only event log, which Cassandra handles well), but
that's a substrate-side spec, not a mocapi spec. This module
is strictly about `McpSessionStore`.

### Module structure

```
mocapi-session-store-cassandra/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/callibrity/mocapi/session/cassandra/
    │   │   ├── CassandraMcpSessionStore.java
    │   │   └── CassandraMcpSessionStoreAutoConfiguration.java
    │   └── resources/
    │       ├── META-INF/spring/
    │       │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       └── schema/
    │           └── mocapi-session-store-cassandra.cql
    └── test/
        └── java/com/callibrity/mocapi/session/cassandra/
            ├── CassandraMcpSessionStoreTest.java         (Testcontainers)
            └── CassandraMcpSessionStoreAutoConfigurationTest.java
```

Add the module to the parent pom's `<modules>` list.

### Schema

Single-keyspace, single-table. Ship the CQL as
`classpath:schema/mocapi-session-store-cassandra.cql`:

```sql
-- Caller creates the keyspace themselves; this script assumes
-- the current session is already USE-ing the target keyspace.
CREATE TABLE IF NOT EXISTS mocapi_sessions (
  session_id text PRIMARY KEY,
  payload text
);
```

The table is deliberately simple — no `expires_at` column
because Cassandra's native TTL handles expiration. The
`payload` column holds the JSON-serialized `McpSession`.

Operators who want a non-default keyspace name override the
script or create their own keyspace and reference its name via
`spring.cassandra.keyspace-name`. Default keyspace: `mocapi` (or
whatever Spring Boot's Cassandra auto-configuration resolves).

### `CassandraMcpSessionStore` implementation

Constructor dependencies:
- `CqlSession` (auto-wired from Spring Boot's Cassandra
  auto-configuration)
- `ObjectMapper` (for JSON serialization of `McpSession`)
- Configuration properties (keyspace name, table name)

**CQL statements** (prepared at construction time):

```java
private final PreparedStatement saveStatement;    // INSERT ... USING TTL ?
private final PreparedStatement updateStatement;  // UPDATE ... SET payload = ?
                                                   // (preserving TTL — see notes)
private final PreparedStatement findStatement;    // SELECT payload FROM ... WHERE session_id = ?
private final PreparedStatement touchStatement;   // UPDATE ... SET payload = payload
                                                   // USING TTL ? WHERE session_id = ?
private final PreparedStatement deleteStatement;  // DELETE FROM ... WHERE session_id = ?
```

### `save(session, ttl)` — straightforward

```cql
INSERT INTO mocapi_sessions (session_id, payload)
VALUES (?, ?)
USING TTL ?;
```

Native per-row TTL. When the TTL elapses, Cassandra automatically
removes the row during compaction. No cleanup task.

### `update(sessionId, session)` — preserving TTL

This is the tricky one. Cassandra's `UPDATE` statement accepts
`USING TTL`, but specifying it REPLACES the existing TTL.
Omitting it REMOVES the TTL entirely (!). To preserve the
existing TTL, the store must:

1. Query the current `TTL(payload)` via
   `SELECT TTL(payload) FROM mocapi_sessions WHERE session_id = ?`
2. Use the returned value (seconds remaining) in an
   `UPDATE ... USING TTL <remaining>` statement

Both steps happen in the `update(...)` method. Two round-trips
to Cassandra per update, which is acceptable since updates are
infrequent compared to reads.

Alternative: store `expires_at` as a column (like the JDBC
implementation) and manage TTL at the application level. But
this loses the native-TTL advantage and requires an `expires_at`
column + a periodic cleanup. **Stick with the two-statement
approach** — it's cleaner for typical workloads.

### `touch(sessionId, ttl)` — extending TTL

Cassandra has no native "extend TTL" operation, but setting a
column to its own value with a new TTL works:

```cql
UPDATE mocapi_sessions
USING TTL ?
SET payload = payload  -- no-op re-write that resets TTL
WHERE session_id = ?;
```

Wait — Cassandra doesn't allow `SET column = column` in an
UPDATE. The cleaner approach: read the current payload, then
issue an UPDATE with the same payload and a new TTL. Two
round-trips again.

Or: `UPDATE ... USING TTL <new_ttl> SET payload = :current_payload
WHERE session_id = :sid` where `:current_payload` is fetched
from the previous SELECT. This is the same two-step pattern as
`update`.

### `find(sessionId)` — simple SELECT

```cql
SELECT payload FROM mocapi_sessions WHERE session_id = ?;
```

If the row has expired, Cassandra doesn't return it. No filter
needed on `expires_at` because there is no `expires_at` column.

### `delete(sessionId)` — simple DELETE

```cql
DELETE FROM mocapi_sessions WHERE session_id = ?;
```

Cassandra's idempotent DELETE handles the "row doesn't exist"
case silently.

### Auto-configuration

`CassandraMcpSessionStoreAutoConfiguration` activates when:
- A `CqlSession` bean is present (Spring Boot's
  `CassandraAutoConfiguration` has run)
- No user-provided `McpSessionStore` bean is present

Uses `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
and `@ConditionalOnBean(CqlSession.class)` +
`@ConditionalOnMissingBean(McpSessionStore.class)`.

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
    <artifactId>spring-boot-starter-data-cassandra</artifactId>
    <optional>true</optional>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <!-- Test: Testcontainers Cassandra -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>cassandra</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Note: `spring-boot-starter-data-cassandra` pulls in the DataStax
Java driver's `CqlSession` auto-configuration. We don't use any
of the Spring Data Cassandra repository/template abstractions —
we go directly to `CqlSession` with prepared statements. This
keeps the dependency surface minimal (no entity mapping, no
reflection-based query generation).

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-cassandra` exists with
      correct parent reference.
- [ ] Listed in parent pom's `<modules>`.
- [ ] Package is `com.callibrity.mocapi.session.cassandra`.
- [ ] License headers and spotless conventions match the rest
      of the project.

### Schema

- [ ] Ships `classpath:schema/mocapi-session-store-cassandra.cql`
      with a `CREATE TABLE IF NOT EXISTS mocapi_sessions` statement
      containing `session_id text PRIMARY KEY` and `payload text`.

### Implementation

- [ ] `CassandraMcpSessionStore` implements all six methods using
      prepared `CqlSession` statements.
- [ ] `save(session, ttl)` uses `INSERT ... USING TTL ?` with the
      TTL in seconds.
- [ ] `update(sessionId, session)` queries the current TTL via
      `SELECT TTL(payload)` and re-issues the update with the
      remaining TTL so the expiration is preserved.
- [ ] `touch(sessionId, ttl)` updates with the new TTL while
      preserving the current payload (read + update round-trip
      if needed).
- [ ] `find(sessionId)` returns `Optional.empty()` for rows
      that don't exist or have expired (Cassandra automatically
      omits expired rows from query results).
- [ ] `delete(sessionId)` issues `DELETE` and doesn't fail on
      missing rows.
- [ ] Keyspace and table names are configurable via
      `mocapi.session.cassandra.keyspace` and
      `mocapi.session.cassandra.table` properties
      (defaults: `mocapi` and `mocapi_sessions`).
- [ ] JSON serialization uses the application's `ObjectMapper`.

### Auto-configuration

- [ ] `CassandraMcpSessionStoreAutoConfiguration` is registered
      in the auto-config imports file.
- [ ] `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
- [ ] `@ConditionalOnBean(CqlSession.class)`
- [ ] `@ConditionalOnMissingBean(McpSessionStore.class)`
- [ ] Returns a `McpSessionStore` bean.

### Tests

- [ ] `CassandraMcpSessionStoreTest` uses Testcontainers
      Cassandra (`cassandra:5` image or similar) and exercises
      all six methods.
- [ ] Dedicated tests cover:
  - **Native TTL expiration**: save with 1s TTL, sleep 2s, find
    returns empty (Cassandra has already removed the row).
  - **update preserves TTL**: save with 10s TTL, update payload,
    verify the TTL remaining is still close to 10s (query
    `SELECT TTL(payload)` to assert the value).
  - **touch extends TTL**: save with 1s TTL, touch to 10s,
    sleep 2s, find still returns the session.
- [ ] `CassandraMcpSessionStoreAutoConfigurationTest` uses
      `ApplicationContextRunner` with a mock or embedded
      `CqlSession` and verifies the conditional activation.
- [ ] `mvn verify` on the new module is green. Testcontainers
      Cassandra only runs if Docker is available — gate via
      JUnit assumption if needed.
- [ ] `mvn verify` on the full reactor is green.

## Implementation notes

- **Dependency on nothing else in this session's spec queue**
  — this module is independent of the Redis, Hazelcast, and
  JDBC specs. It can land in any order relative to 113-115.
- **Native TTL is the killer feature**. Do not implement
  application-managed expiration via an `expires_at` column
  and a cleanup task. Lean on Cassandra's built-in TTL — it's
  what makes this backend compelling.
- **`update` round-trip cost**: two round-trips per update
  (SELECT TTL + UPDATE) is acceptable because session updates
  are rare (only on `withLogLevel(...)` state changes, which
  happen a few times per session at most). If profiling shows
  update becoming a bottleneck, revisit — but don't
  pre-optimize.
- **Schema initialization**: ship the CQL file in the jar but
  do not auto-run it. Document in the module's javadoc /
  README how operators initialize the schema via `cqlsh` or
  Spring Boot's `spring.cassandra.schema-action: create` /
  `create-if-not-exists` (Spring Boot's Cassandra auto-config
  has this mechanism).
- **Consistency levels**: the module should default to
  `LOCAL_ONE` reads and `LOCAL_QUORUM` writes, which is a
  reasonable balance for session data. Make the consistency
  level configurable via properties for operators who want
  stricter or looser guarantees.
- **DataStax driver version**: whatever Spring Boot 4 pulls
  in via `spring-boot-starter-data-cassandra` is fine. No
  need to pin a specific driver version.
- **Don't** depend on Spring Data Cassandra repository
  abstractions. Use `CqlSession` directly with prepared
  statements. Keeps the dependency surface minimal and the
  performance profile predictable.
- **Commit granularity**: one commit for module scaffolding,
  one for the store implementation + Testcontainers test,
  one for auto-config + its test.
- **A future spec can add a Cassandra example app** (similar
  to specs 127-129) with a docker-compose file and README
  showing how to run against a local Cassandra instance. Not
  in scope for this spec — this spec is the library module
  only.
