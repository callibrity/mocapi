# NATS-backed McpSessionStore module (mocapi-session-store-nats)

## What to build

Create a new Maven module `mocapi-session-store-nats` that
provides a NATS JetStream KV backed implementation of
`McpSessionStore`. Same drop-in pattern as the other session
store modules (`mocapi-session-store-redis`,
`mocapi-session-store-hazelcast`, `mocapi-session-store-jdbc`,
`mocapi-session-store-cassandra`, `mocapi-session-store-mongodb`,
`mocapi-session-store-dynamodb`): adding the jar to a Spring
Boot application's classpath automatically replaces the
in-memory fallback with the NATS implementation whenever an
`io.nats.client.Connection` bean is present.

### Why NATS

1. **Single-backplane deployment**. Substrate already publishes
   `substrate-mailbox-nats`, `substrate-journal-nats`, and
   `substrate-notifier-nats` — all three other substrate SPIs
   have native NATS implementations. Adding a NATS session store
   means the **entire mocapi stack** can run on a single NATS
   container: sessions, journal, mailbox, and notifier all
   using the same connection. No other substrate backend
   combination gets you to four-out-of-four with a single piece
   of infrastructure.
2. **JetStream KV is the right primitive for sessions**. NATS
   JetStream exposes a Key-Value abstraction (`KeyValue` /
   `KeyValueManagement` APIs) built on top of streams. It
   provides get/put/delete, bucket-level TTL (max age), and
   durable replication — exactly the subset of operations that
   `McpSessionStore` needs. Substrate's own
   `substrate-mailbox-nats` is already "backed by NATS KV
   Store" per its own description, so this is a proven pattern
   inside the mocapi ecosystem.
3. **Lightweight ops**. NATS is a single Go binary; a clustered
   NATS deployment is dramatically simpler to run than a
   clustered Redis, Hazelcast, or Cassandra. For teams who want
   a distributed mocapi without taking on a full database or
   in-memory grid, NATS is the lowest-friction option.
4. **Java client is official and stable**. `io.nats:jnats` is
   the official NATS Java client, published by Synadia, with
   first-class JetStream and KV support since 2.14. No fragile
   community integrations.

### Session-store-only scope (but with a compelling sibling story)

This spec covers the session store module only. It does **not**
create a NATS example app — that's a follow-up spec (see the
"Follow-up work" section at the end).

Unlike every other session store module in the repo (which
pair with a backend that only handles *sessions*), the NATS
session store is the final piece that lets a single NATS
cluster serve as mocapi's entire distributed backplane. The
payoff is in the follow-up example, but the module itself is
strictly a session store implementation.

### Module structure

```
mocapi-session-store-nats/
├── pom.xml
└── src/
    ├── main/
    │   └── java/com/callibrity/mocapi/session/nats/
    │       ├── NatsMcpSessionStore.java
    │       └── NatsMcpSessionStoreAutoConfiguration.java
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/callibrity/mocapi/session/nats/
            ├── NatsMcpSessionStoreTest.java    (Testcontainers)
            └── NatsMcpSessionStoreAutoConfigurationTest.java
```

### Data shape

One JetStream KV **bucket** (`mocapi-sessions` by default), one
entry per session:

- **Key**: session ID (string)
- **Value**: JSON-serialized `McpSession` (`byte[]`, UTF-8)
- **TTL**: bucket-level `max_age`, set to
  `mocapi.session-timeout` at bucket-create time

KV buckets in NATS have a **bucket-wide max age** — all entries
in the bucket expire `max_age` after their *last* update.
mocapi uses a single global session timeout
(`mocapi.session-timeout`, default 1h), so bucket-level TTL is
a natural fit: every session shares the same expiration
policy.

> **Note on per-key TTL**: NATS 2.11+ introduced per-message
> TTL in JetStream, and by extension per-key TTL in KV.
> However, client support varies and it requires a newer
> server. This spec uses bucket-level TTL for broadest
> compatibility. A future spec can add per-key TTL as an
> optional optimization once we've confirmed minimum NATS
> server version requirements.

### Bucket initialization

