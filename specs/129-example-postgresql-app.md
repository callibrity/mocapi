# PostgreSQL example app (examples/postgresql)

## What to build

A Spring Boot example app at `examples/postgresql/` that
consumes `examples-lib` (spec 125) + the JDBC session store
(`mocapi-session-store-jdbc`, spec 115) pointed at a
PostgreSQL database. Demonstrates a full JDBC-backed mocapi
deployment.

Ships a `docker-compose.yml` that spins up a local
PostgreSQL instance and initializes the schema.

### Important caveat — session store only

As of this writing, **there is no PostgreSQL-backed substrate
Mailbox, Journal, or Notifier**. The JDBC module
(`mocapi-session-store-jdbc`) only implements
`McpSessionStore`. For the other substrate SPIs, this example
falls back to the in-memory implementations.

**What this means in practice:**
- **`McpSessionStore`** → PostgreSQL (survives restarts, clusters,
  etc.)
- **`Mailbox`, `Journal`, `Notifier`** → in-memory (single-node
  only)

Document this limitation prominently in the README. For a
fully-distributed deployment, operators should use the Redis
or Hazelcast example instead. The PostgreSQL example is
appropriate for deployments that already have a PostgreSQL
database and want session persistence without adding a Redis
dependency.

### Module structure

```
examples/postgresql/
├── pom.xml
├── README.md
├── docker-compose.yml
└── src/
    └── main/
        ├── java/com/callibrity/mocapi/examples/postgresql/
        │   └── PostgreSqlExampleApplication.java
        └── resources/
            ├── application.yml
            └── schema.sql          (copied from mocapi-session-store-jdbc)
```

### `pom.xml`

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-examples</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-example-postgresql</artifactId>
  <name>Mocapi - Example (PostgreSQL)</name>
  <description>
    Mocapi example app with PostgreSQL-backed McpSessionStore.
    Note: Substrate Mailbox/Journal/Notifier fall back to
    in-memory — use the Redis or Hazelcast example for full
    distributed deployments.
  </description>

  <dependencies>
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-examples-lib</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-session-store-jdbc</artifactId>
      <version>${project.version}</version>
    </dependency>

    <!-- JDBC driver -->
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Spring Boot JDBC starter — provides DataSource auto-config -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
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

### `PostgreSqlExampleApplication.java`

```java
package com.callibrity.mocapi.examples.postgresql;

import com.callibrity.mocapi.examples.ExampleApplication;

public class PostgreSqlExampleApplication {
  public static void main(String[] args) {
    ExampleApplication.main(args);
  }
}
```

### `docker-compose.yml`

```yaml
services:
  postgres:
    image: postgres:16-alpine
    container_name: mocapi-example-postgres
    environment:
      POSTGRES_DB: mocapi
      POSTGRES_USER: mocapi
      POSTGRES_PASSWORD: mocapi
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U mocapi -d mocapi"]
      interval: 5s
      timeout: 3s
      retries: 5
```

### `application.yml`

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY:ChangeMeForProduction1234567890abcd}

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/mocapi}
    username: ${DB_USER:mocapi}
    password: ${DB_PASSWORD:mocapi}
  sql:
    init:
      mode: always
      schema-locations: classpath:schema/mocapi-session-store-postgresql.sql

server:
  port: 8080

logging:
  level:
    com.callibrity.mocapi: INFO
```

The `schema-locations` points at the schema file that spec
115 ships as part of `mocapi-session-store-jdbc`
(`src/main/resources/schema/mocapi-session-store-postgresql.sql`).
Spring Boot's SQL init runs it on startup to create the
`mocapi_sessions` table if it doesn't already exist.

### `README.md`

Cover:

1. **Prerequisites**: JDK 25, Maven 3.6+, Docker + Docker
   Compose.
2. **⚠️ Limitation**: session store only — mailbox, journal,
   notifier are in-memory. Single-node only for those
   capabilities. For full distributed deployments, use the
   Redis or Hazelcast example.
3. **Start infra**: `docker compose up -d` to start PostgreSQL.
4. **Run the app**: `mvn spring-boot:run`. The schema is
   auto-initialized on first startup.
5. **Verify session persistence**: initialize an MCP session,
   note the session ID, restart the app (`Ctrl+C`, rerun).
   The session ID still works. Query the `mocapi_sessions`
   table directly with `docker exec -it mocapi-example-postgres
   psql -U mocapi mocapi -c 'SELECT session_id, expires_at
   FROM mocapi_sessions;'` to see the raw row.
6. **Tear down**: `docker compose down -v`.
7. **Cloud deployment hint**: point `DB_URL`, `DB_USER`,
   `DB_PASSWORD` env vars at any PostgreSQL instance.

## Acceptance criteria

- [ ] New module `mocapi-example-postgresql` at
      `examples/postgresql/` with parent `mocapi-examples`.
- [ ] Listed in `examples/pom.xml`'s `<modules>`.
- [ ] Depends on `mocapi-examples-lib`,
      `mocapi-session-store-jdbc`, PostgreSQL JDBC driver, and
      `spring-boot-starter-jdbc`.
- [ ] `PostgreSqlExampleApplication` class exists.
- [ ] `docker-compose.yml` exists with a PostgreSQL 16 service
      + health check + credentials for `mocapi/mocapi/mocapi`.
- [ ] `application.yml` configures the datasource with env var
      defaults and references the session-store schema init
      script.
- [ ] `README.md` documents:
  - Prerequisites
  - **⚠️ Limitation warning** about mailbox/journal/notifier
    fallback
  - Infrastructure startup
  - App run command
  - Session persistence verification recipe
  - Cloud deployment hints
- [ ] With PostgreSQL running, `mvn -pl examples/postgresql
      spring-boot:run` starts the app, the schema is
      auto-initialized, the startup logs show the JDBC-backed
      session store being used, and hitting `/mcp` works.
- [ ] Session state persists across app restarts.
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Depends on specs 115 (JDBC session store) and 125
  (examples scaffolding)**.
- **No dependency on an "all-in-one postgresql starter"**
  because one doesn't exist (and shouldn't — PostgreSQL
  doesn't have the pub/sub primitives that mailbox and
  notifier need). This example is a direct consumer of
  `mocapi-session-store-jdbc`.
- **Schema init via Spring Boot's `spring.sql.init`**: the
  schema file lives in `mocapi-session-store-jdbc`'s jar at
  `classpath:schema/mocapi-session-store-postgresql.sql`.
  Spring Boot can resolve classpath resources from transitive
  dependencies, so referencing it from this module's
  `application.yml` just works.
- **Environment variable defaults**: keep the local
  experience zero-config (defaults point at the docker-compose
  instance) while letting cloud deployments override via env
  vars. Same pattern as the Redis example.
- **Credentials in docker-compose**: `mocapi/mocapi/mocapi` is
  intentionally trivial for a local demo. Operators must
  change them for any real deployment, and the README must
  say so.
- **Limitation disclaimer in the README is non-negotiable**.
  An operator who picks the PostgreSQL example expecting full
  clustering is going to be surprised in production if the
  mailbox/journal/notifier in-memory limitation isn't
  clearly flagged. Make it prominent.
- **Commit suggested**: one commit creating the whole module.
