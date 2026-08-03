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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * In-memory {@link TaskStore}, suitable for single-node deployments and tests. Expired entries are
 * removed lazily on {@link #get(String)}/{@link #update(String, UnaryOperator)} and proactively by
 * a background sweeper virtual thread.
 */
public class InMemoryTaskStore implements TaskStore, AutoCloseable {

  private static final Duration DEFAULT_SWEEP_INTERVAL = Duration.ofSeconds(30);

  private final ConcurrentHashMap<String, TaskRecord> records = new ConcurrentHashMap<>();
  private final Clock clock;
  private final Thread sweeper;

  public InMemoryTaskStore(Clock clock) {
    this(clock, DEFAULT_SWEEP_INTERVAL);
  }

  public InMemoryTaskStore(Clock clock, Duration sweepInterval) {
    this.clock = clock;
    this.sweeper =
        Thread.ofVirtual().name("mocapi-tasks-sweeper").start(() -> sweep(sweepInterval));
  }

  private void sweep(Duration sweepInterval) {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        Thread.sleep(sweepInterval);
        Instant now = clock.instant();
        records.values().removeIf(rec -> rec.isExpired(now));
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void create(TaskRecord rec) {
    TaskRecord prior = records.putIfAbsent(rec.taskId(), rec);
    if (prior != null) {
      throw new TaskAlreadyExistsException(rec.taskId());
    }
  }

  @Override
  public Optional<TaskRecord> get(String taskId) {
    TaskRecord rec = records.get(taskId);
    if (rec == null) {
      return Optional.empty();
    }
    if (rec.isExpired(clock.instant())) {
      records.remove(taskId, rec);
      return Optional.empty();
    }
    return Optional.of(rec);
  }

  @Override
  public Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation) {
    Instant now = clock.instant();
    TaskRecord updated =
        records.compute(
            taskId,
            (id, current) ->
                current == null || current.isExpired(now) ? null : mutation.apply(current));
    return Optional.ofNullable(updated);
  }

  @Override
  public void delete(String taskId) {
    records.remove(taskId);
  }

  /** Number of records currently held, including any not-yet-swept expired entries. */
  public int size() {
    return records.size();
  }

  @Override
  public void close() {
    sweeper.interrupt();
  }
}
