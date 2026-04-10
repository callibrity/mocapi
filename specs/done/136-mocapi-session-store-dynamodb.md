# DynamoDB-backed McpSessionStore module (mocapi-session-store-dynamodb)

## What to build

Create a new Maven module `mocapi-session-store-dynamodb` that
provides an Amazon DynamoDB-backed implementation of
`McpSessionStore`. Same drop-in pattern as specs 113 (Redis),
114 (Hazelcast), 115 (JDBC), 130 (Cassandra), and 131 (MongoDB):
adding the jar to a Spring Boot application's classpath
automatically replaces the in-memory fallback with a DynamoDB
implementation, as long as a `DynamoDbClient` bean is present.

### Why DynamoDB

1. **Native TTL attribute** — DynamoDB supports a table-level
   TTL configuration that tracks a numeric attribute containing
   the Unix epoch seconds. When the value is in the past,
   DynamoDB's background scanner removes the item within
   ~48 hours. For session data, that window is too loose to
   rely on for expiration semantics — we filter expired items
   at read time (same pattern as the JDBC module) and let TTL
   handle eventual cleanup.
2. **AWS-managed** — no servers to run, no patching, no scaling
   decisions. Fits deployments that are already AWS-native
   (ECS, EKS, Lambda, App Runner, etc.).
3. **Very low latency** — single-digit millisecond reads and
   writes at the 99th percentile with on-demand or provisioned
   capacity.
4. **IAM-based auth** — no credentials on the app side; the
   host's IAM role grants access. Operationally simpler than
   managing Redis AUTH tokens or database passwords.
5. **Global tables** — multi-region replication with eventual
   consistency for deployments that need geo-distribution.

### Session-store-only scope

DynamoDB isn't a message broker. Substrate has
`substrate-mailbox-dynamodb` and `substrate-journal-dynamodb`
on Maven Central but no `substrate-notifier-dynamodb`. An
all-in-one DynamoDB starter would be "mostly full" —
session + mailbox + journal on DynamoDB, notifier in-memory
(or optionally swapped for `substrate-notifier-sns` for an
AWS-native notifier). That's covered by a separate spec for
`mocapi-aws-spring-boot-starter` (spec 137). This spec covers
only the session store library module.

### Module structure

```
mocapi-session-store-dynamodb/
├── pom.xml
└── src/
    ├── main/
    │   └── java/com/callibrity/mocapi/session/dynamodb/
    │       ├── DynamoDbMcpSessionStore.java
    │       ├── DynamoDbMcpSessionStoreAutoConfiguration.java
    │       └── DynamoDbSessionStoreProperties.java
    ├── main/resources/META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/com/callibrity/mocapi/session/dynamodb/
            ├── DynamoDbMcpSessionStoreTest.java    (Testcontainers LocalStack)
            └── DynamoDbMcpSessionStoreAutoConfigurationTest.java
```

Add the module to the parent pom's `<modules>` list.

### Table schema

One DynamoDB table (`mocapi_sessions` by default, configurable),
with a simple schema:

| Attribute | Type | Purpose |
|---|---|---|
| `session_id` | String (S) | Partition key, the MCP session ID |
| `payload` | String (S) | JSON-serialized `McpSession` |
| `expires_at` | Number (N) | Unix epoch seconds — TTL attribute |

Partition key: `session_id` (hash only, no sort key). One item
per session.

### TTL configuration

The table's TTL is configured to use the `expires_at` attribute:

```java
dynamoDbClient.updateTimeToLive(
    UpdateTimeToLiveRequest.builder()
        .tableName(tableName)
        .timeToLiveSpecification(
            TimeToLiveSpecification.builder()
                .enabled(true)
                .attributeName("expires_at")
                .build())
        .build());
```

The store's `@PostConstruct` (or constructor) ensures the TTL
is enabled — idempotent via
`dynamoDbClient.describeTimeToLive(...)` to check the current
state before calling `updateTimeToLive`. If the operator has
already configured it manually, the check is a no-op.

### Table creation

