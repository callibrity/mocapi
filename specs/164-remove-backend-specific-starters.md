# Remove backend-specific Spring Boot starters

## What to build

Delete the backend-specific starter modules. They are convenience bundles that
add two dependencies (a Substrate backend + its Spring Boot starter). Users can
do this themselves, and the examples show how. The maintenance burden isn't worth it.

### Modules to delete

- `mocapi-redis-spring-boot-starter`
- `mocapi-hazelcast-spring-boot-starter`
- `mocapi-postgresql-spring-boot-starter`
- `mocapi-aws-spring-boot-starter`

### Update parent POM

Remove these modules from the `<modules>` section of the parent `pom.xml`.

### Update examples

The examples currently depend on the backend starters. Update them to depend on
`mocapi-spring-boot-starter` directly plus the appropriate Substrate backend and
Spring Boot data starter. For example, the Redis example:

```xml
<!-- Before -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-redis-spring-boot-starter</artifactId>
</dependency>

<!-- After -->
<dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.jwcarman.substrate</groupId>
    <artifactId>substrate-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Do the same for hazelcast, postgresql, and aws examples.

### Update documentation

If the README or any docs reference the backend starters, update to show the
direct dependency approach instead.

## Acceptance criteria

- [ ] All four backend starter modules deleted
- [ ] Parent POM updated — no references to deleted modules
- [ ] Examples updated to use direct dependencies
- [ ] All example modules compile
- [ ] README updated if it references backend starters
- [ ] All remaining tests pass (`mvn verify -pl '!mocapi-compat'`)

## Implementation notes

- The backend starters may have integration tests (e.g., Redis starter has
  Testcontainers tests). Those tests should move to the corresponding example
  module, or be deleted if the example already has equivalent coverage.
- Check for any cross-references in other POMs or documentation.
- The `mocapi-spring-boot-starter` stays — it's the primary starter.
- Substrate's own auto-configuration handles backend discovery. When the user
  adds `substrate-redis` to the classpath, Substrate auto-configures Redis
  implementations of `AtomFactory`, `MailboxFactory`, `JournalFactory`, etc.
  Mocapi doesn't need to know about it.
