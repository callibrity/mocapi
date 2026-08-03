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
 * <p><strong>Atomicity</strong> — {@link #update} is an optimistic read → mutate → {@link
 * Atom#compareAndSet} loop conditioned on the snapshot token; a lost race re-reads and retries,
 * which the {@link TaskStore} contract permits (mutations may run more than once).
 *
 * <p><strong>Expiry</strong> — a {@link TaskRecord}'s deadline is absolute ({@code createdAt +
 * ttl}), while an Atom's TTL is a lease that resets on every write. Every write therefore passes
 * the <em>remaining</em> time to the original deadline, so the backend lease never outlives the
 * record. Backend expiry is garbage collection only; the authoritative gate is {@link
 * TaskRecord#isExpired} against this store's {@link Clock}, checked on every read and update (with
 * an eager purge), which keeps behavior correct even when backend clocks drift from the application
 * clock.
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
