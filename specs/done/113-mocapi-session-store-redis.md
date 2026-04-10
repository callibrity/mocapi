# Redis-backed McpSessionStore module (mocapi-session-store-redis)

## What to build

Create a new Maven module `mocapi-session-store-redis` that provides
a Redis-backed implementation of `McpSessionStore`. The module must
be **drop-in**: adding it to a Spring Boot application's classpath
automatically replaces the default `InMemoryMcpSessionStore`
fallback with the Redis implementation, with zero required
configuration beyond what Spring Boot's `spring-boot-starter-data-redis`
already needs (connection URL, auth, etc.).

### Module structure

```
mocapi-session-store-redis/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/callibrity/mocapi/session/redis/
    │   │   ├── RedisMcpSessionStore.java
    │   │   └── RedisMcpSessionStoreAutoConfiguration.java
    │   └── resources/
    │       └── META-INF/spring/
    │           └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/callibrity/mocapi/session/redis/
            ├── RedisMcpSessionStoreTest.java         (Testcontainers integration)
            └── RedisMcpSessionStoreAutoConfigurationTest.java
```

Add the new module to the parent `pom.xml`'s `<modules>` list.

### `RedisMcpSessionStore` implementation

Uses Spring Boot's auto-configured `RedisTemplate` (or `StringRedisTemplate`
with manual JSON serialization — pick one and be consistent). Each
session is stored as a key-value pair:

- **Key**: `mocapi:session:<sessionId>` (configurable prefix)
- **Value**: JSON-serialized `McpSession` (via the application's
  `ObjectMapper`, or a Redis-level Jackson serializer — both work,
  but the application mapper keeps consistency with the rest of the
  framework)
- **TTL**: set via Redis's native `EXPIRE` / `SET ... EX <seconds>`

The `save(session, ttl)` method does a single `SET` with the TTL in
seconds. The `touch(sessionId, ttl)` method does an `EXPIRE` on the
existing key (no data fetch). The `find(sessionId)` method does a
`GET` and deserializes. The `delete(sessionId)` method does a `DEL`.
The `update(sessionId, session)` method should preserve the
existing TTL — Redis exposes this via `SET ... KEEPTTL` (Redis 6.0+)
or equivalent.

All six methods of the `McpSessionStore` interface must be implemented
using Redis primitives with no roundtrip to a secondary cache or
database. No in-memory coalescing — this is a thin adapter.

### Auto-configuration

`RedisMcpSessionStoreAutoConfiguration` activates when:
- A `RedisConnectionFactory` bean is present (Spring Boot's
  `RedisAutoConfiguration` has already done its work)
- No user-provided `McpSessionStore` bean is present (lower-priority
  fallback than user config, higher-priority than the in-memory
  default)

It registers a `RedisMcpSessionStore` bean wired to the
auto-configured Redis template. Spring Boot's auto-configuration
import file
(`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`)
lists the auto-configuration class so it's picked up automatically
when the jar is on the classpath.

Order relative to `MocapiAutoConfiguration`: `RedisMcpSessionStoreAutoConfiguration`
must run **before** `MocapiAutoConfiguration` so that when mocapi's
auto-config checks for a `McpSessionStore` bean, the Redis one is
already registered. Use `@AutoConfigureBefore(MocapiAutoConfiguration.class)`.

Also: only activate when a Redis connection factory exists. Use
`@ConditionalOnBean(RedisConnectionFactory.class)`. If the user has
mocapi + this module but no Redis on the classpath, the store is
NOT activated and mocapi falls back to in-memory. This is the
"drop-in" requirement — no explicit configuration, just works when
all the pieces are present.

### Dependencies

In `pom.xml`:

```xml
<dependencies>
  <!-- mocapi core: the interface and auto-config hook -->
  <dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-core</artifactId>
    <version>${project.version}</version>
  </dependency>

  <!-- Spring Boot Redis starter: provides RedisConnectionFactory etc. -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <optional>true</optional>   <!-- consumers pick Lettuce vs Jedis themselves -->
  </dependency>

  <!-- Spring Boot auto-config support -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <!-- Test: Testcontainers for a real Redis integration test -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>com.redis</groupId>
    <artifactId>testcontainers-redis</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

Note the Redis starter is `optional` — consumers of
`mocapi-session-store-redis` who are already using
`spring-boot-starter-data-redis` (for their own Redis work) can pull
this module in and everything composes. Those who want the Redis
starter transitively can declare it explicitly.

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-redis` exists with a correct
      parent reference in its pom.
- [ ] Module is listed in the parent `mocapi-parent` pom's
      `<modules>` list.
- [ ] Module follows the standard mocapi pom conventions (license
      headers, Apache 2.0, copyright year, etc.).
- [ ] Package is `com.callibrity.mocapi.session.redis`.

### Implementation

- [ ] `RedisMcpSessionStore` implements all six methods of
      `McpSessionStore` using Redis primitives (`SET`, `GET`, `EXPIRE`,
      `DEL`).
