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

import com.callibrity.mocapi.api.progress.McpProgressSource;
import com.callibrity.mocapi.server.exchange.McpExchange;
import com.callibrity.mocapi.server.mrtr.ElicitationLedgerMismatchException;
import com.callibrity.mocapi.server.tools.ToolCallReplayInvoker;
import com.callibrity.mocapi.tasks.TasksExtension;
import com.callibrity.mocapi.tasks.model.CreateTaskResult;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import java.time.Clock;

/**
 * Runs {@code @McpTask}-annotated tool calls to completion off the {@code tools/call} dispatch
 * thread, writing every outcome (completion, input-required unwind, or failure) back through {@link
 * TaskStore} mutations.
 *
 * <p>Each execution runs on its own virtual thread, snapshot-wrapped via {@link
 * ContextSnapshotFactory} so context-propagated state (tracing, MDC, etc.) survives the thread hop
 * — the same pattern {@code StreamableHttpController.handleCall} uses for the synchronous dispatch
 * path.
 *
 * <p>All terminal writes go through {@link TaskRecord}'s transition helpers, which no-op once the
 * record has already gone terminal. That makes "cancelled sticks, output discarded" (spec §7.4)
 * fall out for free: if {@code tasks/cancel} wins the race and marks the record {@code CANCELLED}
 * before this engine's {@code run} loop finishes, the subsequent {@code completed}/{@code
 * failed}/{@code inputRequired} call is a no-op.
 */
public class TaskExecutionEngine {

  private final TaskStore store;
  private final ToolCallReplayInvoker invoker;
  private final ContextSnapshotFactory snapshotFactory;
  private final Clock clock;

  public TaskExecutionEngine(
      TaskStore store,
      ToolCallReplayInvoker invoker,
      ContextSnapshotFactory snapshotFactory,
      Clock clock) {
    this.store = store;
    this.invoker = invoker;
    this.snapshotFactory = snapshotFactory;
    this.clock = clock;
  }

  /**
   * Durably creates the record, spawns execution #1 on a snapshot-wrapped virtual thread, and
   * returns the {@link CreateTaskResult} acknowledging the task's creation.
   */
  public CreateTaskResult createAndStart(TaskRecord record) {
    store.create(record);
    spawn(record.taskId());
    return toCreateTaskResult(record);
  }

  /**
   * Spawns the next execution (post-{@code tasks/update} flip). The caller has already flipped
   * {@code input_required} to {@code working} and merged the ledger.
   */
  public void resume(String taskId) {
    spawn(taskId);
  }

  private void spawn(String taskId) {
    ContextSnapshot snapshot = snapshotFactory.captureAll();
    Thread.ofVirtual().name("mocapi-task-" + taskId).start(snapshot.wrap(() -> run(taskId)));
  }

  private void run(String taskId) {
    TaskRecord record = store.get(taskId).orElse(null);
    if (record == null || record.status() != TaskStatus.WORKING) {
      return; // expired, deleted, or already terminal (e.g. cancel won before we started)
    }
    McpExchange exchange =
        new McpExchange(record.protocolVersion(), null, record.clientCapabilities());
    McpProgressSource progress = TaskProgressSource.forTask(store, taskId, clock);
    try {
      var outcome =
          invoker.invoke(
              record.toolName(), record.arguments(), record.ledger(), progress, exchange);
      switch (outcome) {
        case ToolCallReplayInvoker.Outcome.Completed c ->
            store.update(taskId, r -> r.completed(c.result(), clock.instant()));
        case ToolCallReplayInvoker.Outcome.InputRequired ir ->
            store.update(
                taskId, r -> r.inputRequired(ir.key(), ir.request(), ir.ledger(), clock.instant()));
      }
    } catch (ElicitationLedgerMismatchException e) {
      // Handler violated the replay idempotency contract mid-task (spec §12): -32602, not -32603.
      store.update(
          taskId,
          r ->
              r.failed(
                  new JsonRpcErrorDetail(JsonRpcProtocol.INVALID_PARAMS, e.getMessage(), null),
                  "replay ledger mismatch",
                  clock.instant()));
    } catch (Exception e) {
      store.update(
          taskId,
          r ->
              r.failed(
                  new JsonRpcErrorDetail(JsonRpcProtocol.INTERNAL_ERROR, e.getMessage(), null),
                  "task execution failed: " + e.getClass().getSimpleName(),
                  clock.instant()));
    }
  }

  private CreateTaskResult toCreateTaskResult(TaskRecord r) {
    return new CreateTaskResult(
        r.taskId(),
        r.status(),
        r.statusMessage(),
        r.createdAt().toString(),
        r.lastUpdatedAt().toString(),
        r.ttl().toMillis(),
        r.pollInterval().toMillis(),
        TasksExtension.RESULT_TYPE_TASK);
  }
}
