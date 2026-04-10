# Hazelcast example app (examples/hazelcast)

## What to build

A Spring Boot example app at `examples/hazelcast/` that consumes
`examples-lib` (spec 125) + the Hazelcast all-in-one starter
(`mocapi-hazelcast-spring-boot-starter`, spec 123), demonstrating
a full Hazelcast-backed mocapi deployment with session store,
mailbox, journal, and notifier on Hazelcast.

Unlike Redis and PostgreSQL, Hazelcast runs **embedded in the
JVM** by default — no separate server process, no Docker image
needed. The example app therefore ships **no docker-compose.yml**
(a single-member embedded Hazelcast cluster is the default), but
the README explains how to connect to a client/server Hazelcast
cluster if operators want that mode.

### Module structure

```
examples/hazelcast/
├── pom.xml
├── README.md
└── src/
    └── main/
        ├── java/com/callibrity/mocapi/examples/hazelcast/
        │   └── HazelcastExampleApplication.java
        └── resources/
            ├── application.yml
            └── hazelcast.yaml       (optional; defaults if absent)
```

### `pom.xml`

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-examples</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-example-hazelcast</artifactId>
  <name>Mocapi - Example (Hazelcast)</name>
  <description>
    Mocapi example app with Hazelcast-backed session store, mailbox,
    journal, and notifier. Uses embedded Hazelcast by default — no
    docker required.
  </description>

  <dependencies>
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-examples-lib</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-hazelcast-spring-boot-starter</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### `HazelcastExampleApplication.java`

```java
package com.callibrity.mocapi.examples.hazelcast;

import com.callibrity.mocapi.examples.ExampleApplication;

public class HazelcastExampleApplication {
  public static void main(String[] args) {
    ExampleApplication.main(args);
  }
}
```

### `application.yml`

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY:ChangeMeForProduction1234567890abcd}

spring:
  hazelcast:
    config: classpath:hazelcast.yaml

server:
  port: 8080

logging:
  level:
    com.callibrity.mocapi: INFO
```

### `hazelcast.yaml` (embedded single-member config)

```yaml
hazelcast:
  cluster-name: mocapi-example
  network:
    join:
      multicast:
        enabled: false
      tcp-ip:
        enabled: false
  instance-name: mocapi-example-instance
```

Multicast and TCP-IP join are disabled so each app instance
runs as a standalone single-member cluster (no accidental
peer-to-peer clustering with other dev machines on the same
network). For real clustering, operators edit this file to
enable join discovery.

### `README.md`

Cover:

1. **Prerequisites**: JDK 25, Maven 3.6+. **No Docker needed**
   by default — Hazelcast runs embedded.
2. **Run the app**: `mvn spring-boot:run`.
3. **Verify session persistence inside one JVM**: unlike the
   in-memory example, state lives in the Hazelcast `IMap`, so
   you can verify that session/mailbox entries are managed
   through Hazelcast's APIs (tooling like Hazelcast Management
   Center can introspect the data).
4. **Cluster mode**: describe how to edit `hazelcast.yaml` to
   enable TCP-IP or multicast discovery, start two instances
   on different ports (override `server.port` via env var or
   profile), and observe that sessions are visible from either
   instance.
5. **Client/server mode**: describe how to run a standalone
   Hazelcast server and have the app connect as a client via
   `hazelcast-client.yaml` instead of embedded mode. Reference
   Spring Boot's Hazelcast auto-configuration docs.

## Acceptance criteria

- [ ] New module `mocapi-example-hazelcast` at
      `examples/hazelcast/` with parent `mocapi-examples`.
- [ ] Listed in `examples/pom.xml`'s `<modules>`.
- [ ] Depends on `mocapi-examples-lib` and
      `mocapi-hazelcast-spring-boot-starter`. No direct
      dependencies on `mocapi-session-store-hazelcast` or
      `hazelcast-spring` — all transitive.
- [ ] `HazelcastExampleApplication` class exists.
- [ ] `application.yml` points `spring.hazelcast.config` at
      the bundled `hazelcast.yaml`.
- [ ] `hazelcast.yaml` disables multicast and TCP-IP discovery
      so the default single-JVM run doesn't accidentally
      cluster with other machines.
- [ ] **No `docker-compose.yml`** — Hazelcast runs embedded.
- [ ] `README.md` explains:
  - The embedded default (no Docker needed)
  - How to enable cluster mode by editing `hazelcast.yaml`
  - How to switch to client/server mode via
    `hazelcast-client.yaml`
- [ ] `mvn -pl examples/hazelcast spring-boot:run` starts the
      app, the startup logs show a single-member Hazelcast
      cluster forming, and hitting `/mcp` with an MCP
      `initialize` request works.
- [ ] The startup logs show the Hazelcast-backed session store
      (not the in-memory fallback).
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Depends on specs 114, 123, and 125**.
- **Embedded by default is a feature, not a bug**. Hazelcast's
  strength over Redis for dev is that there's no infra to
  spin up — the app *is* the infra. The README should lead
  with this.
- **Cluster name**: using `mocapi-example` keeps accidental
  test clusters from mixing with production deployments that
  might default to `dev` or similar. Override via env var for
  production.
- **Discovery disabled by default**: multicast on a dev
  laptop can cause surprising clustering with coworkers'
  machines. Disable it in the shipped config.
- **No Docker compose** — if an operator specifically wants a
  Hazelcast server image, they can use
  `hazelcast/hazelcast:5.4` and connect via client mode, but
  that's a non-default config and documented in the README,
  not the default.
- **Commit suggested**: one commit creating the whole module.
