# Examples module scaffolding and shared example library

## What to build

Reorganize mocapi's example code into a top-level `examples/`
Maven aggregator that holds:

1. **`examples-lib/`** — a shared library of example tools,
   prompts, resources, and the `ExampleApplication` Spring Boot
   main class. Every example app depends on this module so the
   actual MCP content (tools/prompts/resources) is defined once
   and reused.
2. **Per-backend example apps** — one thin module per starter
   (in-memory, Redis, Hazelcast, PostgreSQL). Each is a
   `mocapi-example-<backend>` Spring Boot app whose only source
   files are a `pom.xml` (pulling in the backend's starter +
   `examples-lib`), a one-class `Application.java` that boots
   the shared `ExampleApplication` configuration, a per-backend
   `application.yml` with the right connection settings, and a
   `docker-compose.yml` (when the backend needs one) for
   spinning up the infrastructure locally.

This spec covers **only the scaffolding** — the directory layout,
the parent aggregator pom, the shared `examples-lib` module, and
the migration of existing example code from `mocapi-example/` to
the new structure. The per-backend example app modules are
separate specs (126, 127, 128, 129).

### Target directory layout

```
mocapi/
├── examples/
│   ├── pom.xml                     (aggregator, packaging=pom)
│   ├── examples-lib/
│   │   ├── pom.xml
│   │   └── src/main/java/com/callibrity/mocapi/examples/
│   │       ├── ExampleApplication.java    (@SpringBootApplication)
│   │       ├── tools/
│   │       │   ├── HelloTool.java
│   │       │   ├── Rot13Tool.java
│   │       │   └── CountdownTool.java
│   │       ├── prompts/
│   │       │   └── (moved from mocapi-example if any)
│   │       └── resources/
│   │           └── (moved from mocapi-example if any)
│   ├── in-memory/                   (spec 126)
│   ├── redis/                       (spec 127)
│   ├── hazelcast/                   (spec 128)
│   └── postgresql/                  (spec 129)
├── mocapi-model/
├── mocapi-core/
├── ... (other existing modules)
└── pom.xml                         (mocapi-parent)
```

### Aggregator pom (`examples/pom.xml`)

```xml
<project ...>
  <parent>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-parent</artifactId>
    <version>${project.version}</version>
  </parent>

  <artifactId>mocapi-examples</artifactId>
  <name>Mocapi - Examples</name>
  <description>Example applications demonstrating mocapi on various backends</description>
  <packaging>pom</packaging>

  <modules>
    <module>examples-lib</module>
    <!-- Per-backend apps added by specs 126-129 -->
  </modules>
</project>
```

The parent mocapi pom adds `<module>examples</module>` to its
`<modules>` list. The existing `<module>mocapi-example</module>`
is **removed** (its content migrates into `examples/examples-lib`).

### `examples-lib` module

A regular Spring Boot library (not a starter, not an app). Its
`pom.xml` depends on `mocapi-spring-boot-starter` for the mocapi
HTTP transport, dispatcher, annotations, etc. — but NOT on any
backend-specific module. The example tools don't care what backs
the session store / mailbox / journal.

```xml
<dependencies>
  <dependency>
    <groupId>com.callibrity.mocapi</groupId>
    <artifactId>mocapi-spring-boot-starter</artifactId>
    <version>${project.version}</version>
  </dependency>
</dependencies>
```

### `ExampleApplication.java`

A `@SpringBootApplication`-annotated class that scans the
`com.callibrity.mocapi.examples` package. Example apps (specs
126-129) just reference it via `@SpringBootTest(classes = ...)`
or a thin subclass:

```java
package com.callibrity.mocapi.examples;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExampleApplication {
  public static void main(String[] args) {
    SpringApplication.run(ExampleApplication.class, args);
  }
}
```

### Migration from existing `mocapi-example/`

Move the following files verbatim (adjusting package declarations):

- `mocapi-example/src/main/java/com/callibrity/mocapi/example/tools/HelloTool.java`
  → `examples/examples-lib/src/main/java/com/callibrity/mocapi/examples/tools/HelloTool.java`
