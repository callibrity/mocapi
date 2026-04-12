# Migrate McpSessionStore to substrate Atom SPI (delete per-backend modules)

## Status

**BLOCKED for Ralph** — do not run in the auto-loop until the
restructured substrate (with `Atom` SPI) is published to Maven
Central. A SNAPSHOT build of substrate won't work in CI or for
other contributors pulling the repo, so Ralph skipping this spec
during the auto-loop is the right default.

**Executable by a human** once a local SNAPSHOT of substrate is
installed via `mvn install` in the substrate source tree
(`~/IdeaProjects/substrate`). That puts the new `substrate-core`
+ `substrate-<backend>` artifacts into `~/.m2/repository`, after
which this spec can be executed locally against them. Just keep
in mind that the work-in-progress branch can't be merged to main
until substrate is in Central — otherwise CI will fail to
resolve the SNAPSHOT.

Prerequisites actually needed for execution:

- `org.jwcarman.substrate:substrate-core` **AND** at least two
  `substrate-<backend>` modules with Atom support (minimum
  `substrate-redis` + `substrate-hazelcast`) — available locally
  via `mvn install` from the substrate repo, or in Central.
- `org.jwcarman.codec:codec-jackson` — **already in Central**, no
  blocker here.

Before starting execution, bump the `substrate.version` property
in the parent pom to whichever version contains the Atom SPI
(SNAPSHOT for local dev, release version before merging to
main), then verify with `mvn dependency:tree | grep substrate`
that the new modules resolve cleanly.

Before merging to main, verify:
1. `substrate.version` is a real released version, not a SNAPSHOT.
2. CI build passes (would fail if the version is unresolvable).
3. `mvn verify` on a fresh checkout (no `~/.m2` override) succeeds.

## What to build

Replace mocapi's entire per-backend `McpSessionStore` infrastructure
with a single ~30-line adapter that delegates to substrate's new
`AtomFactory` SPI. This eliminates seven session-store modules,
the standalone `InMemoryMcpSessionStore`, and all the per-backend
auto-config ordering/conditional gymnastics that shipped with
them. The backend a user runs on is controlled **entirely** by
which `substrate-<backend>` module is on the classpath — mocapi
stays backend-agnostic.

## Why

Substrate shipped a new `Atom<T>` SPI — a strongly-typed,
storage-backed atomic reference with native TTL, `touch`, and
`create`/`connect` semantics. Every substrate backend module
(`substrate-redis`, `substrate-nats`, `substrate-hazelcast`,
`substrate-mongodb`, `substrate-postgresql`, `substrate-cassandra`,
`substrate-dynamodb`) now ships a backend-specific `AtomSpi`
implementation via `<Backend>AtomAutoConfiguration`. Substrate's
`SubstrateAutoConfiguration` assembles an `AtomFactory` bean from
whichever `AtomSpi` is on the classpath — or falls back to
`InMemoryAtomSpi` if none is present, with a WARN log matching
mocapi's current in-memory fallback messaging.

The `Atom<T>` API is a near-perfect match for `McpSessionStore`:

| `McpSessionStore` op | `Atom<T>` / `AtomFactory` op |
|---|---|
| `save(session, ttl)` | `atomFactory.create(sessionId, McpSession.class, session, ttl)` |
| `find(sessionId)` | `atomFactory.connect(sessionId, McpSession.class).get().value()` (catch `AtomExpiredException` → `Optional.empty()`) |
| `update(sessionId, session)` | `atomFactory.connect(sessionId, McpSession.class).set(session, sessionTimeout)` |
| `touch(sessionId, ttl)` | `atomFactory.connect(sessionId, McpSession.class).touch(ttl)` |
| `delete(sessionId)` | `atomFactory.connect(sessionId, McpSession.class).delete()` |

The adapter is a direct method-for-method forwarder. No
serialization plumbing (substrate's `Codec<T>` from `codec-jackson`
handles JSON), no TTL index management (substrate sweepers handle
expiry), no DEL tombstone filtering (substrate SPI hides that),
no backend driver classpath checks (substrate auto-configs own
that).

### What this deletes

