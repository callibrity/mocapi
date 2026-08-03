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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * In-memory {@link TaskStore}, suitable for single-node deployments and tests. Expired entries are
 * removed lazily on {@link #get(String)}/{@link #update(String, UnaryOperator)} and proactively by
 * a background sweeper virtual thread.
 *
 * <p>Records are held as serialized JSON strings, not live {@link TaskRecord} object graphs. This
 * is deliberate, not an optimization opportunity to "fix": (1) it gives this store parity with
 * every external store (Redis, a database, Substrate) that necessarily serializes, so a bug in
 * {@code TaskRecord}'s wire representation — such as the missing {@code PrimitiveSchemaDefinition}
 * deserialization routing this store rework accompanies — is caught by the in-memory path too,
 * instead of only surfacing against a real backing store; and (2) {@link TaskRecord#arguments()}
 * and other fields carry mutable {@code JsonNode}s, and holding live references would let a caller
 * mutate a node after {@link #create(TaskRecord)} and silently corrupt the stored state, or mutate
 * a {@link #get(String)}-returned record's node and corrupt what a subsequent {@code get} returns.
 * Serializing on write and deserializing on every read make that aliasing impossible: every {@link
 * #get(String)}/{@link #update(String, UnaryOperator)} result is a fresh instance. One consequence
 * of round-tripping through JSON is that read-back fidelity is bounded by wire round-trip fidelity:
 * for example a {@code LegacyTitledEnumSchema} stored without {@code enumNames} legitimately comes
 * back as an {@code UntitledSingleSelectEnumSchema} on the next {@code get}, because the two are
 * indistinguishable on the wire (see {@code PrimitiveSchemaDefinitionDeserializer}).
 */
public class InMemoryTaskStore implements TaskStore, AutoCloseable {

  private static final Duration DEFAULT_SWEEP_INTERVAL = Duration.ofSeconds(30);
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final Logger log = LoggerFactory.getLogger(InMemoryTaskStore.class);
  private final ConcurrentHashMap<String, String> records = new ConcurrentHashMap<>();
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
        sweepOnce();
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Removes expired entries for one sweep pass. A malformed entry that fails to deserialize is
   * logged and skipped rather than allowed to escape and kill the sweeper thread — otherwise a
   * single bad record would silently stop all future expiry sweeps for the life of the store.
   */
  private void sweepOnce() {
    Instant now = clock.instant();
    records
        .entrySet()
        .removeIf(
            entry -> {
              try {
                return deserialize(entry.getValue()).isExpired(now);
              } catch (RuntimeException e) {
                log.warn(
                    "Skipping sweep of task {}: failed to deserialize stored record",
                    entry.getKey(),
                    e);
                return false;
              }
            });
  }

  @Override
  public void create(TaskRecord rec) {
    String prior = records.putIfAbsent(rec.taskId(), serialize(rec));
    if (prior != null) {
      throw new TaskAlreadyExistsException(rec.taskId());
    }
  }

  @Override
  public Optional<TaskRecord> get(String taskId) {
    String json = records.get(taskId);
    if (json == null) {
      return Optional.empty();
    }
    TaskRecord rec = deserialize(json);
    if (rec.isExpired(clock.instant())) {
      records.remove(taskId, json);
      return Optional.empty();
    }
    return Optional.of(rec);
  }

  @Override
  public Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation) {
    Instant now = clock.instant();
    AtomicReference<TaskRecord> mutated = new AtomicReference<>();
    records.compute(
        taskId,
        (id, currentJson) -> {
          if (currentJson == null) {
            return null;
          }
          TaskRecord current = deserialize(currentJson);
          if (current.isExpired(now)) {
            return null;
          }
          TaskRecord result =
              Objects.requireNonNull(mutation.apply(current), "mutation must not return null");
          mutated.set(result);
          return serialize(result);
        });
    return Optional.ofNullable(mutated.get());
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

  private static String serialize(TaskRecord rec) {
    return MAPPER.writeValueAsString(rec);
  }

  private static TaskRecord deserialize(String json) {
    return MAPPER.readValue(json, TaskRecord.class);
  }
}
