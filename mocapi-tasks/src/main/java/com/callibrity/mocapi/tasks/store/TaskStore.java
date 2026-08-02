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

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Durable storage SPI for {@link TaskRecord}s backing the {@code io.modelcontextprotocol/tasks}
 * extension. Implementations may be in-memory, database-backed, or distributed; the contract is
 * exercised uniformly by {@link TaskStoreContractTest}.
 */
public interface TaskStore {

  /**
   * Durably creates the record; MUST NOT return before a subsequent {@link #get(String)} would find
   * it.
   *
   * @throws TaskAlreadyExistsException on {@code taskId} collision
   */
  void create(TaskRecord rec);

  /** Empty if unknown OR expired ({@code createdAt + ttl} before now). */
  Optional<TaskRecord> get(String taskId);

  /**
   * Applies {@code mutation} atomically against the current record; returns the post-mutation
   * record, or empty if unknown/expired. The mutation function MUST be deterministic and
   * side-effect-free: implementations MAY invoke it more than once (optimistic retry); only the
   * final invocation's result is stored. Returning the input unchanged is a no-op.
   */
  Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation);

  /** Idempotent. */
  void delete(String taskId);
}