- **Seven session store modules**: `mocapi-session-store-redis`,
  `mocapi-session-store-hazelcast`, `mocapi-session-store-jdbc`,
  `mocapi-session-store-cassandra`, `mocapi-session-store-mongodb`,
  `mocapi-session-store-dynamodb`, `mocapi-session-store-nats`.
- **`InMemoryMcpSessionStore`** in `mocapi-core` (replaced by the
  Atom adapter running against substrate's in-memory `AtomSpi`).
- All the SB4 `afterName`/`after` ordering code, all the
  `@ConditionalOnBean(<backend-connection>.class)` guards, all
  the Testcontainers-driven per-backend integration tests. None
  of it needs to live in mocapi anymore — substrate owns it.

### What this preserves

- **`McpSessionStore` interface** stays — it's mocapi's stable
  contract for session lifecycle. Only the implementation shape
  changes.
- **`mocapi.session-timeout` property** — still drives session
  TTL. The adapter reads it from `MocapiProperties` and passes
  it on every `set`/`touch` call.
- **`mocapi.session-encryption-master-key`** — unrelated to
  storage, unaffected.
- **All four all-in-one starters** (`mocapi-redis-spring-boot-starter`,
  `mocapi-hazelcast-spring-boot-starter`,
  `mocapi-postgresql-spring-boot-starter`, future AWS starter) —
  still exist, but their dependency lists shrink significantly.

## Implementation

### Step 1: Bump substrate version + add codec-jackson

In the parent `pom.xml`:

```xml
<properties>
  <substrate.version>0.2.0</substrate.version>  <!-- or whatever ships Atom -->
  <codec.version>0.2.0</codec.version>          <!-- or whatever ships with substrate -->
</properties>

<dependencyManagement>
  <dependencies>
    <!-- existing substrate-bom import -->
    <dependency>
      <groupId>org.jwcarman.codec</groupId>
      <artifactId>codec-bom</artifactId>          <!-- if a codec BOM exists; otherwise manage codec-jackson directly -->
      <version>${codec.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### Step 2: Add substrate-core + codec-jackson to mocapi-core

In `mocapi-core/pom.xml`:

```xml
<dependency>
  <groupId>org.jwcarman.substrate</groupId>
  <artifactId>substrate-core</artifactId>
</dependency>
<dependency>
  <groupId>org.jwcarman.codec</groupId>
  <artifactId>codec-jackson</artifactId>
</dependency>
```

Both likely come in transitively already (via `odyssey-core` →
`substrate-core`), but declaring explicitly makes the dependency
intent visible and prevents accidental breakage if odyssey drops
the transitive edge.

### Step 3: Create `SubstrateAtomMcpSessionStore`

New file: `mocapi-core/src/main/java/com/callibrity/mocapi/session/SubstrateAtomMcpSessionStore.java`

```java
/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 * ... (standard header)
 */
package com.callibrity.mocapi.session;

import java.time.Duration;
import java.util.Optional;
import org.jwcarman.substrate.atom.AtomExpiredException;
import org.jwcarman.substrate.atom.AtomFactory;

/**
 * {@link McpSessionStore} implementation backed by substrate's {@link AtomFactory} SPI.
 *
 * <p>Each session is stored as a typed {@code Atom<McpSession>} keyed by session ID. The backing
 * storage is whatever {@code substrate-<backend>} module is on the classpath — substrate's
 * {@code SubstrateAutoConfiguration} assembles an {@code AtomFactory} bean from the selected
 * {@code AtomSpi} + {@code CodecFactory} + {@code NotifierSpi}, and this adapter delegates every
 * {@code McpSessionStore} operation to it.
 *
 * <p>If no backend {@code AtomSpi} is configured, substrate falls back to its in-memory
 * implementation and logs a WARN — mirroring the behavior of the previous {@code
 * InMemoryMcpSessionStore} class (which this replaces).
 *
 * <p><b>TTL semantics</b>: substrate's {@code Atom.set(value, ttl)} always assigns a fresh TTL
 * because the underlying SPI provides no "keep TTL" primitive. The adapter passes the configured
 * {@code mocapi.session-timeout} on every {@link #update(String, McpSession)} call — equivalent
 * to touching the session on every write, which matches "active sessions stay alive" intent and
 * is a slight behavior change from the previous Redis-backed {@code SET ... KEEPTTL} path.
 */
public class SubstrateAtomMcpSessionStore implements McpSessionStore {

  private final AtomFactory atomFactory;
  private final Duration sessionTimeout;

  public SubstrateAtomMcpSessionStore(AtomFactory atomFactory, Duration sessionTimeout) {
    this.atomFactory = atomFactory;
    this.sessionTimeout = sessionTimeout;
  }

  @Override
  public void save(McpSession session, Duration ttl) {
    atomFactory.create(session.sessionId(), McpSession.class, session, ttl);
  }

  @Override
  public Optional<McpSession> find(String sessionId) {
    try {
      return Optional.of(atomFactory.connect(sessionId, McpSession.class).get().value());
    } catch (AtomExpiredException e) {
      return Optional.empty();
    }
  }

  @Override
  public void update(String sessionId, McpSession session) {
    try {
      atomFactory.connect(sessionId, McpSession.class).set(session, sessionTimeout);
    } catch (AtomExpiredException e) {
      // Session expired between find and update — silently swallow, matching the previous
      // Redis SET XX / JDBC UPDATE WHERE id=? semantics. Callers that need to react to
      // missing sessions should use find() first.
    }
  }

  @Override
  public void touch(String sessionId, Duration ttl) {
    atomFactory.connect(sessionId, McpSession.class).touch(ttl);
  }

  @Override
  public void delete(String sessionId) {
    atomFactory.connect(sessionId, McpSession.class).delete();
  }
}
```

### Step 4: Replace `mcpSessionStore` bean in `MocapiAutoConfiguration`

In `MocapiAutoConfiguration.java`, replace:

```java
@Bean(destroyMethod = "shutdown")
@ConditionalOnMissingBean(McpSessionStore.class)
public InMemoryMcpSessionStore mcpSessionStore() {
  log.warn(
      "No McpSessionStore implementation found; using in-memory fallback (single-node only). "
          + "For clustered deployments, provide a McpSessionStore bean.");
  return new InMemoryMcpSessionStore();
}
```

With:

```java
@Bean
@ConditionalOnMissingBean(McpSessionStore.class)
public McpSessionStore mcpSessionStore(AtomFactory atomFactory) {
  return new SubstrateAtomMcpSessionStore(atomFactory, props.getSessionTimeout());
}
```

Notes:

- **No `@ConditionalOnBean(AtomFactory.class)`** guard is needed
  because substrate's `SubstrateAutoConfiguration` always
  provides one (either from a backend `AtomSpi` or from its
  in-memory fallback).
- **No `destroyMethod = "shutdown"`** needed — substrate's
  `Sweeper` beans handle expired-entry cleanup lifecycle, not
  mocapi.
- **The WARN log about in-memory fallback moves to substrate**
  — `SubstrateAutoConfiguration.atomSpi()` already logs a
  matching WARN when it creates the `InMemoryAtomSpi` fallback.
  No mocapi-side warning is needed.
- **Import** `org.jwcarman.substrate.atom.AtomFactory`.

### Step 5: Delete `InMemoryMcpSessionStore` and its test

- `mocapi-core/src/main/java/com/callibrity/mocapi/session/InMemoryMcpSessionStore.java` — **delete**.
- `mocapi-core/src/test/java/com/callibrity/mocapi/session/InMemoryMcpSessionStoreTest.java` — **delete**.
- Any test in `MocapiAutoConfigurationTest` that asserts the
  bean type is `InMemoryMcpSessionStore` — **update** to assert
  `SubstrateAtomMcpSessionStore` instead, or switch to a
  functional assertion ("the bean implements `McpSessionStore`
  and a round-trip save/find works").

### Step 6: Delete the seven per-backend session store modules

Delete the entire directory tree for each:

- `mocapi-session-store-redis/`
- `mocapi-session-store-hazelcast/`
- `mocapi-session-store-jdbc/`
- `mocapi-session-store-cassandra/`
- `mocapi-session-store-mongodb/`
- `mocapi-session-store-dynamodb/`
- `mocapi-session-store-nats/`

Remove each from the parent `pom.xml`'s `<modules>` list.

### Step 7: Update all-in-one starter deps

Each of the four all-in-one starters currently depends on both
(a) mocapi's per-backend session store module and (b) substrate's
three separate per-SPI modules (mailbox, journal, notifier). All
of those collapse to a single `substrate-<backend>` dep.

**`mocapi-redis-spring-boot-starter/pom.xml`**:

```xml
<!-- DELETE these four -->
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-session-store-redis</artifactId>
  <version>${project.version}</version>
</dependency>
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

<!-- REPLACE with just this -->
<dependency>
  <groupId>org.jwcarman.substrate</groupId>
  <artifactId>substrate-redis</artifactId>
</dependency>
```

Do the same for:

- `mocapi-hazelcast-spring-boot-starter/pom.xml` →
  `substrate-hazelcast`. Also remove the `spring-boot-hazelcast`
  dep we added for the `HazelcastAutoConfiguration` class
  reference — substrate-hazelcast handles that now.
- `mocapi-postgresql-spring-boot-starter/pom.xml` →
  `substrate-postgresql`.
- Future AWS starter → `substrate-dynamodb` + `substrate-sns`
  (sns is notifier-only; the AWS starter still needs two
  substrate modules since DynamoDB lacks a notifier).

Keep the existing `spring-boot-starter-data-redis` /
`hazelcast-spring` / `spring-boot-starter-jdbc` deps — those
provide the Spring Boot auto-configuration that produces the
backend-native beans substrate-<backend> consumes
(`RedisConnectionFactory`, `HazelcastInstance`, `DataSource`,
etc.).

### Step 8: Update `MocapiAutoConfiguration` ordering

Because `SubstrateAutoConfiguration` is the class that provides
the `AtomFactory` bean, mocapi's auto-config must order itself
after it:

```java
@AutoConfiguration(after = {
    ProjectInfoAutoConfiguration.class,
    SubstrateAutoConfiguration.class
})
```

The existing `after = ProjectInfoAutoConfiguration.class`
becomes a list with both entries. Import
`org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration`.

### Step 9: Update tests that reference deleted types

Grep for every test that imports `InMemoryMcpSessionStore` and
update each:

- `ApplicationContextRunner`-based tests should switch from
  asserting the concrete type to asserting `McpSessionStore`
  functionality (round-trip save/find works).
- Tests that needed the in-memory store for isolation can now
  rely on substrate's in-memory `AtomSpi` (which is wired
  automatically when no backend is on the classpath).

### Step 10: Update the example apps

No code changes needed. The example apps
(`examples/in-memory`, `examples/redis`, `examples/hazelcast`,
`examples/postgresql`) depend on the all-in-one starters, so
once the starter deps are updated, the examples inherit the
new behavior transparently.

However, **verify that each example app still boots** against
a real backend:

- `examples/in-memory` — no docker, should boot clean with
  substrate's in-memory fallback + the adapter.
- `examples/redis` — docker-compose brings Redis up, substrate-redis
  provides the `AtomSpi`, adapter writes to `mocapi:session:*`
  (or whatever key prefix substrate-redis uses).
- `examples/hazelcast` — embedded Hazelcast + substrate-hazelcast.
- `examples/postgresql` — docker-compose Postgres + substrate-postgresql.

Run each and hit `tools/list` + a streaming tool call to verify
sessions are created and persisted end-to-end.

## Acceptance criteria

### Core changes

- [ ] `SubstrateAtomMcpSessionStore` exists in
      `mocapi-core/src/main/java/com/callibrity/mocapi/session/`
      and implements all five `McpSessionStore` methods exactly
      as shown in Step 3.
- [ ] `MocapiAutoConfiguration.mcpSessionStore()` returns an
      `SubstrateAtomMcpSessionStore` wired to the injected
      `AtomFactory` and `MocapiProperties.sessionTimeout`.
- [ ] `MocapiAutoConfiguration` is ordered
      `@AutoConfiguration(after = {ProjectInfoAutoConfiguration.class, SubstrateAutoConfiguration.class})`.
- [ ] `InMemoryMcpSessionStore` class and test are deleted.
- [ ] `mocapi-core/pom.xml` declares explicit deps on
      `substrate-core` and `codec-jackson` (even if transitively
      present via `odyssey-core`).

### Module deletion

- [ ] Seven `mocapi-session-store-*` modules are deleted from
      the filesystem.
- [ ] Parent `pom.xml`'s `<modules>` list no longer references
      any of them.
- [ ] `git grep "mocapi-session-store"` in the repo returns
      zero results (outside of `specs/done/` — historical specs
      stay intact).

### Starter updates

- [ ] `mocapi-redis-spring-boot-starter`: depends on
      `substrate-redis` only (no per-SPI substrate modules, no
      `mocapi-session-store-redis`).
- [ ] `mocapi-hazelcast-spring-boot-starter`: depends on
      `substrate-hazelcast` only. The `spring-boot-hazelcast`
      dep added for SB4 `HazelcastAutoConfiguration` class
      reference can be removed — substrate-hazelcast handles
      that transitively.
- [ ] `mocapi-postgresql-spring-boot-starter`: depends on
      `substrate-postgresql` only.
- [ ] A future AWS starter spec will need updating to reference
      `substrate-dynamodb` + `substrate-sns` — document this as
      a follow-up.

### Behavior verification

- [ ] `mvn verify` on the full reactor is green.
- [ ] `examples/in-memory` app boots clean, `tools/list`
      returns tools, a tool call creates a session.
- [ ] `examples/redis` app boots with Redis running, and
      `KEYS *` in redis-cli shows substrate-owned session keys
      (whatever prefix `substrate-redis` uses — likely
      `substrate:atom:*`). This replaces the `mocapi:session:*`
      prefix the previous module used; document this as a
      migration note.
- [ ] `examples/hazelcast` app boots, sessions are stored in
      the substrate-hazelcast IMap (name TBD by substrate).
- [ ] `examples/postgresql` app boots, sessions are stored in
      substrate's postgres table (name/schema TBD by substrate).

### Test updates

- [ ] No test in the repo imports `InMemoryMcpSessionStore` (it
      no longer exists).
- [ ] `MocapiAutoConfigurationTest` (or equivalent) verifies:
  - When no backend `AtomSpi` is on the classpath, the
    `McpSessionStore` bean is a `SubstrateAtomMcpSessionStore`
    backed by substrate's in-memory `AtomSpi` (functional
    assertion — save a session, find it, verify round trip).
  - `mocapi.session-timeout` is honored — write a session
    with a short timeout, verify it expires.
- [ ] Every surviving IT test in mocapi-compat still passes.

### Docs

- [ ] README in the repo root updated: the "Backend options"
      section no longer mentions per-backend mocapi session
      store modules. Instead, it explains that users pick a
      backend by adding the matching `mocapi-<backend>-spring-boot-starter`
      (which transitively brings the corresponding
      `substrate-<backend>`).
- [ ] Each example's README updated to reflect the new key
      namespace (e.g., redis example shows `substrate:atom:*`
      instead of `mocapi:session:*`).

## Implementation notes

- **Commit granularity**. This is a meaty all-or-nothing change
  — the Atom adapter can't land half-way. Suggested commit
  sequence:
  1. Bump substrate version + add `codec-jackson` to parent
     BOM. Verify `mvn dependency:tree`.
  2. Add `SubstrateAtomMcpSessionStore` + update
     `MocapiAutoConfiguration` + delete `InMemoryMcpSessionStore`.
     Update `MocapiAutoConfigurationTest`.
  3. Update the four all-in-one starter poms (one commit per
     starter for reviewability).
  4. Delete the seven `mocapi-session-store-*` modules + parent
     pom `<modules>` list (one commit for all seven — they're
     all dead code at this point).
  5. Verify each example app boots and persists sessions to the
     expected backend. Fix any issues. Final `mvn verify`
     green across the reactor.

- **Don't try to preserve the old key prefixes** (e.g.,
  `mocapi:session:*` in Redis). Substrate owns the key space
  now via its own prefix conventions
  (`substrate:atom:<bucket>:<key>` or similar — check the
  specific backend's SPI impl). Document this as a **breaking
  change for operators** running upgraded mocapi against an
  existing Redis database: old sessions in the `mocapi:session:*`
  namespace will be orphaned, and clients will need to
  re-initialize. This is acceptable because:
  - Sessions are ephemeral by design (1-hour TTL default).
  - The upgrade window is short (a few minutes of downtime).
  - The alternative (preserving the old prefix) would require a
    migration script or dual-read logic, which is far more
    complex than the benefit justifies.

- **The TTL-on-update behavior change**. The old Redis
  implementation used `SET ... KEEPTTL` — a session created at
  T=0 with TTL=1h stayed expiring at T=1h even if updated at
  T=30min. The new Atom adapter passes `sessionTimeout` on
  every `set`, meaning every update resets the TTL to 1h from
  "now." This is arguably the correct behavior (active sessions
  stay alive), but it's a behavior change. Document it in the
  changelog.

- **`AtomAlreadyExistsException`**. `AtomFactory.create()`
  throws if an atom with the same key already exists. mocapi's
  `McpSessionStore.save()` contract assumes new session IDs
  (generated via `UUID.randomUUID()` in
  `McpSessionService.create()`), so this shouldn't fire in
  practice. However, if it does (e.g., UUID collision — or a
  test that reuses session IDs), the exception will propagate
  to the caller. If this turns out to cause test breakage,
  catch and convert to an `IllegalStateException` with a clear
  message.

- **Substrate sweepers are enabled by default**.
  `SubstrateAutoConfiguration` creates `Sweeper` beans for
  `AtomSpi`, `JournalSpi`, and `MailboxSpi` under
  `substrate.<spi>.sweep.enabled=true` (default true). These
  replace mocapi's old `InMemoryMcpSessionStore` cleanup
  executor. No additional configuration needed.

- **Codec is Jackson-only in this spec**. substrate supports
  multiple codec backends (`codec-jackson`, `codec-gson`,
  `codec-protobuf`). Mocapi picks Jackson because the rest of
  mocapi-core uses `tools.jackson.databind.ObjectMapper` for
  everything else. If a future use case needs a different
  codec, it can be swapped via `@ConditionalOnMissingBean(CodecFactory.class)`
  — the user provides their own bean and mocapi's adapter
  picks it up automatically (substrate's `DefaultAtomFactory`
  doesn't care about the concrete codec type).

- **Don't delete `McpSessionStore` the interface**. It's still
  mocapi's public contract for session operations. Only the
  implementation layer consolidates. Tool authors and
  integration tests that inject `McpSessionStore` are
  unaffected.

## Follow-up work (not in scope)

- **Delete the `examples/in-memory/` module?** With substrate's
  in-memory fallbacks covering all four SPIs, the
  "zero-infrastructure" demo is now just "add mocapi's starter,
  done." The in-memory example might become redundant. Worth a
  separate discussion spec — could be retired, could be kept as
  the "simplest possible demo."
- **NATS example app**. Now that substrate-nats provides
  Atom + Journal + Mailbox + Notifier, the NATS-only example
  idea from the Atom planning discussion becomes trivial: one
  docker-compose service, the `substrate-nats` module on the
  classpath, and you have a fully distributed single-backplane
  mocapi. Create `examples/nats/` as a new spec, possibly
  bundled with a future `mocapi-nats-spring-boot-starter`.
- **Cassandra example app**. With substrate-cassandra now
  providing Atom + Journal (but no Mailbox/Notifier), a
  Cassandra example can be written — falling back to in-memory
  for the missing SPIs, OR combining with NATS for the
  mixed-topology story. Separate spec.
- **Remove `mocapi.session-timeout` from the parent starter**.
  If substrate's `Atom.maxTtl` becomes the canonical limit,
  mocapi's separate `session-timeout` property may be
  redundant — or at least needs to be validated against
  `substrate.atom.max-ttl` at startup. Worth investigating
  once spec 147 lands.
