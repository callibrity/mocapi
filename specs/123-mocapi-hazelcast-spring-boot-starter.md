# mocapi-hazelcast-spring-boot-starter (all-in-one Hazelcast starter)

## What to build

The Hazelcast analogue of spec 122 (Redis all-in-one starter).
A new Maven module `mocapi-hazelcast-spring-boot-starter` that
is a pure aggregation pom, depending on every mocapi and
substrate module that has a Hazelcast-backed implementation of
a pluggable SPI.

Same shape as the Redis starter, same rationale (one-coordinate
consumption of the full stack), different backend.

### Naming convention

Per Spring Boot's documented third-party starter convention
(`<name>-spring-boot-starter`), this module is named
`mocapi-hazelcast-spring-boot-starter`, NOT
`mocapi-spring-boot-starter-hazelcast`.

### Module structure

```
mocapi-hazelcast-spring-boot-starter/
└── pom.xml
```

Add to the parent pom's `<modules>` list.

### pom.xml

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-parent</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-hazelcast-spring-boot-starter</artifactId>
  <name>Mocapi - Hazelcast Spring Boot Starter</name>
  <description>
    All-in-one Hazelcast starter: bundles the mocapi Spring Boot
    starter plus Hazelcast implementations of McpSessionStore,
    Substrate Mailbox, Substrate Journal, and Substrate Notifier.
  </description>

  <dependencies>
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-spring-boot-starter</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-session-store-hazelcast</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-mailbox-hazelcast</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-journal-hazelcast</artifactId>
    </dependency>
    <dependency>
      <groupId>org.jwcarman.substrate</groupId>
      <artifactId>substrate-notifier-hazelcast</artifactId>
    </dependency>

    <dependency>
      <groupId>com.hazelcast</groupId>
      <artifactId>hazelcast-spring</artifactId>
    </dependency>
  </dependencies>
</project>
```

### Consumer usage

```xml
<dependency>
  <groupId>com.callibrity.mocapi</groupId>
  <artifactId>mocapi-hazelcast-spring-boot-starter</artifactId>
  <version>...</version>
</dependency>
```

Consumer optionally provides a `hazelcast.yaml` or
`application.yml` configuration for the Hazelcast instance. If
none is provided, Spring Boot auto-configures an embedded
single-member Hazelcast instance (fine for development; clustered
deployments need explicit config).

## Acceptance criteria

- [ ] New module `mocapi-hazelcast-spring-boot-starter` exists.
- [ ] Listed in parent pom's `<modules>`.
- [ ] Pure aggregation pom — no Java source.
- [ ] Declares dependencies on:
  - `mocapi-spring-boot-starter`
  - `mocapi-session-store-hazelcast` (from spec 114)
  - `substrate-mailbox-hazelcast`
  - `substrate-journal-hazelcast`
  - `substrate-notifier-hazelcast`
  - `hazelcast-spring`
- [ ] An integration test uses `@SpringBootTest` with an embedded
      Hazelcast instance, verifies that all four pluggable beans
      are the Hazelcast-backed implementations.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Spring Boot naming convention** — must be
  `mocapi-hazelcast-spring-boot-starter`, NOT
  `mocapi-spring-boot-starter-hazelcast`. See spec 122 for the
  rationale.
- **Dependency on spec 114** (the Hazelcast session store module)
  and on substrate's Hazelcast modules. Verify the substrate
  coordinates before committing.
- **Embedded vs client/server mode**: Hazelcast can run embedded
  in the JVM (the default Spring Boot behavior) or connect to a
  separate Hazelcast server. The starter doesn't force either —
  it inherits whatever Spring Boot's Hazelcast auto-configuration
  provides. Document both modes in the starter's readme.
- **No Java code**. Same as the Redis starter — pure aggregation.
- Same commit and versioning notes as spec 122.
