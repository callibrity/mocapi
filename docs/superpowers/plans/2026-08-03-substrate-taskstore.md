# Substrate-Backed TaskStore (`mocapi-tasks-substrate`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A new `mocapi-tasks-substrate` module providing a distributed, durable `TaskStore` backed by Substrate 0.8.0 Atoms (token CAS), drop-in via autoconfiguration.

**Architecture:** One `Atom<TaskRecord>` per task. `TaskStore.update` is a read → mutate → `compareAndSet` optimistic loop. TaskRecord expiry is absolute (`createdAt + ttl`); every atom write passes the *remaining* time so the backend lease dies at the original deadline, and the adapter's own `isExpired` check is the correctness gate. Autoconfig registers the store before `MocapiTasksAutoConfiguration` so the in-memory default backs off.

**Tech Stack:** Java 25, Spring Boot 4.0.5, Substrate 0.8.0 (`org.jwcarman.substrate`), codec 0.1.0 (`org.jwcarman.codec`, Jackson 3 / `tools.jackson`), Testcontainers + Redis 7, JUnit 5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-03-substrate-taskstore-design.md`

## Global Constraints

- **Never suppress warnings** — no `@SuppressWarnings` of any kind in this work (the `LegacyTitledEnumSchema` deprecation exception does not apply here).
- **No star imports** — explicit single-symbol imports everywhere, including static imports.
- Every new `.java` file carries the standard license header — after creating files, run `mvn -pl mocapi-tasks-substrate com.mycila:license-maven-plugin:format` (and `spotless:apply` for google-java-format) before committing.
- Zero new SonarCloud issues is the bar (James's standing rule) — self-check before each commit: no unused imports, no commented-out code, no TODO comments, no generic `RuntimeException` throws.
- Javadoc on all public types/members (release build runs doclint).
- 2-space indentation, google-java-format style (spotless enforces).
- Version floors are exact: `substrate.version=0.8.0`, `codec.version=0.1.0`. Do not use SNAPSHOT versions.

---

### Task 1: Build scaffolding (parent, BOM, module POM)

**Files:**
- Modify: `pom.xml` (parent — module entry, version properties, dependencyManagement)
- Modify: `mocapi-bom/pom.xml` (new managed artifact)
- Create: `mocapi-tasks-substrate/pom.xml`

**Interfaces:**
- Produces: an empty-but-building `mocapi-tasks-substrate` Maven module every later task compiles into. Dependency coordinates for all later tasks are fixed here.

- [ ] **Step 1: Parent POM — module + versions + dependencyManagement**

In `pom.xml`, add to `<modules>` right after `<module>mocapi-tasks</module>` (search for it; the tasks module is in the list around line 60-82):

```xml
        <module>mocapi-tasks-substrate</module>
```

In the `<!-- Dependency Versions -->` properties block (~line 91), add alphabetically:

```xml
        <codec.version>0.1.0</codec.version>
        <substrate.version>0.8.0</substrate.version>
