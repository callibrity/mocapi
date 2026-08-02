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
package com.callibrity.mocapi.tasks;

import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.InputResponse;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.model.CancelTaskParams;
import com.callibrity.mocapi.tasks.model.CancelTaskResult;
import com.callibrity.mocapi.tasks.model.GetTaskParams;
import com.callibrity.mocapi.tasks.model.GetTaskResult;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.model.UpdateTaskParams;
import com.callibrity.mocapi.tasks.model.UpdateTaskResult;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethod;
import com.callibrity.ripcurl.core.annotation.JsonRpcParams;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@code io.modelcontextprotocol/tasks} extension's three JSON-RPC methods: {@code tasks/get},
 * {@code tasks/update}, and {@code tasks/cancel}. Every method first requires the caller to have
 * declared the {@code io.modelcontextprotocol/tasks} capability (SEP-2575 "Missing Required
 * Capabilities"; SEP-2663 extends the gate to the whole {@code tasks/*} namespace, not just
 * {@code @McpTask(required = true)} tools) — a non-capable caller gets {@code -32021} regardless of
 * whether the referenced task exists. Once past that gate, every method binds the lookup to the
 * requesting principal ({@link McpPrincipalSource}) — an unknown, expired, or foreign-owned task
 * all report the identical {@code "Unknown task"} {@code -32602} error, so a capable caller cannot
 * distinguish "doesn't exist" from "not yours" (spec §7.6).
 */
public class McpTasksService {

  private final TaskStore store;
  private final TaskExecutionEngine engine;
  private final McpPrincipalSource principalSource;
  private final Clock clock;

  public McpTasksService(
      TaskStore store,
      TaskExecutionEngine engine,
      McpPrincipalSource principalSource,
      Clock clock) {
    this.store = store;
    this.engine = engine;
    this.principalSource = principalSource;
    this.clock = clock;
  }

  @JsonRpcMethod(TasksExtension.TASKS_GET)
  public GetTaskResult getTask(@JsonRpcParams GetTaskParams params) {
    requireTaskCapable(params.meta(), TasksExtension.TASKS_GET);
    TaskRecord record = requireOwned(params.taskId());
    return new GetTaskResult(
        record.taskId(),
        record.status(),
        record.statusMessage(),
        record.createdAt().toString(),
        record.lastUpdatedAt().toString(),
        record.ttl().toMillis(),
        record.pollInterval().toMillis(),
        record.status() == TaskStatus.INPUT_REQUIRED ? record.inputRequests() : null,
        record.status() == TaskStatus.COMPLETED ? record.result() : null,
        record.status() == TaskStatus.FAILED ? record.error() : null,
        ResultTypes.COMPLETE);
  }

  @JsonRpcMethod(TasksExtension.TASKS_UPDATE)
  public UpdateTaskResult updateTask(@JsonRpcParams UpdateTaskParams params) {
    requireTaskCapable(params.meta(), TasksExtension.TASKS_UPDATE);
    requireOwned(params.taskId());
    Map<String, InputResponse> responses =
        params.inputResponses() != null ? params.inputResponses() : Map.of();
    // Reset every mutation invocation: TaskStore.update may re-invoke the mutation function
    // (optimistic retry), and only the final invocation's result is stored — flipped must reflect
    // that final attempt, not an earlier one the store discarded.
    var flipped = new AtomicBoolean();
    // Hoisted outside the mutation lambda: TaskStore.update's contract requires the mutation to be
    // deterministic since it may be re-invoked (optimistic retry). Calling clock.instant() from
    // inside the lambda would return a different value on each retry/each call site.
    Instant now = clock.instant();
    store.update(
        params.taskId(),
        r -> {
          flipped.set(false);
          if (r.status() != TaskStatus.INPUT_REQUIRED) {
            return r; // ignore per spec SHOULD: keys not outstanding / duplicate update
          }
          List<ResponseLedgerEntry> merged =
              mergeResponses(r.ledger(), r.inputRequests(), responses);
          if (merged == null) {
            return r; // nothing outstanding was answered — no flip
          }
          flipped.set(true);
          return r.withLedger(merged, now).working(now);
        });
    if (flipped.get()) {
      engine.resume(params.taskId());
    }
    return new UpdateTaskResult(ResultTypes.COMPLETE);
  }

  @JsonRpcMethod(TasksExtension.TASKS_CANCEL)
  public CancelTaskResult cancelTask(@JsonRpcParams CancelTaskParams params) {
    requireTaskCapable(params.meta(), TasksExtension.TASKS_CANCEL);
    requireOwned(params.taskId());
    Instant now = clock.instant();
    store.update(params.taskId(), r -> r.cancelled(now));
    return new CancelTaskResult(ResultTypes.COMPLETE);
  }

  /**
   * Gates every {@code tasks/*} method on the {@code io.modelcontextprotocol/tasks} capability
   * (SEP-2575/SEP-2663), independent of task existence or ownership — checked first so a
   * non-capable caller always sees {@code -32021}, never a task-existence signal.
   */
  private static void requireTaskCapable(RequestMeta meta, String method) {
    if (!TaskToolCallDispatcher.isTaskCapable(meta)) {
      throw new McpTaskRequiredException("Method \"" + method + "\"");
    }
  }

  private TaskRecord requireOwned(String taskId) {
    return store
        .get(taskId)
        .filter(record -> Objects.equals(record.principal(), principalSource.currentPrincipal()))
        .orElseThrow(() -> new JsonRpcException(JsonRpcProtocol.INVALID_PARAMS, "Unknown task"));
  }

  /**
   * Answers each outstanding ledger entry whose key is present in {@code responses} with an {@link
   * ElicitResult}, ignoring unknown keys and non-{@code ElicitResult} responses (spec SHOULD).
   * Returns {@code null} if nothing outstanding was answered.
   */
  private static List<ResponseLedgerEntry> mergeResponses(
      List<ResponseLedgerEntry> ledger,
      Map<String, ?> outstanding,
      Map<String, InputResponse> responses) {
    boolean answeredAny = false;
    List<ResponseLedgerEntry> merged = new ArrayList<>(ledger.size());
    for (ResponseLedgerEntry entry : ledger) {
      InputResponse response = responses.get(entry.key());
      if (!entry.isAnswered()
          && outstanding.containsKey(entry.key())
          && response instanceof ElicitResult elicitResult) {
        merged.add(entry.answeredWith(elicitResult));
        answeredAny = true;
      } else {
        merged.add(entry);
      }
    }
    return answeredAny ? merged : null;
  }
}