- `mocapi-example/src/main/java/com/callibrity/mocapi/example/tools/Rot13Tool.java`
  → `examples/examples-lib/src/main/java/com/callibrity/mocapi/examples/tools/Rot13Tool.java`
- `mocapi-example/src/main/java/com/callibrity/mocapi/example/tools/CountdownTool.java`
  → `examples/examples-lib/src/main/java/com/callibrity/mocapi/examples/tools/CountdownTool.java`
- The existing `MocapiExampleApplication.java` becomes
  `ExampleApplication.java` (class rename + package change +
  relocation).

Move tests similarly:
- `HelloToolTest.java`, `HelloToolIT.java`, `Rot13ToolTest.java`,
  `Rot13ToolIT.java` → `examples/examples-lib/src/test/java/com/callibrity/mocapi/examples/tools/`.

**Package rename**: `com.callibrity.mocapi.example` →
`com.callibrity.mocapi.examples` (plural, to match the new
module name and emphasize that this is now a library shared by
multiple example apps).

### Delete the old `mocapi-example` module

After migrating, the old module is gone:
- Delete `mocapi-example/` directory entirely.
- Remove `<module>mocapi-example</module>` from the parent pom's
  `<modules>` list (it's been replaced by `<module>examples</module>`).

## Acceptance criteria

- [ ] New top-level `examples/` directory exists with an
      aggregator `pom.xml` (packaging=pom).
- [ ] `examples` is listed in the parent mocapi pom's
      `<modules>` list.
- [ ] `mocapi-example` is removed from the parent pom's
      `<modules>` list.
- [ ] The `mocapi-example/` directory is deleted.
- [ ] `examples/examples-lib/` module exists with a standard
      Spring Boot library pom depending on
      `mocapi-spring-boot-starter`.
- [ ] The three existing example tools (`HelloTool`, `Rot13Tool`,
      `CountdownTool`) are migrated to
      `examples/examples-lib/src/main/java/com/callibrity/mocapi/examples/tools/`
      with package declarations updated to
      `com.callibrity.mocapi.examples.tools`.
- [ ] The existing `MocapiExampleApplication` is replaced by
      `com.callibrity.mocapi.examples.ExampleApplication` in
      `examples-lib`.
- [ ] All existing tests for the example tools are migrated and
      still pass after the package rename.
- [ ] `mvn verify` passes across the full reactor.
- [ ] `mvn -pl examples/examples-lib test` passes in isolation.

## Implementation notes

- **This spec is scaffolding only** — it moves existing code
  around and sets up the aggregator. No new tools, prompts, or
  resources are added. Specs 126-129 add the per-backend thin
  apps that consume `examples-lib`.
- **Don't add new example tools in this spec**. The goal is to
  get the structure right. New example content can come in a
  later spec.
- **Package rename consistency**: every `import
  com.callibrity.mocapi.example.*;` in the migrated source must
  become `com.callibrity.mocapi.examples.*`. IDE refactor or a
  sed pass after the git mv works fine.
- **Lombok and Jackson**: the migrated tools use Lombok and
  Jackson 3 annotations. These come transitively through
  `mocapi-spring-boot-starter`. No additional dependencies
  needed in `examples-lib`.
- **Tests live in `examples-lib`**: unit tests for the shared
  tools stay alongside the source code they test. Per-backend
  example apps (specs 126-129) may add backend-specific
  integration tests but shouldn't duplicate the tool unit
  tests.
- **Git history**: prefer `git mv` over delete + create so the
  file history follows. The package-rename step is a separate
  commit: one commit to move files, one commit to update
  package declarations + imports.
- **Commit granularity**:
  1. Create `examples/` and `examples-lib/` scaffolding (poms).
  2. `git mv` tool sources and tests from `mocapi-example/` to
     `examples/examples-lib/`.
  3. Update package declarations and imports across the moved
     files.
  4. Delete the old `mocapi-example/` directory and update the
     parent pom `<modules>` list.
  5. Verify `mvn verify` is green.