On startup (from the store's constructor or `@PostConstruct`),
create the bucket idempotently:

```java
KeyValueManagement kvm = connection.keyValueManagement();
try {
  kvm.getBucketInfo(bucketName); // throws if missing
} catch (JetStreamApiException e) {
  kvm.create(
      KeyValueConfiguration.builder()
          .name(bucketName)
          .maxHistoryPerKey(1)          // we don't need version history
          .ttl(sessionTimeout)          // bucket-wide max age
          .storageType(StorageType.File) // durable by default
          .build());
}
KeyValue kv = connection.keyValue(bucketName);
```

`maxHistoryPerKey(1)` explicitly disables history retention —
we only care about the latest value per session. File storage
is the durable default; operators can override at the NATS
server level if they want memory-only buckets.

The `sessionTimeout` value is injected from the existing
`mocapi.session-timeout` property so both the bucket TTL and
the touch/save TTLs line up.

### `NatsMcpSessionStore` implementation

Constructor dependencies:
- `io.nats.client.Connection` (injected from Spring bean)
- `ObjectMapper` (for JSON serialization of `McpSession`)
- `bucketName` (configurable)
- `sessionTimeout` (from `MocapiProperties.sessionTimeout`)

Fields:
- `private final KeyValue kv;` (cached after init)
- `private final ObjectMapper objectMapper;`

#### `save(session, ttl)`

```java
@Override
public void save(McpSession session, Duration ttl) {
  byte[] payload = objectMapper.writeValueAsBytes(session);
  try {
    kv.put(session.sessionId(), payload);
  } catch (JetStreamApiException | IOException e) {
    throw new IllegalStateException("Failed to save session " + session.sessionId(), e);
  }
}
```

The `ttl` parameter is **ignored** — the bucket-level `max_age`
handles expiration. Document this clearly in the javadoc: the
store honors the `mocapi.session-timeout` at bucket-creation
time, and all sessions share that TTL.

> **Mismatch caveat**: if an operator configures
> `mocapi.session-timeout` after the bucket already exists
> with a different `max_age`, the new value won't apply until
> the bucket is deleted and recreated. Document this in the
> implementation notes as a known limitation.

#### `update(sessionId, session)` — preserving TTL

NATS KV doesn't have a KEEPTTL equivalent. A re-put resets the
age. For the `update` case (which is supposed to preserve
TTL), the cleanest semantics are:

**Accept that update also resets the TTL.** Document this
explicitly. In practice, mocapi's `update` call is followed
very quickly by a `touch` on the next request, so the
distinction is negligible. The Redis implementation uses
KEEPTTL as an optimization; the NATS implementation doesn't
have that primitive, so update behaves like a `save`.

```java
@Override
public void update(String sessionId, McpSession session) {
  byte[] payload = objectMapper.writeValueAsBytes(session);
  try {
    kv.put(sessionId, payload); // resets bucket-level TTL
  } catch (JetStreamApiException | IOException e) {
    throw new IllegalStateException("Failed to update session " + sessionId, e);
  }
}
```

> **Alternative considered**: read current value, skip update
> if unchanged, otherwise re-put. Rejected — adds a round trip
> for zero real benefit.

#### `find(sessionId)`

```java
@Override
public Optional<McpSession> find(String sessionId) {
  try {
    KeyValueEntry entry = kv.get(sessionId);
    if (entry == null || entry.getOperation() != KeyValueOperation.PUT) {
      return Optional.empty();
    }
    return Optional.of(objectMapper.readValue(entry.getValue(), McpSession.class));
  } catch (JetStreamApiException | IOException e) {
    throw new IllegalStateException("Failed to find session " + sessionId, e);
  }
}
```

The `getOperation() != PUT` check handles the DELETE tombstone
case — NATS KV keeps a tombstone entry briefly after a delete
operation. Filtering out non-PUT operations ensures `find`
returns empty for recently-deleted keys.

`kv.get()` returns `null` if the key doesn't exist, so the
null check covers the "never existed" and "expired past
max_age" cases.

#### `touch(sessionId, ttl)`

NATS KV has no native "refresh TTL without rewriting value"
primitive. The only way to reset the bucket-level age is to
re-put the current value. Implementation:

```java
@Override
public void touch(String sessionId, Duration ttl) {
  try {
    KeyValueEntry entry = kv.get(sessionId);
    if (entry == null || entry.getOperation() != KeyValueOperation.PUT) {
      return; // nothing to touch
    }
    kv.put(sessionId, entry.getValue()); // re-put to reset age
  } catch (JetStreamApiException | IOException e) {
    throw new IllegalStateException("Failed to touch session " + sessionId, e);
  }
}
```

Two round trips (get + put). Acceptable because `touch` is
called at most once per request and NATS operations are
microsecond-latency.

> **Alternative considered**: skip the get and accept that a
> `touch` on a missing key silently creates an empty value.
> Rejected — violates the store contract ("touch does nothing
> if the session doesn't exist").

#### `delete(sessionId)`

```java
@Override
public void delete(String sessionId) {
  try {
    kv.delete(sessionId);
  } catch (JetStreamApiException | IOException e) {
    throw new IllegalStateException("Failed to delete session " + sessionId, e);
  }
}
```

Idempotent (deleting a non-existent key is a no-op in KV
semantics — it just creates a DELETE tombstone).

### Auto-configuration

```java
import com.callibrity.mocapi.MocapiAutoConfiguration;
import com.callibrity.mocapi.MocapiProperties;
import com.callibrity.mocapi.session.McpSessionStore;
import io.nats.client.Connection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers a {@link NatsMcpSessionStore} when an {@link Connection} bean is available.
 * The in-memory fallback in {@link MocapiAutoConfiguration} steps aside via its own
 * {@code @ConditionalOnMissingBean} once this backend bean is present.
 */
@AutoConfiguration(before = MocapiAutoConfiguration.class)
@ConditionalOnClass(Connection.class)
@ConditionalOnBean(Connection.class)
public class NatsMcpSessionStoreAutoConfiguration {

  @Bean
  public McpSessionStore natsMcpSessionStore(
      Connection connection,
      ObjectMapper objectMapper,
      MocapiProperties mocapiProperties,
      @Value("${mocapi.session.nats.bucket:mocapi-sessions}") String bucketName) {
    return new NatsMcpSessionStore(
        connection, objectMapper, bucketName, mocapiProperties.getSessionTimeout());
  }
}
```

**Critical conventions** (match the other session store
modules after the spec 130b/c cleanup):

1. **No `@ConditionalOnMissingBean(McpSessionStore.class)`** on
   the backend bean. The guard lives solely on the fallback
   bean in `MocapiAutoConfiguration.mcpSessionStore()`. If a
   second backend is on the classpath, Spring fails loudly
   with a duplicate bean error — which is the correct outcome.
2. **No `after = SomeNatsAutoConfiguration.class`**. Unlike
   Redis/Cassandra/Hazelcast/MongoDB, Spring Boot does not
   ship a NATS auto-configuration — NATS is a third-party
   concern. The `Connection` bean is expected to come from
   one of:
   - A user-defined `@Bean Connection(...)` in the
     application.
   - A third-party spring-boot-nats library.
   - A future `mocapi-nats-support` module (not in scope
     here).

   Because there's no first-party NATS auto-config to order
   after, `@ConditionalOnBean(Connection.class)` alone is
   sufficient — Spring Boot's condition evaluation waits for
   all bean definitions to be registered before making a
   final decision.
3. **`@ConditionalOnClass(Connection.class)`** ensures the
   auto-config is silently skipped if `io.nats:jnats` isn't on
   the classpath at all.

### Dependencies

```xml
<dependencies>
  <dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-core</artifactId>
    <version>${project.version}</version>
  </dependency>

  <dependency>
    <groupId>io.nats</groupId>
    <artifactId>jnats</artifactId>
    <!-- Version from substrate's published mailbox/journal/notifier NATS
         modules to stay in lockstep; currently 2.25.2. If the substrate-bom
         exports jnats, prefer that. -->
    <optional>true</optional>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <dependency>
    <groupId>tools.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
  </dependency>

  <!-- Testcontainers — NATS has a community module -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>nats</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <exclusions>
      <exclusion>
        <groupId>com.vaadin.external.google</groupId>
        <artifactId>android-json</artifactId>
      </exclusion>
    </exclusions>
  </dependency>
</dependencies>
```

**Check substrate-bom first**: if
`org.jwcarman.substrate:substrate-bom` already manages the
`jnats` version (because of `substrate-mailbox-nats` et al.),
remove the explicit version on the `jnats` dependency and let
the BOM govern it. This keeps mocapi in lockstep with whatever
version substrate is using.

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-nats` exists with
      correct parent reference.
- [ ] Listed in parent pom's `<modules>` alongside the other
      session store modules.
- [ ] Package is `com.callibrity.mocapi.session.nats`.

### Implementation

- [ ] `NatsMcpSessionStore` implements all six `McpSessionStore`
      methods using the `io.nats.client.KeyValue` and
      `KeyValueManagement` APIs.
- [ ] Bucket is created idempotently at construction time via
      `keyValueManagement.getBucketInfo` + `create` if missing.
      Bucket config: `maxHistoryPerKey(1)`, `ttl(sessionTimeout)`,
      `StorageType.File`.
- [ ] `save` writes the JSON-serialized session as bytes. The
      passed `ttl` parameter is documented as ignored because
      the bucket-level max age governs expiration.
- [ ] `update` re-puts the new value. Documented as resetting
      the age (unlike Redis KEEPTTL).
- [ ] `find` filters out non-PUT entries (DELETE tombstones)
      and returns `Optional.empty()` for missing keys.
- [ ] `touch` does a get + re-put of the current value. Noops
      on missing keys.
- [ ] `delete` calls `kv.delete(sessionId)` (idempotent).
- [ ] Bucket name configurable via
      `mocapi.session.nats.bucket` (default `mocapi-sessions`).
- [ ] JSON serialization uses the application's `ObjectMapper`.
- [ ] All NATS client exceptions
      (`JetStreamApiException`, `IOException`) are wrapped as
      `IllegalStateException` with descriptive messages
      identifying the session ID and operation.

### Auto-configuration

- [ ] `NatsMcpSessionStoreAutoConfiguration` is in the
      auto-config imports file.
- [ ] `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
- [ ] `@ConditionalOnClass(Connection.class)`
- [ ] `@ConditionalOnBean(Connection.class)`
- [ ] **No `@ConditionalOnMissingBean(McpSessionStore.class)`**
      on the backend bean method. The guard lives on the
      in-memory fallback in `MocapiAutoConfiguration` only.
- [ ] Returns a `McpSessionStore` bean.
- [ ] Wired to read `mocapi.session-timeout` via
      `MocapiProperties` so the bucket TTL matches mocapi's
      global session timeout.

### Tests

- [ ] `NatsMcpSessionStoreTest` uses Testcontainers (`nats:latest`
      image with JetStream enabled via
      `.withCommand("-js")` or equivalent) and exercises all six
      `McpSessionStore` methods end-to-end:
  - **save + find**: put a session, find returns it.
  - **save + update + find**: update replaces the payload,
    find returns the new value.
  - **save + touch + find**: touch on an existing session;
    find still returns it. Optional: verify the bucket
    history / revision advanced.
  - **save + delete + find**: delete, then find returns
    empty (tombstone filtered).
  - **find on missing key**: returns empty.
  - **delete on missing key**: does not throw.
  - **touch on missing key**: does not throw, does not
    create a new entry.
- [ ] A test verifies that **the bucket is created at startup**
      by inspecting `keyValueManagement.getBucketInfo` and
      asserting the expected config (`maxHistoryPerKey=1`,
      `ttl` equals the configured session timeout).
- [ ] A test verifies **TTL filtering**: construct the store
      with a very short session timeout (e.g., `Duration.ofSeconds(1)`),
      save a session, wait with `Awaitility` for longer than
      the TTL, and assert that `find` returns empty. (Uses
      Awaitility per spec 138 conventions — **no
      `Thread.sleep`**.)
- [ ] `NatsMcpSessionStoreAutoConfigurationTest` uses
      `ApplicationContextRunner` with a mock or test-containers
      `Connection` and verifies:
  - Conditional activation when a `Connection` bean is
    present.
  - Silent skip when `Connection` class is not on the
    classpath (use `FilteredClassLoader`).
  - Silent skip when no `Connection` bean exists.
  - When activated, the in-memory fallback from
    `MocapiAutoConfiguration` does not fire (the resulting
    bean is a `NatsMcpSessionStore`, not an
    `InMemoryMcpSessionStore`).
- [ ] `mvn verify` on the new module is green.
- [ ] `mvn verify` on the full reactor is green.

## Implementation notes

- **Bucket TTL is global, not per-session**. This is a
  fundamental property of NATS KV bucket-level `max_age`. If
  a future version of mocapi supports per-session TTLs, the
  NATS implementation will need to migrate to per-key TTL
  (which requires NATS 2.11+ and verified client support).
  For now, all sessions share `mocapi.session-timeout`.
- **`update` resets TTL**. Unlike the Redis implementation
  (which uses `SET ... KEEPTTL`), NATS has no primitive to
  refresh the value without resetting the bucket-age clock.
  This is a slight semantic difference; document it clearly
  in the `NatsMcpSessionStore` class-level javadoc.
- **`touch` does a get + put**. Two round trips instead of
  Redis's one-round-trip `EXPIRE`. NATS KV has no `EXPIRE`
  equivalent, so this is unavoidable with bucket-level TTL.
- **Don't create a `Connection` bean in this module**. The
  `Connection` is a shared piece of infrastructure — if the
  same app also uses `substrate-mailbox-nats` /
  `substrate-journal-nats` / `substrate-notifier-nats`, all
  four modules consume the same `Connection` bean. Creating a
  `Connection` here would either conflict with substrate's
  assumption (there's no substrate-provided Connection
  either) or duplicate a user-provided bean. Let the user
  provide one. Document this in the module README.
- **Bucket name and sessionTimeout mismatch**: if an operator
  changes `mocapi.session-timeout` after the bucket has been
  created, the existing bucket keeps its original `max_age`.
  Options: (a) check bucket config at startup and warn if
  mismatched; (b) throw if mismatched; (c) silently accept
  the existing bucket. Go with **(a)**: log a `WARN`
  describing the mismatch and instructing the operator to
  delete and recreate the bucket manually.
- **Don't depend on substrate modules**. This module is
  purely a session store — it should not pull in any of the
  `substrate-*-nats` artifacts. The `Connection` bean
  dependency is type-level only (`io.nats.client.Connection`),
  and the jnats client jar is the sole NATS dependency.
- **Commit granularity**:
  1. Scaffolding: pom, package structure, skeleton
     `NatsMcpSessionStore` with stub methods.
  2. Real implementation + Testcontainers test.
  3. Auto-config + its test.
  4. README for the module explaining how to provide the
     `Connection` bean (quick snippet showing
     `Nats.connect("nats://localhost:4222")`).
- **Mirror with spec 120-level Sonar expectations**. No
  `Thread.sleep` in tests (use Awaitility). No assertion-less
  tests. Chain AssertJ assertions per spec 119 conventions.
  Keep `@SuppressWarnings` out unless required by a specific
  spec-mandated deprecation (none applicable here).

## Follow-up work (not in scope for this spec)

- **`mocapi-nats-spring-boot-starter`** — all-in-one starter
  that bundles `mocapi-spring-boot-starter`,
  `mocapi-session-store-nats`, `substrate-mailbox-nats`,
  `substrate-journal-nats`, and `substrate-notifier-nats` plus
  a `Connection` bean producer driven by
  `mocapi.nats.url` / `mocapi.nats.credentials` properties.
  This is the piece that makes "NATS-only distributed mocapi"
  a one-dependency experience.
- **`examples/nats/`** — example app that depends on the NATS
  all-in-one starter, ships a `docker-compose.yml` spinning
  up a single `nats:latest` container with JetStream enabled
  (`command: ["-js"]`), and demonstrates the full substrate
  stack running on a single backplane. This is the compelling
  "here's the simplest distributed mocapi deployment" demo.
- **`examples/cassandra-nats/`** — mixed-topology example
  (Cassandra for sessions, NATS for all three substrate
  SPIs). Demonstrates the "right tool per SPI" pattern.
  Lower priority than the NATS-only example because the
  NATS-only is strictly simpler and more compelling.
- **Per-key TTL optimization**. NATS 2.11+ supports per-message
  TTL in JetStream. Once client support stabilizes, a spec can
  migrate `save` to use per-key TTL so that `update` can
  preserve the original expiration time and `touch` can
  explicitly reset it without re-putting the value. Not worth
  doing until the API stabilizes across jnats releases.
