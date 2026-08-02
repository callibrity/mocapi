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
package com.callibrity.mocapi.tasks.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.InMemoryTaskStore;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** Tests for {@link TaskProgressSource} — progress emitters routed to a task's statusMessage. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskProgressSourceTest {

  private final Instant baseTime = Instant.parse("2026-08-02T00:00:00Z");
  private final Clock fixedClock = Clock.fixed(baseTime, ZoneOffset.UTC);

  private TaskRecord newRecord(String taskId, TaskStatus status) {
    return new TaskRecord(
        taskId,
        "demo.tool",
        null,
        "user-1",
        "2026-07-28",
        null,
        status,
        null,
        baseTime,
        baseTime,
        Duration.ofMinutes(5),
        Duration.ofSeconds(1),
        List.of(),
        Map.of(),
        null,
        null,
        0L);
  }

  @Test
  void longProgress_with_total_formats_correctly() {
    TaskStore store = new InMemoryTaskStore(fixedClock, Duration.ofHours(1));
    TaskRecord rec = newRecord("t1", TaskStatus.WORKING);
    store.create(rec);

    McpProgressSource source = TaskProgressSource.forTask(store, "t1", fixedClock);
    source.longProgress(100L).emit(42, "resizing");

    TaskRecord updated = store.get("t1").orElseThrow();
    assertThat(updated.statusMessage()).isEqualTo("42/100: resizing");
    assertThat(updated.lastUpdatedAt()).isEqualTo(baseTime);
  }

  @Test
  void countingProgress_without_total_increments() {
    TaskStore store = new InMemoryTaskStore(fixedClock, Duration.ofHours(1));
    TaskRecord rec = newRecord("t1", TaskStatus.WORKING);
    store.create(rec);

    McpProgressSource source = TaskProgressSource.forTask(store, "t1", fixedClock);
    var counter = source.countingProgress(null);
    counter.emit();
    counter.emit();

    TaskRecord updated = store.get("t1").orElseThrow();
    assertThat(updated.statusMessage()).isEqualTo("2");
  }

  @Test
  void emit_against_cancelled_record_leaves_status_untouched() {
    TaskStore store = new InMemoryTaskStore(fixedClock, Duration.ofHours(1));
    TaskRecord rec = newRecord("t1", TaskStatus.CANCELLED);
    store.create(rec);

    McpProgressSource source = TaskProgressSource.forTask(store, "t1", fixedClock);
    source.longProgress(100L).emit(42, "resizing");

    TaskRecord updated = store.get("t1").orElseThrow();
    assertThat(updated.status()).isEqualTo(TaskStatus.CANCELLED);
    assertThat(updated.statusMessage()).isNull();
  }

  @Test
  void non_monotonic_emit_throws() {
    TaskStore store = new InMemoryTaskStore(fixedClock, Duration.ofHours(1));
    TaskRecord rec = newRecord("t1", TaskStatus.WORKING);
    store.create(rec);

    McpProgressSource source = TaskProgressSource.forTask(store, "t1", fixedClock);
    var emitter = source.longProgress(100L);
    emitter.emit(42);

    assertThatThrownBy(() -> emitter.emit(40)).isInstanceOf(IllegalArgumentException.class);
  }
}
