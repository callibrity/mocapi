/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.tasks.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.callibrity.mocapi.model.BooleanSchema;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.LegacyTitledEnumSchema;
import com.callibrity.mocapi.model.NumberSchema;
import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.RequestedSchema;
import com.callibrity.mocapi.model.StringSchema;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InMemoryTaskStoreTest extends TaskStoreContractTest {

  @Override
  protected TaskStore newStore(Clock clock) {
    return new InMemoryTaskStore(clock, Duration.ofDays(1));
  }

  @Test
  void single_arg_constructor_uses_the_default_sweep_interval() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock)) {
      assertThat(store.size()).isZero();
    }
  }

  @Test
  void sweep_loop_exits_immediately_when_the_thread_is_already_interrupted() throws Exception {
    // The sweeper's while-condition false branch is only reachable when the running thread's
    // interrupt flag is already set before the loop's first check — in production that happens
    // via close()'s sweeper.interrupt() racing the loop, which is not deterministically testable.
    // Driving the private sweep() method directly on a pre-interrupted current thread exercises
    // the same condition deterministically without waiting on Thread.sleep to throw.
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofHours(1))) {
      Method sweep = InMemoryTaskStore.class.getDeclaredMethod("sweep", Duration.class);
      sweep.setAccessible(true);
      Thread.currentThread().interrupt();
      try {
        sweep.invoke(store, Duration.ofMillis(1));
      } finally {
        assertThat(Thread.interrupted()).isTrue(); // clears the flag for subsequent tests
      }
    }
  }

  @Test
  void sweeper_physically_removes_expired_records_without_any_get() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofMillis(20))) {
      store.create(
          new TaskRecord(
              "t1",
              "demo.tool",
              null,
              "user-1",
              "2026-07-28",
              null,
              TaskStatus.WORKING,
              "0",
              clock.instant(),
              clock.instant(),
              Duration.ofMillis(50),
              Duration.ofSeconds(1),
              List.of(),
              Map.of(),
              null,
              null,
              0L));
      assertThat(store.size()).isEqualTo(1);

      clock.advance(Duration.ofMinutes(5));

      await()
          .atMost(Duration.ofSeconds(2))
          .pollInterval(Duration.ofMillis(10))
          .until(() -> store.size() == 0);

      assertThat(store.size()).isZero();
    }
  }

  @Test
  void stored_records_are_not_aliased_with_the_caller_or_the_returned_copy() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    JsonMapper mapper = JsonMapper.builder().build();
    ObjectNode arguments = mapper.createObjectNode().put("city", "Cincinnati");
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofDays(1))) {
      TaskRecord rec =
          new TaskRecord(
              "t1",
              "demo.tool",
              arguments,
              "user-1",
              "2026-07-28",
              null,
              TaskStatus.WORKING,
              "0",
              clock.instant(),
              clock.instant(),
              Duration.ofHours(1),
              Duration.ofSeconds(1),
              List.of(),
              Map.of(),
              null,
              null,
              0L);
      store.create(rec);

      // Mutating the ORIGINAL node after create() must not affect what's stored.
      arguments.put("city", "Columbus");
      assertThat(store.get("t1").orElseThrow().arguments().get("city").asString())
          .isEqualTo("Cincinnati");

      // Mutating the RETURNED record's node must not affect a subsequent get().
      TaskRecord returned = store.get("t1").orElseThrow();
      ((ObjectNode) returned.arguments()).put("city", "Dayton");
      assertThat(store.get("t1").orElseThrow().arguments().get("city").asString())
          .isEqualTo("Cincinnati");
    }
  }

  @Test
  @SuppressWarnings(
      "deprecation") // Exercises the deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility (docs/plans/2026-07-28-schema.ts) — the "type":"string" collision case that
  // PrimitiveSchemaDefinitionDeserializer must route correctly.
  void a_fully_populated_requested_schema_round_trips_through_the_in_memory_store() {
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    Map<String, PrimitiveSchemaDefinition> properties = new LinkedHashMap<>();
    properties.put("city", new StringSchema("City", "Destination city", null, null, null, null));
    properties.put("travelers", new NumberSchema("integer", "Travelers", null, 1, 10, 1));
    properties.put("confirmed", new BooleanSchema("Confirmed", null, null));
    properties.put(
        "status",
        new LegacyTitledEnumSchema(
            "Status", null, List.of("pending", "approved"), List.of("Pending", "Approved"), null));
    RequestedSchema requestedSchema = new RequestedSchema(properties, List.of("city"), null);

    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofDays(1))) {
      TaskRecord rec =
          new TaskRecord(
              "t1",
              "demo.tool",
              null,
              "user-1",
              "2026-07-28",
              null,
              TaskStatus.INPUT_REQUIRED,
              "waiting",
              clock.instant(),
              clock.instant(),
              Duration.ofHours(1),
              Duration.ofSeconds(1),
              List.of(),
              Map.of(
                  "slot-1",
                  new ElicitRequest(new ElicitRequestFormParams("Confirm", requestedSchema))),
              null,
              null,
              0L);
      store.create(rec);

      assertThat(store.get("t1")).contains(rec);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void sweeper_survives_a_malformed_entry_and_keeps_sweeping_other_expired_records()
      throws Exception {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofMillis(20))) {
      store.create(
          new TaskRecord(
              "good",
              "demo.tool",
              null,
              "user-1",
              "2026-07-28",
              null,
              TaskStatus.WORKING,
              "0",
              clock.instant(),
              clock.instant(),
              Duration.ofMillis(50),
              Duration.ofSeconds(1),
              List.of(),
              Map.of(),
              null,
              null,
              0L));

      // Directly inject a malformed entry the public API has no way to produce, simulating
      // corruption, to prove one bad entry can't kill the sweeper thread for every other record.
      Field recordsField = InMemoryTaskStore.class.getDeclaredField("records");
      recordsField.setAccessible(true);
      ((ConcurrentHashMap<String, String>) recordsField.get(store))
          .put("malformed", "not valid json");
      assertThat(store.size()).isEqualTo(2);

      clock.advance(Duration.ofMinutes(5));

      await()
          .atMost(Duration.ofSeconds(2))
          .pollInterval(Duration.ofMillis(10))
          .until(() -> store.size() == 1);

      // The malformed entry survives (deserialization failure is treated as "not expired", not
      // silently dropped or, worse, allowed to kill the sweeper thread); the good, now-expired
      // entry is gone.
      assertThat(store.size()).isEqualTo(1);
    }
  }

  @Test
  void update_with_a_mutation_that_returns_null_throws_instead_of_corrupting_the_store() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    try (InMemoryTaskStore store = new InMemoryTaskStore(clock, Duration.ofDays(1))) {
      store.create(
          new TaskRecord(
              "t1",
              "demo.tool",
              null,
              "user-1",
              "2026-07-28",
              null,
              TaskStatus.WORKING,
              "0",
              clock.instant(),
              clock.instant(),
              Duration.ofHours(1),
              Duration.ofSeconds(1),
              List.of(),
              Map.of(),
              null,
              null,
              0L));

      assertThatThrownBy(() -> store.update("t1", _ -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("mutation must not return null");

      // The record is untouched by the failed update.
      assertThat(store.get("t1")).isPresent();
    }
  }

  /** A {@link Clock} whose {@link #instant()} can be advanced manually. */
  private static final class MutableClock extends Clock {
    private volatile Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    static MutableClock at(Instant instant) {
      return new MutableClock(instant);
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
