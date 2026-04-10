# In-memory example app (examples/in-memory)

## What to build

Create a Spring Boot example app at
`examples/in-memory/` that consumes `examples-lib` (spec 125)
and runs with the default in-memory fallback backends for
`McpSessionStore`, `Mailbox`, `Journal`, and `Notifier`. It is
the simplest possible mocapi deployment — zero external
infrastructure, runs with `mvn spring-boot:run` and nothing
else.

### Why

Not every operator wants to run Redis/Hazelcast/Postgres just
to try mocapi. The in-memory example is the starting-point
experience: "clone the repo, `cd examples/in-memory`, `mvn
spring-boot:run`, hit `/mcp` with an MCP client, done."

### Module structure

```
examples/in-memory/
├── pom.xml
├── README.md                     (how to run, what to try)
└── src/
    └── main/
        ├── java/com/callibrity/mocapi/examples/inmemory/
        │   └── InMemoryExampleApplication.java
        └── resources/
            └── application.yml
```

No tests in this module — the example tools are tested in
`examples-lib`, and this module is too thin to have its own
test surface. A smoke test at the reactor level verifies the
app starts and has the expected beans.

### `pom.xml`

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-examples</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-example-in-memory</artifactId>
  <name>Mocapi - Example (In-Memory)</name>
  <description>
    Mocapi example app with in-memory backends. Zero external
    infrastructure — runs with mvn spring-boot:run.
  </description>

  <dependencies>
    <dependency>
      <groupId>com.callibrity.mocapi</groupId>
      <artifactId>mocapi-examples-lib</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
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

### `InMemoryExampleApplication.java`

Delegates to `ExampleApplication.main(args)` so the shared
bootstrap handles all the component scanning:

```java
package com.callibrity.mocapi.examples.inmemory;

import com.callibrity.mocapi.examples.ExampleApplication;

public class InMemoryExampleApplication {
  public static void main(String[] args) {
    ExampleApplication.main(args);
  }
}
```

Alternatively — if tests or ops tooling reference a
module-local class — make this a thin `@SpringBootApplication`
that `@Import`s the `ExampleApplication` configuration. Pick
whichever feels idiomatic.

### `application.yml`

Minimal — just a random master key for the session encryption
so the app boots cleanly:

```yaml
mocapi:
  session-encryption-master-key: ${MOCAPI_MASTER_KEY:ChangeMeForProduction1234567890abcd}

server:
  port: 8080

logging:
  level:
    com.callibrity.mocapi: INFO
```

The `ChangeMeForProduction...` default is intentionally a weak
placeholder so the app boots for demo purposes. Document in
the README that production deployments must set
`MOCAPI_MASTER_KEY` explicitly.

### `README.md`

A short guide covering:

1. **Prerequisites**: JDK 25, Maven 3.6+.
2. **Run**: `cd examples/in-memory && mvn spring-boot:run`.
3. **Test it**: example curl commands to hit `/mcp` with a
   minimal MCP `initialize` request and a `tools/call` for
   `HelloTool`.
4. **What's included**: brief description of the three tools
   (Hello, Rot13, Countdown) pulled from `examples-lib`.
5. **Limitations**: in-memory only — state is lost on
   restart, sessions don't survive, no clustering. For
   production, use one of the backend-specific examples
   (Redis, Hazelcast, PostgreSQL).

## Acceptance criteria

- [ ] New module `mocapi-example-in-memory` at
      `examples/in-memory/` with parent `mocapi-examples`.
- [ ] Module is listed in `examples/pom.xml`'s `<modules>`
      list.
- [ ] Depends only on `mocapi-examples-lib` and
      `spring-boot-starter-web` — no backend-specific modules.
- [ ] `InMemoryExampleApplication` class exists and delegates
      to / imports `ExampleApplication`.
- [ ] `application.yml` exists with a master key placeholder
      and an `INFO`-level logging config.
- [ ] `README.md` exists with prerequisites, run command,
      sample curl commands, and the "not for production"
      disclaimer.
- [ ] Running `mvn -pl examples/in-memory spring-boot:run`
      starts the app and the `/mcp` endpoint responds to an
      MCP `initialize` POST.
- [ ] The `/mcp` endpoint accepts a `tools/call` for `hello`
      (from `HelloTool`) and returns the expected response.
- [ ] `mvn verify` passes across the full reactor.
- [ ] No Testcontainers dependency — this example must run
      without Docker.

## Implementation notes

- **Depends on spec 125** (the examples scaffolding).
- **No `docker-compose.yml`** — the whole point of the
  in-memory example is that no infrastructure is needed.
- **No backend-specific dependencies** — if you find yourself
  adding a Redis client or a JDBC driver to this module's
  pom, you're in the wrong module.
- **Do NOT add new tools in this spec**. The example tools
  come from `examples-lib`. If you want a new tool, add it
  there and it becomes available to every example app
  automatically.
- **`spring-boot-starter-web` vs `mocapi-spring-boot-starter`**:
  the mocapi starter transitively pulls in the web starter.
  Depending on `examples-lib` (which depends on
  `mocapi-spring-boot-starter`) is sufficient. The explicit
  `spring-boot-starter-web` dependency in the sample pom above
  is redundant — remove it unless a specific reason exists.
- **The `MOCAPI_MASTER_KEY` env var default**: the
  `${MOCAPI_MASTER_KEY:ChangeMeForProduction...}` syntax uses
  the Spring Boot property placeholder syntax to fall back to
  a literal default when the env var isn't set. This lets
  `mvn spring-boot:run` work without any extra setup while
  making the production-readiness story clear.
- **Commit suggested**: one commit creating the whole module
  (pom + application class + yaml + readme).
