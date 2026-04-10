# MongoDB-backed McpSessionStore module (mocapi-session-store-mongodb)

## What to build

Create a new Maven module `mocapi-session-store-mongodb` that
provides a MongoDB-backed implementation of `McpSessionStore`.
Same drop-in pattern as specs 113 (Redis), 114 (Hazelcast), 115
(JDBC), and 130 (Cassandra): adding the jar to a Spring Boot
application's classpath automatically replaces the in-memory
fallback with the MongoDB implementation whenever a
`MongoTemplate` bean is present.

### Why MongoDB

1. **TTL indexes** — MongoDB's TTL index feature
   (`{ expireAfterSeconds: 0 }` on a `Date` field) automatically
   removes documents whose `expires_at` field is older than
   the current time. The background TTL monitor runs every ~60
   seconds, which is fine for session data (slightly less
   precise than Cassandra's native TTL but effectively
   hands-free).
2. **Document model** — `McpSession` maps naturally to a BSON
   document. The MongoDB Java driver's codec handles Jackson
   annotations via a custom codec, or we can serialize to JSON
   string and store as a field (simpler, same pattern as the
   other modules).
3. **Spring Data MongoDB** — well-established auto-configuration
   via `MongoTemplate`. Operators who already use MongoDB for
   application data get a seamless experience.
4. **Replica sets** — built-in HA without extra operational
   complexity.
5. **Prevalence** — MongoDB is one of the most common document
   databases in Spring Boot apps. Many teams will pick this
   backend specifically because they already run it.

### Session-store-only scope

Like JDBC and Cassandra, MongoDB isn't a message broker.
Substrate Mailbox and Notifier aren't covered here — operators
who want a fully distributed mocapi deployment with MongoDB
will get session persistence from this module and fall back to
in-memory for the other substrate SPIs.

### Module structure

```
mocapi-session-store-mongodb/
├── pom.xml
└── src/
    ├── main/
    │   └── java/com/callibrity/mocapi/session/mongodb/
    │       ├── MongoMcpSessionStore.java
    │       └── MongoMcpSessionStoreAutoConfiguration.java
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/callibrity/mocapi/session/mongodb/
            ├── MongoMcpSessionStoreTest.java    (Testcontainers)
            └── MongoMcpSessionStoreAutoConfigurationTest.java
```

### Document shape

One collection (`mocapi_sessions` by default), one document per
session:

```json
{
  "_id": "<sessionId>",
  "payload": "<JSON-serialized McpSession>",
  "expiresAt": ISODate("2026-04-10T09:30:00Z")
}
```

The `_id` field is the session ID (string primary key).
`payload` holds the serialized `McpSession` as a JSON string
(consistent with the other module implementations for
portability of the JSON representation). `expiresAt` is a
BSON `Date` — the field that the TTL index watches.

### TTL index

Created at application startup on first use:

```java
indexOps.ensureIndex(
    new Index()
        .on("expiresAt", Sort.Direction.ASC)
        .expire(0, TimeUnit.SECONDS));
```

The `.expire(0, SECONDS)` means "delete the document as soon as
`expiresAt` is in the past" — the actual TTL value per session
is encoded in the document's `expiresAt` field, not in the
index configuration.

### `MongoMcpSessionStore` implementation

Constructor dependencies:
- `MongoTemplate` (auto-wired from Spring Boot)
- `ObjectMapper` (for JSON serialization of `McpSession`)
- Configuration properties (collection name)

Initialization (called from `@PostConstruct` or the constructor):

```java
mongoTemplate.indexOps(collectionName).ensureIndex(
    new Index()
        .on("expiresAt", Sort.Direction.ASC)
        .expire(0, TimeUnit.SECONDS));
```

### `save(session, ttl)`

```java
var doc = new Document()
    .append("_id", session.sessionId())
    .append("payload", objectMapper.writeValueAsString(session))
    .append("expiresAt", Date.from(Instant.now().plus(ttl)));
mongoTemplate.getCollection(collectionName).replaceOne(
    new Document("_id", session.sessionId()),
    doc,
    new ReplaceOptions().upsert(true));
```

`replaceOne` with `upsert(true)` handles both insert-new and
update-existing cases atomically.

### `update(sessionId, session)` — preserving TTL

Unlike Cassandra, MongoDB's update path can reference the
existing `expiresAt` via an aggregation pipeline update, or we
can do a read+write:

**Simple approach (read+write)**:
1. `findOne({_id: sessionId})` to fetch the current document
2. `updateOne({_id: sessionId}, {$set: {payload: newPayload}})`
   — updates only the `payload` field, leaving `expiresAt`
   untouched.

The `$set` on only the `payload` field preserves `expiresAt`
naturally — MongoDB doesn't reset untouched fields on update.
**Single round-trip** — no need for the read step because the
update only needs to know which document to modify and the
new payload value, both of which are parameters.

```java
mongoTemplate.getCollection(collectionName).updateOne(
    new Document("_id", sessionId),
    new Document("$set", new Document("payload", newPayload)));
```

### `find(sessionId)`

```java
var doc = mongoTemplate.getCollection(collectionName).find(
    new Document("_id", sessionId)).first();
if (doc == null) return Optional.empty();
// Optional: also check doc.getDate("expiresAt") > now to handle
// the window between expiration and TTL-monitor cleanup
return Optional.of(objectMapper.readValue(doc.getString("payload"), McpSession.class));
```

