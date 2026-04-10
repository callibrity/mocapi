# Redis example app (examples/redis)

## What to build

A Spring Boot example app at `examples/redis/` that consumes
`examples-lib` (spec 125) + the Redis all-in-one starter
(`mocapi-redis-spring-boot-starter`, spec 122), demonstrating
a full Redis-backed mocapi deployment with session store,
mailbox, journal, and notifier all on Redis.

Ships a `docker-compose.yml` that spins up a local Redis
instance so operators can `docker compose up -d && mvn
spring-boot:run` and have everything working in under a
minute.

### Module structure

```
examples/redis/
├── pom.xml
├── README.md
├── docker-compose.yml
└── src/
    └── main/
        ├── java/com/callibrity/mocapi/examples/redis/
        │   └── RedisExampleApplication.java
        └── resources/
            └── application.yml
```

### `pom.xml`

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-examples</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-example-redis</artifactId>
  <name>Mocapi - Example (Redis)</name>
  <description>
    Mocapi example app with Redis-backed session store, mailbox,
    journal, and notifier. Uses mocapi-redis-spring-boot-starter
    for one-dependency setup.
  </description>

  <dependencies>
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-examples-lib</artifactId>
      <version>${project.version}</version>
    </dependency>

    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-redis-spring-boot-starter</artifactId>
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

One dependency on the all-in-one Redis starter pulls in
everything — mocapi core, the Redis session store, substrate
Redis backends, `spring-boot-starter-data-redis`.

### `RedisExampleApplication.java`

```java
package com.callibrity.mocapi.examples.redis;

import com.callibrity.mocapi.examples.ExampleApplication;

public class RedisExampleApplication {
  public static void main(String[] args) {
    ExampleApplication.main(args);
  }
}
```

### `docker-compose.yml`

```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: mocapi-example-redis
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
```

Minimal — one service, health check, port exposed to localhost.
Operators run `docker compose up -d` in the module directory
and the app can connect to `localhost:6379`.

### `application.yml`

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY:ChangeMeForProduction1234567890abcd}

spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

server:
  port: 8080

logging:
  level:
    com.callibrity.mocapi: INFO
```

The `REDIS_HOST` / `REDIS_PORT` defaults point at the
docker-compose Redis. For cloud deployments, operators
override via env vars.

### `README.md`

Cover:

1. **Prerequisites**: JDK 25, Maven 3.6+, Docker + Docker Compose.
2. **Start infra**: `docker compose up -d` to start Redis.
3. **Run the app**: `mvn spring-boot:run`.
4. **Verify session persistence**: hit the app with an MCP
   client, initialize a session, then RESTART the app
   (`Ctrl+C`, `mvn spring-boot:run` again) and note that the
   session ID is still valid — state survived the restart,
   unlike the in-memory example.
5. **Tear down**: `docker compose down -v` to remove the
   volume too if you want a clean slate.
6. **Cloud deployment hint**: point `REDIS_HOST` /
   `REDIS_PORT` + auth env vars at any Redis 6.0+ instance.

## Acceptance criteria

- [ ] New module `mocapi-example-redis` at `examples/redis/`
      with parent `mocapi-examples`.
- [ ] Listed in `examples/pom.xml`'s `<modules>`.
- [ ] Depends on `mocapi-examples-lib` and
      `mocapi-redis-spring-boot-starter`.
- [ ] No direct dependencies on `mocapi-session-store-redis`,
      `substrate-*-redis`, or `spring-boot-starter-data-redis`
      — all of these come transitively through the starter.
- [ ] `RedisExampleApplication` class exists.
- [ ] `docker-compose.yml` exists at the module root with a
      Redis 7 service + health check + port 6379 exposed.
- [ ] `application.yml` configures `spring.data.redis.host/port`
      with env var defaults pointing at localhost.
- [ ] `README.md` documents prerequisites, the docker-compose
      infra startup, the app run command, a session-persistence
      verification recipe, and cloud deployment hints.
- [ ] With Redis running (via `docker compose up -d`), running
      `mvn -pl examples/redis spring-boot:run` starts the app,
      the startup logs show the Redis-backed session store
      being used (not the in-memory fallback), and hitting
      `/mcp` with an MCP `initialize` works.
- [ ] Session state persists across app restarts (verify by
      initializing, stopping, restarting, and using the same
      session id).
- [ ] `mvn verify` passes across the full reactor.

## Implementation notes

- **Depends on specs 113 (Redis session store), 122 (Redis
  all-in-one starter), and 125 (examples scaffolding)**. All
  three need to land before this spec can be implemented.
- **No Java tests in this module**. Smoke testing is done via
  a manual run against docker-compose. The underlying pieces
  (session store, starter, tools) are tested in their own
  modules.
- **Docker health check**: the `docker-compose.yml` includes
  a health check so operators can `docker compose up -d &&
  docker compose ps` and see `healthy` before starting the
  app. Without the health check, the app might start before
  Redis is ready and crash.
- **`REDIS_HOST` env var**: defaulting to `localhost` keeps
  the zero-config local experience while letting operators
  override for cloud or clustered deployments. Don't hardcode
  `localhost`.
- **No `.env` file in this module** — keep the configuration
  surface simple. Operators who want env-var-driven config
  can set them at the shell level.
- **Commit suggested**: one commit creating the whole module
  (pom + app class + yaml + docker-compose + readme).
