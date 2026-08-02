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

import com.callibrity.mocapi.tasks.model.TaskStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Contract tests every {@link TaskStore} implementation must satisfy. Ships in the mocapi-tasks
 * test-jar so external store implementations can reuse it.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public abstract class TaskStoreContractTest {

  protected abstract TaskStore newStore(Clock clock);

  private TaskRecord newRecord(String taskId, Instant createdAt, Duration ttl) {
    return new TaskRecord(
        taskId,
        "demo.tool",
        null,
        "user-1",
        "2026-07-28",
        null,
        TaskStatus.WORKING,
        "0",
        createdAt,
        createdAt,
        ttl,
        Duration.ofSeconds(1),
        List.of(),
        Map.of(),
        null,
        null,
        0L);
  }

  @Test
  void create_then_get_round_trips() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    TaskRecord record = newRecord("t1", clock.instant(), Duration.ofMinutes(5));

    store.create(record);

    assertThat(store.get("t1")).contains(record);
  }

  @Test
  void create_collision_throws() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    TaskRecord record = newRecord("t1", clock.instant(), Duration.ofMinutes(5));
    store.create(record);

    assertThatThrownBy(() -> store.create(record)).isInstanceOf(TaskAlreadyExistsException.class);
  }

  @Test
  void get_unknown_returns_empty() {
    TaskStore store = newStore(MutableClock.at(Instant.parse("2026-08-02T00:00:00Z")));

    assertThat(store.get("does-not-exist")).isEmpty();
  }

  @Test
  void update_unknown_returns_empty() {
    TaskStore store = newStore(MutableClock.at(Instant.parse("2026-08-02T00:00:00Z")));

    assertThat(store.update("does-not-exist", r -> r.working(Instant.now()))).isEmpty();
  }

  @Test
  void expired_record_is_purged_on_get() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    TaskRecord record = newRecord("t1", clock.instant(), Duration.ofMinutes(5));
    store.create(record);

    clock.advance(Duration.ofMinutes(6));

    assertThat(store.get("t1")).isEmpty();
    // A subsequent create with the same id must succeed: the expired record was purged, not
    // merely hidden.
    store.create(newRecord("t1", clock.instant(), Duration.ofMinutes(5)));
    assertThat(store.get("t1")).isPresent();
  }

  @Test
  void expired_record_is_purged_on_update() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    TaskRecord record = newRecord("t1", clock.instant(), Duration.ofMinutes(5));
    store.create(record);

    clock.advance(Duration.ofMinutes(6));

    assertThat(store.update("t1", r -> r.working(clock.instant()))).isEmpty();
  }

  @Test
  void concurrent_updates_are_applied_atomically() throws InterruptedException {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    store.create(newRecord("counter", clock.instant(), Duration.ofHours(1)));

    int threadCount = 8;
    int incrementsPerThread = 100;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    try {
      for (int i = 0; i < threadCount; i++) {
        pool.submit(
            () -> {
              ready.countDown();
              awaitUninterruptibly(start);
              for (int j = 0; j < incrementsPerThread; j++) {
                store.update("counter", TaskStoreContractTest::incrementCounter);
              }
            });
      }
      ready.await();
      start.countDown();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    Optional<TaskRecord> finalRecord = store.get("counter");
    assertThat(finalRecord).isPresent();
    assertThat(Integer.parseInt(finalRecord.orElseThrow().statusMessage()))
        .isEqualTo(threadCount * incrementsPerThread);
  }

  private static TaskRecord incrementCounter(TaskRecord record) {
    int current = Integer.parseInt(record.statusMessage());
    return record.withStatusMessage(String.valueOf(current + 1), record.lastUpdatedAt());
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @Test
  void transitions_from_a_terminal_status_are_final() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    store.create(newRecord("t1", clock.instant(), Duration.ofHours(1)));

    store.update("t1", r -> r.completed(null, clock.instant()));
    Optional<TaskRecord> afterCancel = store.update("t1", r -> r.cancelled(clock.instant()));

    assertThat(afterCancel).isPresent();
    assertThat(afterCancel.orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);
  }

  @Test
  void version_strictly_increases_across_transitions() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    store.create(newRecord("t1", clock.instant(), Duration.ofHours(1)));

    long v0 = store.get("t1").orElseThrow().version();
    long v1 = store.update("t1", r -> r.working(clock.instant())).orElseThrow().version();
    long v2 =
        store
            .update("t1", r -> r.withStatusMessage("halfway", clock.instant()))
            .orElseThrow()
            .version();
    long v3 = store.update("t1", r -> r.completed(null, clock.instant())).orElseThrow().version();

    assertThat(v1).isGreaterThan(v0);
    assertThat(v2).isGreaterThan(v1);
    assertThat(v3).isGreaterThan(v2);
  }

  @Test
  void delete_is_idempotent() {
    MutableClock clock = MutableClock.at(Instant.parse("2026-08-02T00:00:00Z"));
    TaskStore store = newStore(clock);
    store.create(newRecord("t1", clock.instant(), Duration.ofHours(1)));

    store.delete("t1");
    store.delete("t1");

    assertThat(store.get("t1")).isEmpty();
  }

  /** A {@link Clock} whose {@link #instant()} can be advanced manually, for expiry tests. */
  protected static final class MutableClock extends Clock {
    private Instant instant;
    private final ZoneId zone;

    private MutableClock(Instant instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    static MutableClock at(Instant instant) {
      return new MutableClock(instant, ZoneOffset.UTC);
    }

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
