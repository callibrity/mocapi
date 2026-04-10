# Hazelcast-backed McpSessionStore module (mocapi-session-store-hazelcast)

## What to build

Create a new Maven module `mocapi-session-store-hazelcast` that
provides a Hazelcast-backed implementation of `McpSessionStore`.
Same drop-in behavior as `mocapi-session-store-redis`: adding the
jar to a Spring Boot application's classpath automatically replaces
the in-memory fallback with a Hazelcast `IMap`-backed store,
without requiring explicit bean configuration from the application
author.

### Why Hazelcast

Hazelcast is a common choice for Spring Boot applications that need
clustered in-memory state without standing up a separate Redis
server. It embeds into the JVM process and clusters peer-to-peer,
which some operators prefer for operational simplicity. It's also
the second most common distributed cache option in Spring Boot's
auto-configuration ecosystem (after Redis).

### Module structure

```
mocapi-session-store-hazelcast/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/callibrity/mocapi/session/hazelcast/
    │   │   ├── HazelcastMcpSessionStore.java
    │   │   └── HazelcastMcpSessionStoreAutoConfiguration.java
    │   └── resources/
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/callibrity/mocapi/session/hazelcast/
            ├── HazelcastMcpSessionStoreTest.java
            └── HazelcastMcpSessionStoreAutoConfigurationTest.java
```

Add the module to the parent `pom.xml`'s `<modules>` list.

### `HazelcastMcpSessionStore` implementation

Uses a Hazelcast `IMap<String, McpSession>` keyed by session ID.

- **Map name**: `mocapi-sessions` (configurable via
  `mocapi.session.hazelcast.map-name`)
- **Value**: `McpSession` record, serialized by Hazelcast's default
  (Java serialization) or by registering a custom
  `StreamSerializer` / `Compact` serializer keyed to the record's
  fields. The spec recommends **Hazelcast Compact serialization**
  for forward compatibility — it handles record classes natively
  without requiring `Serializable` and survives schema evolution
  better than Java serialization.
- **TTL**: Hazelcast `IMap` supports per-entry TTL via
  `map.put(key, value, ttl, TimeUnit.SECONDS)` or
  `map.set(key, value, ttl, TimeUnit.SECONDS)`.
- **Update with preserved TTL**: Hazelcast doesn't have a first-class
  "preserve TTL" update op. Implement as a read + update-value +
  explicit-TTL rewrite, computing the remaining TTL from the
  existing entry's metadata (`EntryView.getTtl()` + `getLastAccessTime()`,
  or by storing the original TTL in the value itself and recomputing).
  The simplest approach: store `expiresAt` as a field in a wrapper
  and read it back on update. Pick whichever is cleaner — the spec
  doesn't mandate the mechanism, only the observable behavior.

### Auto-configuration

`HazelcastMcpSessionStoreAutoConfiguration` activates when:
- A `HazelcastInstance` bean is present (Spring Boot's
  `HazelcastAutoConfiguration` has already done its work)
- No user-provided `McpSessionStore` bean is present

It registers a `HazelcastMcpSessionStore` bean wired to the
`HazelcastInstance` that Spring Boot auto-configured. Same ordering
as Redis: `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
so mocapi's in-memory fallback is skipped.

### Dependencies

```xml
<dependencies>
  <dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-core</artifactId>
    <version>${project.version}</version>
  </dependency>

  <!-- Spring Boot Hazelcast starter -->
  <dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast-spring</artifactId>
    <optional>true</optional>
  </dependency>

  <!-- Alternatively, if Spring Boot 4 bundles Hazelcast support
       into spring-boot-starter-cache or similar, reference that -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <!-- Test: embedded Hazelcast for integration tests -->
  <dependency>
    <groupId>com.hazelcast</groupId>
    <artifactId>hazelcast</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Hazelcast's embedded mode is perfect for tests — no containers