```

In `<dependencyManagement>` (where `jmustache` is managed, ~line 184), add:

```xml
            <dependency>
                <groupId>org.jwcarman.substrate</groupId>
                <artifactId>substrate-bom</artifactId>
                <version>${substrate.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.jwcarman.codec</groupId>
                <artifactId>codec-jackson</artifactId>
                <version>${codec.version}</version>
            </dependency>
```

- [ ] **Step 2: BOM entry**

In `mocapi-bom/pom.xml`, next to the existing `mocapi-tasks` entry (follow the exact surrounding format — `com.callibrity.mocapi` group, `${project.version}`):

```xml
            <dependency>
                <groupId>com.callibrity.mocapi</groupId>
                <artifactId>mocapi-tasks-substrate</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 3: Module POM**

Create `mocapi-tasks-substrate/pom.xml`. Copy the license-comment header block from `mocapi-tasks/pom.xml` verbatim, then:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.callibrity.mocapi</groupId>
        <artifactId>mocapi-parent</artifactId>
        <version>1.3.0-SNAPSHOT</version>
    </parent>

    <artifactId>mocapi-tasks-substrate</artifactId>
    <name>Mocapi - Tasks - Substrate</name>
    <description>Distributed, durable TaskStore for the MCP Tasks extension, backed by
        Substrate Atoms (token compare-and-set) across any Substrate backend.</description>

    <dependencies>
        <dependency>
            <groupId>com.callibrity.mocapi</groupId>
            <artifactId>mocapi-tasks</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jwcarman.substrate</groupId>
            <artifactId>substrate-api</artifactId>
        </dependency>
        <dependency>
            <groupId>com.callibrity.mocapi</groupId>
            <artifactId>mocapi-autoconfigure</artifactId>
            <version>${project.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>com.callibrity.mocapi</groupId>
            <artifactId>mocapi-tasks</artifactId>
            <version>${project.version}</version>
            <type>test-jar</type>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.jwcarman.substrate</groupId>
            <artifactId>substrate-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.jwcarman.substrate</groupId>
            <artifactId>substrate-redis</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.jwcarman.codec</groupId>
            <artifactId>codec-jackson</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Notes: `substrate-api`/`substrate-core`/`substrate-redis` versions come from the imported `substrate-bom`; `testcontainers` from `spring-boot-dependencies`. The `mocapi-autoconfigure` optional dep exists solely for the typed `@AutoConfiguration(before = MocapiTasksAutoConfiguration.class)` reference in Task 4 (no cycle: `mocapi-autoconfigure` does not depend on this module).

- [ ] **Step 4: Verify the module builds and 0.8.0 resolves from Central**

Run: `mvn -pl mocapi-tasks-substrate verify`
Expected: BUILD SUCCESS, and the log shows `substrate-api-0.8.0.jar` being downloaded/resolved (this is the proof James's 0.8.0 release is actually on Central — if resolution fails, STOP and report; do not fall back to a SNAPSHOT).

- [ ] **Step 5: Commit**

```bash
git add pom.xml mocapi-bom/pom.xml mocapi-tasks-substrate/pom.xml
git commit -m "build: scaffold mocapi-tasks-substrate module (substrate-bom 0.8.0, codec-jackson 0.1.0)"
```

---

### Task 2: `SubstrateTaskStore` driven by the contract TCK (in-memory)

**Files:**
- Create: `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/SubstrateTaskStore.java`
- Test: `mocapi-tasks-substrate/src/test/java/com/callibrity/mocapi/tasks/substrate/SubstrateTaskStoreTest.java`

**Interfaces:**
- Consumes: `com.callibrity.mocapi.tasks.store.TaskStore` SPI, `TaskRecord` (has `isExpired(Instant)`, `taskId()`, `createdAt()`, `ttl()`), `TaskAlreadyExistsException(String taskId)`; Substrate `AtomFactory.create(String, Class, T, Duration)` / `connect(String, Class)`, `Atom.get()` → `Snapshot<T>(value, token)`, `Atom.compareAndSet(Snapshot, T, Duration)`, `Atom.delete()`, exceptions `AtomAlreadyExistsException`, `AtomNotFoundException`, `AtomExpiredException` (all in `org.jwcarman.substrate.atom`).
- Produces: `public SubstrateTaskStore(AtomFactory atomFactory, Clock clock, String keyPrefix)` implementing `TaskStore` — Task 4's autoconfig instantiates exactly this constructor; Tasks 3 and 7 reuse the test fixture pattern.

- [ ] **Step 1: Write the failing test — TCK subclass with an in-memory Substrate stack**

`SubstrateTaskStoreTest.java` (license header; the TCK base class brings all test methods):

```java
package com.callibrity.mocapi.tasks.substrate;

import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.mocapi.tasks.store.TaskStoreContractTest;
import java.time.Clock;
import java.time.Duration;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the {@link TaskStoreContractTest} TCK against {@link SubstrateTaskStore} on Substrate's
 * in-memory Atom SPI. Every write still round-trips through {@code codec-jackson} bytes, so
 * serialization is genuinely exercised.
 */
class SubstrateTaskStoreTest extends TaskStoreContractTest {

  @Override
  protected TaskStore newStore(Clock clock) {
    CodecFactory codecFactory = new JacksonCodecFactory(JsonMapper.builder().build());
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new InMemoryAtomSpi(),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(atomFactory, clock, "mocapi:tasks:");
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -pl mocapi-tasks-substrate test`
Expected: COMPILATION ERROR — `SubstrateTaskStore` does not exist.

- [ ] **Step 3: Implement `SubstrateTaskStore`**

```java
package com.callibrity.mocapi.tasks.substrate;

import com.callibrity.mocapi.tasks.store.TaskAlreadyExistsException;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jwcarman.substrate.atom.Atom;
import org.jwcarman.substrate.atom.AtomAlreadyExistsException;
import org.jwcarman.substrate.atom.AtomExpiredException;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.atom.AtomNotFoundException;
import org.jwcarman.substrate.atom.Snapshot;

/**
 * A {@link TaskStore} backed by one Substrate {@link Atom} per task, giving shared, durable task
 * state across every Substrate backend.
 *
 * <p><strong>Atomicity</strong> — {@link #update} is an optimistic read → mutate →
 * {@link Atom#compareAndSet} loop conditioned on the snapshot token; a lost race re-reads and
 * retries, which the {@link TaskStore} contract permits (mutations may run more than once).
 *
 * <p><strong>Expiry</strong> — a {@link TaskRecord}'s deadline is absolute
 * ({@code createdAt + ttl}), while an Atom's TTL is a lease that resets on every write. Every
 * write therefore passes the <em>remaining</em> time to the original deadline, so the backend
 * lease never outlives the record. Backend expiry is garbage collection only; the authoritative
 * gate is {@link TaskRecord#isExpired} against this store's {@link Clock}, checked on every read
 * and update (with an eager purge), which keeps behavior correct even when backend clocks drift
 * from the application clock.
 */
public class SubstrateTaskStore implements TaskStore {

  private final AtomFactory atomFactory;
  private final Clock clock;
  private final String keyPrefix;

  /**
   * @param atomFactory the Substrate atom factory (any backend)
   * @param clock the clock used for expiry decisions
   * @param keyPrefix prefix for backend atom keys, e.g. {@code "mocapi:tasks:"}
   */
  public SubstrateTaskStore(AtomFactory atomFactory, Clock clock, String keyPrefix) {
    this.atomFactory = atomFactory;
    this.clock = clock;
    this.keyPrefix = keyPrefix;
  }

  @Override
  public void create(TaskRecord rec) {
    Duration remaining = remaining(rec, clock.instant());
    if (remaining.isZero() || remaining.isNegative()) {
      return;
    }
    if (tryCreate(rec, remaining)) {
      return;
    }
    if (!incumbentIsExpired(rec.taskId())) {
      throw new TaskAlreadyExistsException(rec.taskId());
    }
    connect(rec.taskId()).delete();
    if (!tryCreate(rec, remaining)) {
      throw new TaskAlreadyExistsException(rec.taskId());
    }
  }

  @Override
  public Optional<TaskRecord> get(String taskId) {
    Atom<TaskRecord> atom = connect(taskId);
    try {
      TaskRecord current = atom.get().value();
      if (current.isExpired(clock.instant())) {
        atom.delete();
        return Optional.empty();
      }
      return Optional.of(current);
    } catch (AtomNotFoundException | AtomExpiredException e) {
      return Optional.empty();
    }
  }

  @Override
  public Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation) {
    Atom<TaskRecord> atom = connect(taskId);
    try {
      while (true) {
        Snapshot<TaskRecord> snapshot = atom.get();
        Instant now = clock.instant();
        if (snapshot.value().isExpired(now)) {
          atom.delete();
          return Optional.empty();
        }
        TaskRecord mutated = mutation.apply(snapshot.value());
        Duration remaining = remaining(mutated, now);
        if (remaining.isZero() || remaining.isNegative()) {
          atom.delete();
          return Optional.empty();
        }
        if (atom.compareAndSet(snapshot, mutated, remaining)) {
          return Optional.of(mutated);
        }
      }
    } catch (AtomNotFoundException | AtomExpiredException e) {
      return Optional.empty();
    }
  }

  @Override
  public void delete(String taskId) {
    connect(taskId).delete();
  }

  private boolean tryCreate(TaskRecord rec, Duration remaining) {
    try {
      atomFactory.create(keyPrefix + rec.taskId(), TaskRecord.class, rec, remaining);
      return true;
    } catch (AtomAlreadyExistsException e) {
      return false;
    }
  }

  private boolean incumbentIsExpired(String taskId) {
    try {
      return connect(taskId).get().value().isExpired(clock.instant());
    } catch (AtomNotFoundException | AtomExpiredException e) {
      return true;
    }
  }

  private Atom<TaskRecord> connect(String taskId) {
    return atomFactory.connect(keyPrefix + taskId, TaskRecord.class);
  }

  private static Duration remaining(TaskRecord rec, Instant now) {
    return Duration.between(now, rec.createdAt().plus(rec.ttl()));
  }
}
```

- [ ] **Step 4: Run the TCK to verify it passes**

Run: `mvn -pl mocapi-tasks-substrate test`
Expected: PASS — all 9 TCK tests green (`create_then_get_round_trips`, `create_collision_throws`, `get_unknown_returns_empty`, `update_unknown_returns_empty`, `expired_record_is_purged_on_get`, `expired_record_is_purged_on_update`, `concurrent_updates_are_applied_atomically` [800 contended CAS increments], `transitions_from_a_terminal_status_are_final`, `version_strictly_increases_across_transitions`, `delete_is_idempotent`).

- [ ] **Step 5: Format, header, commit**

```bash
mvn -pl mocapi-tasks-substrate spotless:apply com.mycila:license-maven-plugin:format
git add mocapi-tasks-substrate/src
git commit -m "feat(tasks): SubstrateTaskStore — Atom CAS-backed TaskStore passing the contract TCK"
```

---

### Task 3: Serialization round-trip richness test

**Files:**
- Test: `mocapi-tasks-substrate/src/test/java/com/callibrity/mocapi/tasks/substrate/TaskRecordRoundTripTest.java`

**Interfaces:**
- Consumes: `SubstrateTaskStore` (Task 2) and the same in-memory fixture; mocapi wire types `TextContent(String, Annotations)`, `CallToolResult(List<ContentBlock>, Boolean, JsonNode, String)`, `ElicitRequest(ElicitRequestParams)`, `ElicitRequestFormParams(String, RequestedSchema)`, `ElicitResult(ElicitAction, ObjectNode)`, `ResponseLedgerEntry(String, String, ElicitResult)`, ripcurl `JsonRpcErrorDetail(int, String)`.
- Produces: proof that a maximally populated `TaskRecord` survives codec-jackson bytes with field-by-field equality (record `equals`).

- [ ] **Step 1: Write the round-trip test**

```java
package com.callibrity.mocapi.tasks.substrate;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.InputRequest;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.atom.InMemoryAtomSpi;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Proves a maximally populated {@link TaskRecord} — polymorphic content blocks, sealed
 * {@link InputRequest} variants, ledger entries, error detail, and raw {@code JsonNode}
 * arguments — survives the codec-jackson byte round-trip with full equality.
 */
class TaskRecordRoundTripTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void maximallyPopulatedRecordRoundTrips() {
    TaskStore store = newStore();
    Instant createdAt = Instant.parse("2026-08-03T00:00:00Z");
    ObjectNode arguments = MAPPER.createObjectNode().put("city", "Cincinnati");
    ObjectNode answer = MAPPER.createObjectNode().put("confirmed", true);
    TaskRecord rec =
        new TaskRecord(
            "rt-1",
            "demo.tool",
            arguments,
            "user-1",
            "2026-07-28",
            null,
            TaskStatus.INPUT_REQUIRED,
            "waiting on slot-2",
            createdAt,
            createdAt.plusSeconds(5),
            Duration.ofHours(1),
            Duration.ofSeconds(2),
            List.of(
                new ResponseLedgerEntry(
                    "slot-1", "fp-1", new ElicitResult(ElicitAction.ACCEPT, answer)),
                new ResponseLedgerEntry("slot-2", "fp-2", null)),
            Map.of(
                "slot-2",
                new ElicitRequest(new ElicitRequestFormParams("Confirm the city", null))),
            new CallToolResult(List.of(new TextContent("done", null)), false, null, null),
            new JsonRpcErrorDetail(-32000, "boom"),
            3L);

    store.create(rec);

    assertThat(store.get("rt-1")).contains(rec);
  }

  private TaskStore newStore() {
    CodecFactory codecFactory = new JacksonCodecFactory(MAPPER);
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new InMemoryAtomSpi(),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(
        atomFactory, Clock.fixed(Instant.parse("2026-08-03T00:00:10Z"), ZoneOffset.UTC),
        "mocapi:tasks:");
  }
}
```

Note: a realistic record never carries both `result` and `error`; this test populates both anyway because the goal is field coverage, not state-machine realism. `clientCapabilities` stays null (shape is client-negotiated and covered by mocapi-model's own wire tests).

- [ ] **Step 2: Run the test**

Run: `mvn -pl mocapi-tasks-substrate test -Dtest=TaskRecordRoundTripTest`
Expected: PASS. If it FAILS on deserialization of a polymorphic type, that is a genuine finding the spec anticipated: mocapi's mapper customizations are required. In that case, identify the failing type from the stack trace, check how `mocapi-server` configures its `ObjectMapper` for that type (search `mocapi-server/src/main/java` for `JsonMapper.builder`), apply the same builder configuration to `MAPPER` in this test, and record the required configuration — Task 8's guide section must then document it. Do NOT weaken the assertion or drop fields to get to green.

- [ ] **Step 3: Commit**

```bash
git add mocapi-tasks-substrate/src/test
git commit -m "test(tasks): TaskRecord maximal-population round-trip through codec-jackson"
```

---

### Task 4: Autoconfiguration + properties

**Files:**
- Create: `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/MocapiTasksSubstrateProperties.java`
- Create: `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/MocapiTasksSubstrateAutoConfiguration.java`
- Create: `mocapi-tasks-substrate/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `mocapi-tasks-substrate/src/test/java/com/callibrity/mocapi/tasks/substrate/MocapiTasksSubstrateAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `SubstrateTaskStore(AtomFactory, Clock, String)` from Task 2; `com.callibrity.mocapi.tasks.MocapiTasksAutoConfiguration` (from optional `mocapi-autoconfigure` dep) for the `before =` ordering.
- Produces: auto-activated `TaskStore` bean when an `AtomFactory` bean exists; property `mocapi.tasks.substrate.key-prefix` (default `mocapi:tasks:`).

- [ ] **Step 1: Write the failing context-runner tests**

```java
package com.callibrity.mocapi.tasks.substrate;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.tasks.store.TaskStore;
import org.junit.jupiter.api.Test;
import org.jwcarman.codec.jackson.JacksonCodecAutoConfiguration;
import org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class MocapiTasksSubstrateAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class, () -> JsonMapper.builder().build())
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonCodecAutoConfiguration.class,
                  SubstrateAutoConfiguration.class,
                  MocapiTasksSubstrateAutoConfiguration.class));

  @Test
  void registersSubstrateTaskStoreWhenAtomFactoryPresent() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(TaskStore.class);
          assertThat(context.getBean(TaskStore.class)).isInstanceOf(SubstrateTaskStore.class);
        });
  }

  @Test
  void backsOffWhenUserSuppliesTaskStore() {
    TaskStore custom = new InMemoryStub();
    runner
        .withBean("customTaskStore", TaskStore.class, () -> custom)
        .run(context -> assertThat(context.getBean(TaskStore.class)).isSameAs(custom));
  }

  @Test
  void backsOffWithoutAtomFactory() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MocapiTasksSubstrateAutoConfiguration.class))
        .run(context -> assertThat(context).doesNotHaveBean(TaskStore.class));
  }

  @Test
  void keyPrefixPropertyIsApplied() {
    runner
        .withPropertyValues("mocapi.tasks.substrate.key-prefix=acme:jobs:")
        .run(
            context ->
                assertThat(context.getBean(MocapiTasksSubstrateProperties.class).keyPrefix())
                    .isEqualTo("acme:jobs:"));
  }

  private static final class InMemoryStub implements TaskStore {
    @Override
    public void create(com.callibrity.mocapi.tasks.store.TaskRecord rec) {}

    @Override
    public java.util.Optional<com.callibrity.mocapi.tasks.store.TaskRecord> get(String taskId) {
      return java.util.Optional.empty();
    }

    @Override
    public java.util.Optional<com.callibrity.mocapi.tasks.store.TaskRecord> update(
        String taskId,
        java.util.function.UnaryOperator<com.callibrity.mocapi.tasks.store.TaskRecord> mutation) {
      return java.util.Optional.empty();
    }

    @Override
    public void delete(String taskId) {}
  }
}
```

(Replace the inline fully-qualified names in `InMemoryStub` with normal imports — written inline here only for compactness; the No Star Imports rule still applies.)

- [ ] **Step 2: Run to verify failure**

Run: `mvn -pl mocapi-tasks-substrate test -Dtest=MocapiTasksSubstrateAutoConfigurationTest`
Expected: COMPILATION ERROR — autoconfiguration classes don't exist.

- [ ] **Step 3: Implement properties + autoconfiguration + imports file**

`MocapiTasksSubstrateProperties.java`:

```java
package com.callibrity.mocapi.tasks.substrate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Substrate-backed {@link com.callibrity.mocapi.tasks.store.TaskStore}.
 *
 * @param keyPrefix prefix applied to every backend atom key; defaults to {@code mocapi:tasks:}
 */
@ConfigurationProperties(prefix = "mocapi.tasks.substrate")
public record MocapiTasksSubstrateProperties(@DefaultValue("mocapi:tasks:") String keyPrefix) {}
```

`MocapiTasksSubstrateAutoConfiguration.java`:

```java
package com.callibrity.mocapi.tasks.substrate;

import com.callibrity.mocapi.tasks.MocapiTasksAutoConfiguration;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.store.TaskStore;
import java.time.Clock;
import org.jwcarman.substrate.atom.AtomFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-activates a {@link SubstrateTaskStore} when a Substrate {@link AtomFactory} bean is
 * present, replacing the in-memory default that {@link MocapiTasksAutoConfiguration} would
 * otherwise register ({@code before =} ensures this store wins; a user-defined {@link TaskStore}
 * bean still beats both). Runs after Substrate's own autoconfiguration (referenced by name to
 * keep {@code substrate-core} off the compile classpath) so the {@link AtomFactory} bean is
 * registered before the {@link ConditionalOnBean} condition is evaluated.
 */
@AutoConfiguration(
    before = MocapiTasksAutoConfiguration.class,
    afterName = "org.jwcarman.substrate.core.autoconfigure.SubstrateAutoConfiguration")
@ConditionalOnClass({AtomFactory.class, TaskExecutionEngine.class})
@EnableConfigurationProperties(MocapiTasksSubstrateProperties.class)
public class MocapiTasksSubstrateAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(MocapiTasksSubstrateAutoConfiguration.class);

  /** Mirrors the Clock default in {@code MocapiTasksAutoConfiguration}, which runs after us. */
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock mcpTasksClock() {
    return Clock.systemUTC();
  }

  /**
   * The Substrate-backed store. Shared and durable: safe for multi-node deployments, and
   * in-flight tasks survive a restart (subject to each record's TTL).
   */
  @Bean
  @ConditionalOnBean(AtomFactory.class)
  @ConditionalOnMissingBean(TaskStore.class)
  public SubstrateTaskStore mcpTaskStore(
      AtomFactory atomFactory, Clock clock, MocapiTasksSubstrateProperties properties) {
    log.info(
        "Using the Substrate-backed TaskStore (key prefix '{}'): task state is shared across "
            + "nodes and survives restarts.",
        properties.keyPrefix());
    return new SubstrateTaskStore(atomFactory, clock, properties.keyPrefix());
  }
}
```

(All imports above are verified against the source tree: `TaskExecutionEngine` is `com.callibrity.mocapi.tasks.engine.TaskExecutionEngine`.)

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.callibrity.mocapi.tasks.substrate.MocapiTasksSubstrateAutoConfiguration
```

- [ ] **Step 4: Run the tests**

Run: `mvn -pl mocapi-tasks-substrate test -Dtest=MocapiTasksSubstrateAutoConfigurationTest`
Expected: PASS (4 tests). Note `SubstrateAutoConfiguration` will log in-memory fallback WARNs in test output — expected, ignore.

- [ ] **Step 5: Format + commit**

```bash
mvn -pl mocapi-tasks-substrate spotless:apply com.mycila:license-maven-plugin:format
git add mocapi-tasks-substrate/src
git commit -m "feat(tasks): substrate TaskStore autoconfiguration with key-prefix property"
```

---

### Task 5: Native-image hints

**Files:**
- Create: `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/aot/SubstrateTaskStoreRuntimeHints.java`
- Create: `mocapi-tasks-substrate/src/main/resources/META-INF/spring/aot.factories`
- Test: `mocapi-tasks-substrate/src/test/java/com/callibrity/mocapi/tasks/substrate/aot/SubstrateTaskStoreRuntimeHintsTest.java`

**Interfaces:**
- Consumes: `TaskRecord` (store package), `ResponseLedgerEntry` (`com.callibrity.mocapi.server.mrtr`), ripcurl `JsonRpcErrorDetail`.
- Produces: GraalVM reflection hints for the serialized record graph, mirroring the extension-owns-its-hints pattern (`TasksRuntimeHints` precedent).

- [ ] **Step 1: Write the failing hints test**

```java
package com.callibrity.mocapi.tasks.substrate.aot;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

class SubstrateTaskStoreRuntimeHintsTest {

  @Test
  void registersBindingHintsForTheSerializedRecordGraph() {
    RuntimeHints hints = new RuntimeHints();
    new SubstrateTaskStoreRuntimeHints().registerHints(hints, getClass().getClassLoader());

    assertThat(RuntimeHintsPredicates.reflection().onType(TaskRecord.class)).accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(ResponseLedgerEntry.class))
        .accepts(hints);
    assertThat(RuntimeHintsPredicates.reflection().onType(JsonRpcErrorDetail.class))
        .accepts(hints);
  }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -pl mocapi-tasks-substrate test -Dtest=SubstrateTaskStoreRuntimeHintsTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Implement the registrar + aot.factories**

`SubstrateTaskStoreRuntimeHints.java`:

```java
package com.callibrity.mocapi.tasks.substrate.aot;

import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers Jackson binding hints for the record graph {@code SubstrateTaskStore} serializes
 * through codec-jackson: {@link TaskRecord} plus the referenced types that live outside packages
 * already hinted elsewhere. {@code mocapi-server}'s {@code MocapiRuntimeHints} covers {@code
 * com.callibrity.mocapi.model} (content blocks, elicitation types) and {@code mocapi-tasks}'
 * {@code TasksRuntimeHints} covers the tasks wire model — but {@link TaskRecord} (store package),
 * {@link ResponseLedgerEntry} (MRTR ledger), and ripcurl's {@link JsonRpcErrorDetail} are only
 * reachable via this module's serialization, so this module owns their hints (each extension
 * registers hints for what it alone makes reachable — the pattern ADR-0037 established).
 *
 * <p>{@link BindingReflectionHintsRegistrar} walks nested property types transitively, so
 * registering these roots also covers everything they reference.
 */
public class SubstrateTaskStoreRuntimeHints implements RuntimeHintsRegistrar {

  private static final BindingReflectionHintsRegistrar BINDING =
      new BindingReflectionHintsRegistrar();

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    BINDING.registerReflectionHints(
        hints.reflection(), TaskRecord.class, ResponseLedgerEntry.class, JsonRpcErrorDetail.class);
  }
}
```

`META-INF/spring/aot.factories`:

```
org.springframework.aot.hint.RuntimeHintsRegistrar=\
com.callibrity.mocapi.tasks.substrate.aot.SubstrateTaskStoreRuntimeHints
```

- [ ] **Step 4: Run the test**

Run: `mvn -pl mocapi-tasks-substrate test -Dtest=SubstrateTaskStoreRuntimeHintsTest`
Expected: PASS.

- [ ] **Step 5: Format + commit**

```bash
mvn -pl mocapi-tasks-substrate spotless:apply com.mycila:license-maven-plugin:format
git add mocapi-tasks-substrate/src
git commit -m "feat(tasks): native-image hints for the substrate-serialized TaskRecord graph"
```

---

### Task 6: Redis integration test (Testcontainers, failsafe)

**Files:**
- Modify: `mocapi-tasks-substrate/pom.xml` (failsafe binding)
- Test: `mocapi-tasks-substrate/src/test/java/com/callibrity/mocapi/tasks/substrate/RedisSubstrateTaskStoreIT.java`

**Interfaces:**
- Consumes: TCK + `SubstrateTaskStore`; `org.jwcarman.substrate.redis.atom.RedisAtomSpi(RedisCommands<String, String>, String prefix)`; Lettuce (`io.lettuce.core.RedisClient`, transitive via `substrate-redis`).
- Produces: `mvn verify` proof against a real backend. Isolation: each `newStore` call gets a unique SPI-level key prefix, because the TCK reuses task ids (`t1`, `counter`) across tests against one shared Redis.

- [ ] **Step 1: Bind failsafe in the module POM**

Add to `mocapi-tasks-substrate/pom.xml` (version comes from parent `pluginManagement`, `maven-failsafe-plugin.version=3.5.5`):

```xml
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

- [ ] **Step 2: Write the IT**

```java
package com.callibrity.mocapi.tasks.substrate;

import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.mocapi.tasks.store.TaskStoreContractTest;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.jwcarman.codec.jackson.JacksonCodecFactory;
import org.jwcarman.codec.spi.CodecFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.core.atom.DefaultAtomFactory;
import org.jwcarman.substrate.core.lifecycle.ShutdownCoordinator;
import org.jwcarman.substrate.core.memory.notifier.InMemoryNotifier;
import org.jwcarman.substrate.core.notifier.DefaultNotifier;
import org.jwcarman.substrate.core.transform.PayloadTransformer;
import org.jwcarman.substrate.redis.atom.RedisAtomSpi;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the full {@link TaskStoreContractTest} TCK against a real Redis via Substrate's
 * {@link RedisAtomSpi}. Each {@code newStore} call gets a unique SPI key prefix because the TCK
 * reuses task ids across tests and Redis state is shared for the whole class.
 */
class RedisSubstrateTaskStoreIT extends TaskStoreContractTest {

  private static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private static RedisClient client;
  private static RedisCommands<String, String> commands;

  @BeforeAll
  static void startRedis() {
    REDIS.start();
    client =
        RedisClient.create(
            RedisURI.builder()
                .withHost(REDIS.getHost())
                .withPort(REDIS.getFirstMappedPort())
                .build());
    commands = client.connect(StringCodec.UTF8).sync();
  }

  @AfterAll
  static void stopRedis() {
    if (client != null) {
      client.shutdown();
    }
    REDIS.stop();
  }

  @Override
  protected TaskStore newStore(Clock clock) {
    CodecFactory codecFactory = new JacksonCodecFactory(JsonMapper.builder().build());
    AtomFactory atomFactory =
        new DefaultAtomFactory(
            new RedisAtomSpi(commands, "tck:" + UUID.randomUUID() + ":"),
            codecFactory,
            PayloadTransformer.IDENTITY,
            new DefaultNotifier(new InMemoryNotifier(), codecFactory),
            Duration.ofDays(30),
            new ShutdownCoordinator());
    return new SubstrateTaskStore(atomFactory, clock, "mocapi:tasks:");
  }
}
```

(`RedisAtomSpi`'s package is verified against the Substrate source tree: `org.jwcarman.substrate.redis.atom.RedisAtomSpi`, constructor `(RedisCommands<String, String> commands, String prefix)`.)

- [ ] **Step 3: Run the IT (requires Docker)**

Run: `mvn -pl mocapi-tasks-substrate verify`
Expected: failsafe runs `RedisSubstrateTaskStoreIT`, all TCK tests PASS. The contended-counter test does 800 CAS round-trips against Redis — allow up to a minute.

- [ ] **Step 4: Format + commit**

```bash
mvn -pl mocapi-tasks-substrate spotless:apply com.mycila:license-maven-plugin:format
git add mocapi-tasks-substrate
git commit -m "test(tasks): Redis Testcontainers IT running the full TaskStore TCK via failsafe"
```

---

### Task 7: `examples/tasks` substrate profile + JVM + native verification

**Files:**
- Modify: `examples/tasks/pom.xml` (new `substrate` profile)

**Interfaces:**
- Consumes: everything shipped in Tasks 1–5.
- Produces: the spec's success criterion — adding the module + a Substrate backend swaps the active store with zero app-code changes — plus the empirical native-image run.

- [ ] **Step 1: Add the profile**

In `examples/tasks/pom.xml`, add alongside the existing `native` profile:

```xml
        <profile>
            <id>substrate</id>
            <dependencies>
                <dependency>
                    <groupId>com.callibrity.mocapi</groupId>
                    <artifactId>mocapi-tasks-substrate</artifactId>
                    <version>${project.version}</version>
                </dependency>
                <dependency>
                    <groupId>org.jwcarman.substrate</groupId>
                    <artifactId>substrate-core</artifactId>
                </dependency>
                <dependency>
                    <groupId>org.jwcarman.codec</groupId>
                    <artifactId>codec-jackson</artifactId>
                </dependency>
            </dependencies>
        </profile>
```

(`substrate-core` alone gives the in-memory Atom SPI fallback — fine for a single-node example; the point is proving the wiring, not clustering the demo. Substrate will WARN about the in-memory fallback; that's expected.)

If `examples/tasks/pom.xml` does not manage substrate versions (it inherits from `mocapi-parent`, so the `substrate-bom` import from Task 1 applies), the entries above need no `<version>` elements except the reactor one shown.

- [ ] **Step 2: JVM verification — the log line proves the swap**

```bash
mvn -pl examples/tasks -am -Psubstrate spring-boot:run
```

Expected in startup logs: `Using the Substrate-backed TaskStore (key prefix 'mocapi:tasks:')` — and NOT the in-memory TaskStore warning from `MocapiTasksAutoConfiguration`. Ctrl-C after verifying. If the in-memory warning appears instead, the autoconfig ordering is broken — STOP and fix Task 4 (check the imports file is on the classpath and `before =` references the right class).

- [ ] **Step 3: Native-image verification (empirical, per docs/design/native-image.md)**

Follow the verification procedure in `docs/design/native-image.md` (the `examples/tasks` section — Docker buildpacks build), adding the `substrate` profile:

```bash
mvn -pl examples/tasks -am -Psubstrate,native spring-boot:build-image -DskipTests
```

Then run the produced image and drive it with the task requests from the examples' verification flow in that doc (initialize → `tools/call` on the task tool → `tasks/get`), confirming: (a) the substrate log line appears, (b) a task completes end-to-end — which exercises TaskRecord serialization through codec-jackson under native reflection. If serialization fails only in native, the hints in Task 5 are incomplete: add the failing type to `SubstrateTaskStoreRuntimeHints`, rebuild, re-verify (this is exactly the twice-burned scenario the spec calls out — do not skip the native run).

- [ ] **Step 4: Commit**

```bash
git add examples/tasks/pom.xml
git commit -m "feat(examples): substrate profile for the tasks example — native-verified store swap"
```

---

### Task 8: ADR-0040 + design/guide docs + CHANGELOG

**Files:**
- Create: `docs/adr/0040-substrate-taskstore-adapter.md`
- Modify: `docs/adr/README.md` (index entry)
- Modify: `docs/design/tasks.md` (substrate store section)
- Modify: `docs/guides/tasks.md` (enablement how-to)
- Modify: `CHANGELOG.md` (Unreleased entry)

**Interfaces:**
- Consumes: the shipped implementation (Tasks 1–7) — cite real class/property names.
- Produces: governance artifacts the repo's ADR rule requires for a new module.

- [ ] **Step 1: Write ADR-0040**

Use `docs/adr/_template.md` structure (`# ADR-0040 — Substrate-backed TaskStore lives in mocapi as mocapi-tasks-substrate`; Status: Accepted; Date: 2026-08-03). Content requirements:

- **Context:** the 1.2.0 tasks design deferred a distributed store until Substrate had CAS on Atoms; Substrate 0.8.0 shipped token `compareAndSet` across all nine backends; the original note placed the adapter Substrate-side.
- **Decision:** the adapter is a mocapi reactor module (`mocapi-tasks-substrate`) depending only on `substrate-api`; one `Atom<TaskRecord>` per task; optimistic CAS loop; remaining-TTL mapping with the adapter-side `isExpired` check as the correctness gate (backend TTL is GC only); self-registered autoconfiguration ordered before `MocapiTasksAutoConfiguration`; module owns its native hints.
- **Consequences:** mocapi gains an optional third-party dependency (substrate-api) in one leaf module; the "adapter is Substrate-side" note from the 2026-08-02 tasks spec is superseded by this ADR; deployments must keep Substrate's `TtlBounds` max ≥ the largest task TTL; non-goals — Atom subscriptions (no push seam in the extension), additional backend ITs beyond Redis.
- **Code anchors:** `mocapi-tasks-substrate/src/main/java/com/callibrity/mocapi/tasks/substrate/SubstrateTaskStore.java`, `.../MocapiTasksSubstrateAutoConfiguration.java`.

Add the row to the index table in `docs/adr/README.md`, matching the existing format.

- [ ] **Step 2: Update `docs/design/tasks.md`**

Add a "Distributed store: mocapi-tasks-substrate" section after the existing TaskStore/InMemory discussion covering: the one-atom-per-task layout, the CAS update loop, the absolute-deadline vs. lease TTL reconciliation (remaining-time writes + adapter-side expiry gate), key prefix property, autoconfig activation/back-off order (user bean > substrate > in-memory), and the TCK + Redis IT verification story. Keep the design doc describing only what IS true — cite ADR-0040.

- [ ] **Step 3: Update `docs/guides/tasks.md`**

Add an "Using a distributed TaskStore (Substrate)" section: add `mocapi-tasks-substrate` + a Substrate backend module (e.g. `substrate-redis`) + `codec-jackson` to the app; Substrate autoconfigures `AtomFactory`; the store activates automatically (show the log line); `mocapi.tasks.substrate.key-prefix` property; the `TtlBounds` ≥ max task TTL requirement; note any ObjectMapper configuration Task 3 discovered was required (omit if none was).

- [ ] **Step 4: CHANGELOG**

Under `## [Unreleased]`, add an `### Added` section (create it if absent) with a `**Substrate-backed TaskStore (`mocapi-tasks-substrate`)**` bullet in the house style: what it is (distributed/durable TaskStore over Substrate Atoms via token CAS, any of the nine backends), drop-in activation, key-prefix property, ADR-0040 link.

- [ ] **Step 5: Commit**

```bash
git add docs/adr/0040-substrate-taskstore-adapter.md docs/adr/README.md docs/design/tasks.md docs/guides/tasks.md CHANGELOG.md
git commit -m "docs: ADR-0040 + design/guide/CHANGELOG for mocapi-tasks-substrate"
```

---

### Task 9: Full-reactor verification + house-rules self-check

**Files:** none new — verification only.

- [ ] **Step 1: Full reactor build**

Run: `mvn verify`
Expected: BUILD SUCCESS, every module green including the new module's unit tests and Redis IT.

- [ ] **Step 2: Release-profile javadoc (doclint)**

Run: `mvn -P release javadoc:jar -DskipTests`
Expected: BUILD SUCCESS — catches doclint errors in the new public javadoc that plain `verify` misses.

- [ ] **Step 3: House-rules self-check greps**

All must return empty:

```bash
grep -rn "@SuppressWarnings" mocapi-tasks-substrate/src
grep -rn "^import .*\*;" mocapi-tasks-substrate/src
grep -rn "TODO\|FIXME" mocapi-tasks-substrate/src
```

- [ ] **Step 4: Report**

Summarize for James: TCK results (in-memory + Redis), the JVM/native verification outcomes from Task 7, whether Task 3 uncovered any required mapper configuration, and that the reactor + javadoc builds are green. Do NOT push — James pushes when ready.