DynamoDB doesn't have a "schema init on startup" mechanism like
Spring Boot's `spring.sql.init` for JDBC. The module should
**not** auto-create the table — that's an operator concern
(CloudFormation, Terraform, CDK, or manual `aws dynamodb
create-table` calls). The store verifies the table exists at
startup via `describeTable(...)` and throws a clear error if
not:

```java
try {
  dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName(tableName).build());
} catch (ResourceNotFoundException e) {
  throw new IllegalStateException(
      "DynamoDB table '" + tableName + "' does not exist. Create it via your "
          + "infrastructure-as-code tool (CloudFormation, Terraform, CDK) before "
          + "starting the application. See the module's javadoc for the expected "
          + "schema.", e);
}
```

Ship a `README.md` (or package-info.java) with a CloudFormation
snippet and a CLI example for operators.

### `DynamoDbMcpSessionStore` implementation

Constructor dependencies:
- `DynamoDbClient` (auto-wired from Spring Cloud AWS, AWS SDK
  for Java v2, or Spring Boot's AWS auto-configuration)
- `ObjectMapper`
- `DynamoDbSessionStoreProperties` (table name, etc.)

### `save(session, ttl)`

```java
long expiresAt = Instant.now().plus(ttl).getEpochSecond();
String json = objectMapper.writeValueAsString(session);
dynamoDbClient.putItem(
    PutItemRequest.builder()
        .tableName(tableName)
        .item(Map.of(
            "session_id", AttributeValue.fromS(session.sessionId()),
            "payload", AttributeValue.fromS(json),
            "expires_at", AttributeValue.fromN(String.valueOf(expiresAt))))
        .build());
```

`putItem` is an upsert — overwrites the existing item or
creates a new one.

### `update(sessionId, session)` — preserving TTL

DynamoDB's `UpdateItem` with `UpdateExpression` can modify
specific attributes while leaving others untouched:

```java
String json = objectMapper.writeValueAsString(session);
dynamoDbClient.updateItem(
    UpdateItemRequest.builder()
        .tableName(tableName)
        .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
        .updateExpression("SET payload = :p")
        .expressionAttributeValues(Map.of(":p", AttributeValue.fromS(json)))
        .build());
```

Only the `payload` attribute is updated. The `expires_at`
attribute is untouched, so the TTL is preserved naturally —
same pattern as the MongoDB `$set` update (spec 131).

### `find(sessionId)` — filter expired at read time

```java
GetItemResponse response = dynamoDbClient.getItem(
    GetItemRequest.builder()
        .tableName(tableName)
        .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
        .consistentRead(true)
        .build());
if (!response.hasItem()) return Optional.empty();
var item = response.item();
long expiresAt = Long.parseLong(item.get("expires_at").n());
if (expiresAt <= Instant.now().getEpochSecond()) {
  return Optional.empty();  // expired but not yet cleaned up
}
String payload = item.get("payload").s();
return Optional.of(objectMapper.readValue(payload, McpSession.class));
```

DynamoDB's TTL cleanup runs asynchronously and can lag by up to
48 hours, so filtering at read time is essential. `consistentRead(true)`
for strong consistency (session reads should see the latest
write).

### `touch(sessionId, ttl)`

```java
long expiresAt = Instant.now().plus(ttl).getEpochSecond();
dynamoDbClient.updateItem(
    UpdateItemRequest.builder()
        .tableName(tableName)
        .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
        .updateExpression("SET expires_at = :e")
        .expressionAttributeValues(Map.of(":e", AttributeValue.fromN(String.valueOf(expiresAt))))
        .build());
```

Updates only `expires_at`, leaving `payload` untouched.

### `delete(sessionId)`

```java
dynamoDbClient.deleteItem(
    DeleteItemRequest.builder()
        .tableName(tableName)
        .key(Map.of("session_id", AttributeValue.fromS(sessionId)))
        .build());
```

Idempotent — DynamoDB doesn't error on deleting a non-existent
item.

### Auto-configuration

`DynamoDbMcpSessionStoreAutoConfiguration` activates when:

- A `DynamoDbClient` bean is present (from Spring Cloud AWS or
  a user-provided bean)
- No user-provided `McpSessionStore` bean

```java
@AutoConfiguration(before = MocapiAutoConfiguration.class)
@ConditionalOnClass(DynamoDbClient.class)
@ConditionalOnBean(DynamoDbClient.class)
@ConditionalOnMissingBean(McpSessionStore.class)
@EnableConfigurationProperties(DynamoDbSessionStoreProperties.class)
public class DynamoDbMcpSessionStoreAutoConfiguration {

  @Bean
  McpSessionStore dynamoDbMcpSessionStore(
      DynamoDbClient dynamoDbClient,
      ObjectMapper objectMapper,
      DynamoDbSessionStoreProperties properties) {
    return new DynamoDbMcpSessionStore(dynamoDbClient, objectMapper, properties);
  }
}
```

The `@ConditionalOnClass` is defensive — if the AWS SDK isn't
on the classpath at all, the auto-config silently no-ops
instead of failing.

### Properties

```java
@ConfigurationProperties("mocapi.session.dynamodb")
public class DynamoDbSessionStoreProperties {
  /** DynamoDB table name. Default: mocapi_sessions. */
  private String tableName = "mocapi_sessions";
  /** Whether to verify the table exists at startup. Default: true. */
  private boolean verifyTable = true;
  /** Whether to auto-enable TTL on the expires_at attribute. Default: true. */
  private boolean ensureTtl = true;
  // getters/setters
}
```

### Dependencies

```xml
<dependencies>
  <dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-core</artifactId>
    <version>${project.version}</version>
  </dependency>

  <!-- AWS SDK for Java v2 — DynamoDB client -->
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb</artifactId>
    <optional>true</optional>
  </dependency>

  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
  </dependency>

  <!-- Test: Testcontainers LocalStack for DynamoDB -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>localstack</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

The AWS SDK v2 `dynamodb` module brings in the `DynamoDbClient`
and related types. It's marked `optional` because consumers
not using DynamoDB shouldn't pay the ~10MB dependency cost;
the all-in-one AWS starter (spec 137) declares it as a regular
dependency.

## Acceptance criteria

### Module structure

- [ ] New module `mocapi-session-store-dynamodb` exists with
      correct parent reference.
- [ ] Listed in parent pom's `<modules>`.
- [ ] Package is `com.callibrity.mocapi.session.dynamodb`.
- [ ] License headers, spotless formatting, and pom conventions
      match the rest of the project.

### Implementation

- [ ] `DynamoDbMcpSessionStore` implements all six
      `McpSessionStore` methods using `DynamoDbClient` directly
      (no Enhanced Client / `DynamoDbTable` abstractions — stay
      lean, same philosophy as specs 130 and 131).
- [ ] `save(session, ttl)` uses `PutItemRequest` to upsert with
      `session_id`, `payload`, and `expires_at` attributes.
- [ ] `update(sessionId, session)` uses `UpdateItemRequest`
      with `UpdateExpression = "SET payload = :p"`, preserving
      `expires_at`.
- [ ] `find(sessionId)` uses `GetItemRequest` with
      `consistentRead = true` and filters expired items at
      read time (comparing `expires_at` to current epoch).
- [ ] `touch(sessionId, ttl)` uses `UpdateItemRequest` with
      `UpdateExpression = "SET expires_at = :e"`, preserving
      `payload`.
- [ ] `delete(sessionId)` uses `DeleteItemRequest`, idempotent.
- [ ] Store initialization (constructor or `@PostConstruct`)
      verifies the table exists via `describeTable(...)` and
      throws `IllegalStateException` with a helpful message
      if not. This check is gated by
      `mocapi.session.dynamodb.verify-table` (default true).
- [ ] Store initialization ensures TTL is enabled on the
      `expires_at` attribute via `describeTimeToLive` +
      `updateTimeToLive` (idempotent). Gated by
      `mocapi.session.dynamodb.ensure-ttl` (default true).

### Auto-configuration

- [ ] `DynamoDbMcpSessionStoreAutoConfiguration` is registered
      in the auto-config imports file.
- [ ] `@AutoConfiguration(before = MocapiAutoConfiguration.class)`
- [ ] `@ConditionalOnClass(DynamoDbClient.class)` — defensive,
      in case the AWS SDK isn't on the classpath.
- [ ] `@ConditionalOnBean(DynamoDbClient.class)`
- [ ] `@ConditionalOnMissingBean(McpSessionStore.class)`
- [ ] Returns a `McpSessionStore` bean.
- [ ] `DynamoDbSessionStoreProperties` is a standard
      `@ConfigurationProperties` class with `tableName`,
      `verifyTable`, `ensureTtl` fields.

### Documentation

- [ ] The module ships a `README.md` (or package-info.java)
      with:
  - Expected table schema (partition key, attributes)
  - CloudFormation snippet for the table definition
  - AWS CLI `create-table` command example
  - Explanation that TTL cleanup is eventual (up to 48h) and
    that reads are filtered against `expires_at` regardless

### Tests

- [ ] `DynamoDbMcpSessionStoreTest` uses Testcontainers
      LocalStack (`localstack/localstack` image) and exercises
      all six methods against a real DynamoDB-compatible
      endpoint.
- [ ] Dedicated tests cover:
  - **Expired-item filter**: insert an item with
    `expires_at` in the past, verify `find` returns empty
    (the item is still in the table but filtered at read
    time).
  - **update preserves expires_at**: save with a far-future
    `expires_at`, update payload, verify `expires_at`
    unchanged via a direct `getItem` call.
  - **touch updates only expires_at**: save, touch, verify
    `payload` unchanged and `expires_at` advanced.
  - **Missing table**: construct the store against a
    non-existent table with `verifyTable = true`, assert
    `IllegalStateException`.
- [ ] `DynamoDbMcpSessionStoreAutoConfigurationTest` uses
      `ApplicationContextRunner` with a mock `DynamoDbClient`
      and verifies:
  - Store is registered when `DynamoDbClient` is present.
  - Store is NOT registered when the SDK is absent.
  - User-provided `McpSessionStore` wins.
- [ ] `mvn verify` on the new module is green. Testcontainers
      LocalStack runs only if Docker is available.
- [ ] `mvn verify` on the full reactor is green.

## Implementation notes

- **AWS SDK v2 vs v1**: use v2 (`software.amazon.awssdk`).
  v1 is in maintenance mode. Spring Cloud AWS 3.x and Spring
  Boot's AWS support both standardize on v2.
- **`DynamoDbClient` bean source**: the store accepts whatever
  `DynamoDbClient` bean is available. Typical sources:
  - Spring Cloud AWS (`io.awspring.cloud:spring-cloud-aws-starter-dynamodb`)
  - Manual `@Bean` in the consumer's configuration
  - Spring Boot's own AWS auto-configuration (if it ever
    adds one)
  The store doesn't care which — just injects the bean.
- **Enhanced Client vs low-level**: AWS SDK v2 offers a
  `DynamoDbEnhancedClient` with bean-mapped tables. It's
  nicer for complex domain objects but adds machinery. For
  the session store's simple three-attribute shape, the
  low-level `DynamoDbClient` is cleaner and has no reflection
  overhead. Stay low-level.
- **TTL clock skew**: the `expires_at` attribute uses the
  *server's* current epoch time when the store writes it.
  DynamoDB's TTL cleanup uses *DynamoDB's* server clock for
  comparison. Clock skew between the app server and
  DynamoDB is typically sub-second and doesn't matter for
  session timeouts measured in minutes. Don't
  pre-optimize — just use `Instant.now()`.
- **IAM permissions**: document the required IAM permissions
  in the module's README:
  - `dynamodb:PutItem`
  - `dynamodb:UpdateItem`
  - `dynamodb:GetItem`
  - `dynamodb:DeleteItem`
  - `dynamodb:DescribeTable`
  - `dynamodb:DescribeTimeToLive` (if `verifyTable` or
    `ensureTtl` is enabled)
  - `dynamodb:UpdateTimeToLive` (if `ensureTtl` is enabled)
  Scoped to the specific table ARN.
- **Don't introduce a DynamoDB table name validation regex**.
  DynamoDB already enforces its own naming rules
  (3-255 chars, `[a-zA-Z0-9_.-]`). The API rejects invalid
  names with a clear error — that's fine.
- **Commit granularity**: one commit for module scaffolding
  + store implementation + LocalStack test, one for
  auto-config + its test. Or bundle if small enough.