- [ ] `save(session, ttl)` sets both the value and the TTL in a
      single Redis command (`SET ... EX <seconds>`).
- [ ] `update(sessionId, session)` preserves the existing TTL
      (`SET ... KEEPTTL`).
- [ ] `touch(sessionId, ttl)` updates only the TTL without fetching
      or rewriting the value (`EXPIRE`).
- [ ] `find(sessionId)` returns `Optional.empty()` for keys that
      don't exist or have expired (Redis returns nil for both cases).
- [ ] `delete(sessionId)` issues `DEL` and does not fail if the key
      is already gone.
- [ ] The key prefix is configurable via a property
      (`mocapi.session.redis.key-prefix`, default `mocapi:session:`).
- [ ] JSON serialization uses the application's `ObjectMapper`
      (injected) so the same Jackson configuration and modules are
      in effect as the rest of the framework.

### Auto-configuration

- [ ] `RedisMcpSessionStoreAutoConfiguration` is registered in
      `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- [ ] The auto-config is annotated
      `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
      so the Redis store beats mocapi's in-memory fallback.
- [ ] The auto-config is annotated
      `@ConditionalOnBean(RedisConnectionFactory.class)` so it only
      activates when Spring Boot's Redis auto-config has created the
      connection factory.
- [ ] The auto-config is annotated
      `@ConditionalOnMissingBean(McpSessionStore.class)` so user-provided
      beans take precedence.
- [ ] The auto-config's `@Bean` method returns a `McpSessionStore`
      (not a concrete `RedisMcpSessionStore`) so injection points
      typed as the interface resolve correctly.
- [ ] An application that adds `mocapi-session-store-redis` +
      `spring-boot-starter-data-redis` to its classpath and starts
      up with `spring.data.redis.host=localhost` receives a
      `RedisMcpSessionStore` bean and NOT the `InMemoryMcpSessionStore`.

### Tests

- [ ] `RedisMcpSessionStoreTest` uses Testcontainers to spin up a
      real Redis instance and exercises all six methods:
  - save then find → returns the same session
  - save with 1s TTL, sleep 2s, find → returns empty
  - touch extends the TTL (save with 1s TTL, touch to 10s, sleep
    2s, find → still returns the session)
  - update preserves TTL (save with 10s TTL, update, check
    remaining TTL via Redis client → close to 10s, not reset)
  - delete removes the key
  - find on non-existent key → empty
- [ ] `RedisMcpSessionStoreAutoConfigurationTest` uses Spring Boot's
      `ApplicationContextRunner` to verify:
  - With Redis classpath + `RedisConnectionFactory` present + no
    user `McpSessionStore` → `RedisMcpSessionStore` is registered
  - With a user-provided `McpSessionStore` bean → the user bean
    wins, Redis store is NOT registered
  - With no `RedisConnectionFactory` → Redis store is not
    registered, mocapi falls back to in-memory
- [ ] `mvn verify` on the new module is green.
- [ ] `mvn verify` on the full reactor is green (adding the new
      module doesn't break anything else).

## Implementation notes

- **Serialization choice**: use the application's `ObjectMapper`
  (the one Spring Boot auto-configures from Jackson3AutoConfiguration),
  injected into the store's constructor. Do NOT use Redis's
  `GenericJackson2JsonRedisSerializer` — that ships its own mapper
  and will drift from the application's Jackson 3 configuration
  (different modules, different include rules).
- **`KEEPTTL` support**: requires Redis ≥ 6.0. If you need to
  support older Redis, fall back to a `PTTL` + `SET ... PX <ms>`
  dance. The spec assumes Redis 6.0+ since it's been out for years.
- **Testcontainers**: use `com.redis.testcontainers.RedisContainer`
  (from `com.redis:testcontainers-redis`) or
  `org.testcontainers.containers.GenericContainer` with a
  `redis:7-alpine` image. Whichever is cleaner — the
  `testcontainers-redis` module is purpose-built but adds a
  dependency.
- **Don't** put any Redis-specific types in the constructor of
  `RedisMcpSessionStore` that would leak into the interface. The
  store should be a plain `McpSessionStore` implementation; Redis
  is an implementation detail.
- **Key prefix**: having a configurable prefix lets multiple
  applications share a Redis instance without colliding. Default
  to `mocapi:session:` which is unambiguous.
- **Don't** introduce a custom `RedisTemplate` bean in the
  auto-config. Reuse whatever the Spring Boot Redis auto-config
  already provides (`StringRedisTemplate` for string keys + JSON
  values is the cleanest combo).
- **Commit granularity**: one commit for the module scaffolding
  (pom + empty package structure + auto-config imports file), one
  commit for `RedisMcpSessionStore` + unit test, one commit for
  the auto-configuration + its test. Or bundle into a single commit
  if that's cleaner.
- **Documentation**: javadoc on `RedisMcpSessionStore` should
  explain the key format, TTL semantics, and that `update` preserves
  TTL. The auto-config class should have a one-line comment
  explaining the conditional activation.