needed, just instantiate `Hazelcast.newHazelcastInstance(new Config())`
in a `@BeforeEach`.

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-hazelcast` exists with a
      correct parent reference.
- [ ] Module is listed in the parent pom's `<modules>` list.
- [ ] Package is `com.callibrity.mocapi.session.hazelcast`.
- [ ] License headers, spotless formatting, and pom conventions
      match the rest of the mocapi project.

### Implementation

- [ ] `HazelcastMcpSessionStore` implements all six `McpSessionStore`
      methods using `IMap` operations.
- [ ] `save(session, ttl)` uses `map.set(sessionId, session, ttl.toSeconds(), TimeUnit.SECONDS)`.
- [ ] `find(sessionId)` returns `Optional.ofNullable(map.get(sessionId))`.
      Hazelcast handles TTL expiration automatically — expired
      entries are returned as null.
- [ ] `touch(sessionId, ttl)` uses `map.setTtl(sessionId, ttl.toSeconds(), TimeUnit.SECONDS)`.
- [ ] `delete(sessionId)` uses `map.delete(sessionId)` (fire-and-forget;
      does not fail on missing keys).
- [ ] `update(sessionId, session)` preserves the existing TTL. Use
      whatever mechanism works cleanly — either read the `EntryView`
      to get remaining TTL and re-set, or wrap the value in a holder
      that carries `expiresAt` explicitly and recompute from there.
- [ ] Map name is configurable via
      `mocapi.session.hazelcast.map-name` property, default
      `mocapi-sessions`.

### Serialization

- [ ] `McpSession` is serialized via Hazelcast Compact serialization
      (preferred) or Java serialization (acceptable fallback).
- [ ] Round-tripping a session through the map preserves all
      fields: `protocolVersion`, `capabilities`, `clientInfo`,
      `logLevel`, `sessionId`. A round-trip test asserts equality.
- [ ] `ClientCapabilities` and `Implementation` (nested records)
      also round-trip correctly.

### Auto-configuration

- [ ] `HazelcastMcpSessionStoreAutoConfiguration` is registered in
      `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- [ ] `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
- [ ] `@ConditionalOnBean(HazelcastInstance.class)`
- [ ] `@ConditionalOnMissingBean(McpSessionStore.class)`
- [ ] The `@Bean` method returns `McpSessionStore` interface.
- [ ] Application with mocapi + this module + Hazelcast on the
      classpath and a `HazelcastInstance` configured auto-wires
      `HazelcastMcpSessionStore` without any explicit bean
      declaration.

### Tests

- [ ] `HazelcastMcpSessionStoreTest` uses an embedded Hazelcast
      instance (`Hazelcast.newHazelcastInstance(new Config())`) and
      exercises all six methods. At the end of each test,
      `hazelcastInstance.shutdown()` is called to release resources.
- [ ] A dedicated TTL test: save with 1s TTL, verify the entry
      expires (poll until `find` returns empty or use Hazelcast's
      scheduled expiration).
- [ ] A dedicated `update` test: save with 10s TTL, update, assert
      the remaining TTL is close to (not greater than) 10s.
- [ ] `HazelcastMcpSessionStoreAutoConfigurationTest` uses
      `ApplicationContextRunner` with a mock or embedded
      `HazelcastInstance` bean and verifies:
  - Store is registered when Hazelcast is present
  - Store is NOT registered when Hazelcast is absent
  - User-provided `McpSessionStore` wins over the auto-configured
    one
- [ ] `mvn verify` on the new module is green.
- [ ] `mvn verify` on the full reactor is green.

## Implementation notes

- **Hazelcast Compact serialization**: this is the modern (≥ 5.0)
  way to serialize Java types in Hazelcast. It's schema-based,
  forward-compatible, and handles records natively via a
  `CompactSerializer<McpSession>` that you register on the Hazelcast
  config. See Hazelcast docs for the exact API. If Compact is too
  heavy to set up, Java serialization works — just make `McpSession`
  and its nested types implement `Serializable`, which is
  backward-compatible with existing consumers.
- **`setTtl` availability**: Hazelcast `IMap.setTtl(key, ttl, unit)`
  has been available since 4.0. The spec assumes Hazelcast ≥ 4.0.
- **Embedded mode for tests**: `Hazelcast.newHazelcastInstance()`
  with an explicit `Config` that disables network discovery (set
  join to `false` for all discovery types) — this keeps test runs
  from accidentally clustering across multiple JVMs on the same
  machine.
- **Java serialization caveat**: if using Java serialization as a
  fallback, `McpSession`, `ClientCapabilities`, `Implementation`,
  `LoggingLevel`, and any nested records/enums must all be
  `Serializable`. They aren't today — adding `Serializable` to the
  model types is a small change with no runtime cost, but it
  implicitly binds the module to a specific schema version. Compact
  serialization is the better long-term choice.
- **Update operation complexity**: the `update` method preserving
  TTL is the hardest part of this implementation. Acceptable
  approaches:
  1. `IMap.executeOnKey(key, entryProcessor)` with a custom
     `EntryProcessor` that reads the existing entry, updates the
     value, and keeps the existing expiration. This is the cleanest
     but requires an `EntryProcessor` implementation.
  2. Wrap the stored value in a `StoredSession(McpSession session,
     long expiresAtMillis)` container that carries its own
     expiration, then on update read + set with the remaining TTL.
     More allocation but simpler code.
  3. `EntryView<String, McpSession> view = map.getEntryView(key);
     long remainingTtl = view.getExpirationTime() - System.currentTimeMillis();
     map.set(key, session, remainingTtl, MILLISECONDS);` —
     vulnerable to clock skew across cluster members but simple.
  Pick whichever is cleanest for your taste. The spec doesn't
  mandate the approach.
- **Don't** introduce a custom `HazelcastInstance` bean in the
  auto-config. Reuse whatever Spring Boot or the application has
  already configured.
- **Commit granularity**: same as the Redis module — one or two
  commits depending on what feels bisectable.
- **Documentation**: javadoc on the store should explain the map
  name, TTL semantics, and how `update` handles TTL preservation.