**Note on the expiration window**: MongoDB's TTL monitor runs
every ~60 seconds. Between the moment a session expires and the
moment the monitor removes it, `find` could return an "expired"
session. For session integrity, add an explicit `expiresAt > now`
filter in the query:

```java
new Document("_id", sessionId)
    .append("expiresAt", new Document("$gt", new Date()));
```

This filters out expired-but-not-yet-deleted documents at read
time. The TTL monitor still eventually removes them — the
filter just handles the narrow window.

### `touch(sessionId, ttl)`

Update only the `expiresAt` field:

```java
mongoTemplate.getCollection(collectionName).updateOne(
    new Document("_id", sessionId),
    new Document("$set", new Document("expiresAt",
        Date.from(Instant.now().plus(ttl)))));
```

Single round-trip, no read needed.

### `delete(sessionId)`

```java
mongoTemplate.getCollection(collectionName).deleteOne(
    new Document("_id", sessionId));
```

Idempotent.

### Auto-configuration

Activates when:
- `MongoTemplate` bean is present (Spring Boot's
  `MongoAutoConfiguration` has run)
- No user-provided `McpSessionStore` bean

`@AutoConfiguration(before = MocapiAutoConfiguration.class)` +
`@ConditionalOnBean(MongoTemplate.class)` +
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
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
    <optional>true</optional>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <!-- Testcontainers -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mongodb</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-mongodb` exists with
      correct parent reference.
- [ ] Listed in parent pom's `<modules>`.
- [ ] Package is `com.callibrity.mocapi.session.mongodb`.

### Implementation

- [ ] `MongoMcpSessionStore` implements all six `McpSessionStore`
      methods using `MongoTemplate` / `MongoCollection` APIs.
- [ ] A TTL index on `expiresAt` is created at store
      initialization (`@PostConstruct` or the constructor,
      idempotent via `ensureIndex`).
- [ ] `save(session, ttl)` does an upsert via `replaceOne(..., upsert=true)`.
- [ ] `update(sessionId, session)` uses `$set` on only the
      `payload` field, preserving `expiresAt` naturally.
- [ ] `find(sessionId)` filters by `expiresAt > now` to handle
      the expiration-window gap.
- [ ] `touch(sessionId, ttl)` uses `$set` on only the
      `expiresAt` field.
- [ ] `delete(sessionId)` uses `deleteOne`, idempotent.
- [ ] Collection name configurable via
      `mocapi.session.mongodb.collection` (default
      `mocapi_sessions`).
- [ ] JSON serialization of the `payload` field uses the
      application's `ObjectMapper`.

### Auto-configuration

- [ ] `MongoMcpSessionStoreAutoConfiguration` is in the
      auto-config imports file.
- [ ] `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
- [ ] `@ConditionalOnBean(MongoTemplate.class)`
- [ ] `@ConditionalOnMissingBean(McpSessionStore.class)`
- [ ] Returns a `McpSessionStore` bean.

### Tests

- [ ] `MongoMcpSessionStoreTest` uses Testcontainers MongoDB
      (`mongo:7` image or similar) and exercises all six
      methods.
- [ ] Dedicated tests cover:
  - **TTL filtering on read**: save with past `expiresAt`
    (bypassing the normal API to simulate the expiration
    window), verify `find` returns empty because the filter
    excludes it.
  - **update preserves expiresAt**: save with a far-future
    `expiresAt`, update payload, verify the `expiresAt` field
    is unchanged in the underlying document.
  - **touch updates only expiresAt**: save, touch with a new
    TTL, verify the `payload` field is unchanged and
    `expiresAt` has advanced.
  - **TTL index exists**: query `db.mocapi_sessions.getIndexes()`
    and verify the `expiresAt` index has
    `expireAfterSeconds: 0`.
- [ ] `MongoMcpSessionStoreAutoConfigurationTest` uses
      `ApplicationContextRunner` with a mock or embedded
      `MongoTemplate` and verifies conditional activation.
- [ ] `mvn verify` on the new module is green.
- [ ] `mvn verify` on the full reactor is green.

## Implementation notes

- **TTL monitor latency**: MongoDB's TTL monitor runs every ~60
  seconds by default. This isn't configurable from the
  application level — it's a server-side setting. For tests that
  exercise TTL expiration, DON'T rely on the monitor actually
  deleting the document; instead verify the read-path filter
  (`expiresAt > now`) correctly excludes expired documents.
- **`_id` as the session ID**: using the natural session ID as
  `_id` avoids a separate index and makes `findOne({_id: ...})`
  very fast. Don't introduce a surrogate primary key.
- **Payload as a JSON string** (rather than a nested BSON
  document): keeps the format consistent with the Redis,
  Hazelcast, JDBC, and Cassandra implementations. Trading a
  tiny amount of query convenience (no BSON field-level
  queries) for consistency across backends and simpler
  serialization. If a future spec needs to query into session
  data, revisit.
- **Don't use Spring Data MongoDB's repository abstractions**.
  Go directly to `MongoTemplate` / `MongoCollection` — same
  philosophy as the Cassandra spec (avoid heavy abstractions
  for a simple CRUD use case).
- **`ObjectMapper` dependency**: injected from the application's
  bean, same as the other modules. Don't bring in a separate
  mapper.
- **Commit granularity**: one commit for scaffolding + store
  implementation + Testcontainers test, one for auto-config
  + its test. Or bundle if small enough.
- **A future spec can add a MongoDB example app** (similar to
  specs 127-129) with a docker-compose MongoDB and a README.
  Not in scope for this spec.
