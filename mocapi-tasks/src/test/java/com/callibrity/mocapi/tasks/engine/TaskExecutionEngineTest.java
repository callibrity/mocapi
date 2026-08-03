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

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.server.mrtr.ElicitationLedgerMismatchException;
import com.callibrity.mocapi.server.mrtr.ReplayOutcome;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.server.tools.ToolCallReplayInvoker;
import com.callibrity.mocapi.tasks.model.CreateTaskResult;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.InMemoryTaskStore;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import io.micrometer.context.ContextSnapshotFactory;
import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** Tests for {@link TaskExecutionEngine} — spawn, outcome writes, cancel-sticks discard. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskExecutionEngineTest {

  private static final Instant BASE_TIME = Instant.parse("2026-08-02T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(2);

  private TaskRecord newRecord(String taskId) {
    return new TaskRecord(
        taskId,
        "demo.tool",
        null,
        "user-1",
        "2026-07-28",
        null,
        TaskStatus.WORKING,
        null,
        BASE_TIME,
        BASE_TIME,
        Duration.ofMinutes(5),
        Duration.ofSeconds(1),
        List.of(),
        Map.of(),
        null,
        null,
        0L);
  }

  private TaskRecord await(InMemoryTaskStore store, String taskId, TaskStatus status) {
    AtomicReference<TaskRecord> rec = new AtomicReference<>(store.get(taskId).orElseThrow());
    Awaitility.await()
        .atMost(AWAIT_TIMEOUT)
        .pollInterval(Duration.ofMillis(10))
        .until(
            () -> {
              rec.set(store.get(taskId).orElseThrow());
              return rec.get().status() == status;
            });
    return rec.get();
  }

  @Test
  void completed_outcome_transitions_record_to_completed() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      CallToolResult result = new CallToolResult(List.of(), false, null, "complete");
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) ->
              new ReplayOutcome.Completed<>(result);
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      TaskRecord rec = newRecord("t-completed");
      CreateTaskResult createResult = engine.createAndStart(rec);

      assertThat(createResult.resultType()).isEqualTo("task");
      assertThat(createResult.status()).isEqualTo(TaskStatus.WORKING);
      assertThat(createResult.ttlMs()).isEqualTo(Duration.ofMinutes(5).toMillis());
      assertThat(createResult.pollIntervalMs()).isEqualTo(Duration.ofSeconds(1).toMillis());

      TaskRecord finalRecord = await(store, "t-completed", TaskStatus.COMPLETED);
      assertThat(finalRecord.status()).isEqualTo(TaskStatus.COMPLETED);
      assertThat(finalRecord.result()).isEqualTo(result);
    }
  }

  @Test
  void input_required_outcome_persists_key_request_and_ledger() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      ElicitRequest request = new ElicitRequest(new ElicitRequestFormParams("please answer", null));
      List<ResponseLedgerEntry> ledger =
          List.of(new ResponseLedgerEntry("elicit-1", "fingerprint-1", null));
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledgerArg, progress, exchange) ->
              new ReplayOutcome.InputRequired<>("elicit-1", request, ledger);
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      store.create(newRecord("t-input-required"));
      engine.resume("t-input-required");

      TaskRecord finalRecord = await(store, "t-input-required", TaskStatus.INPUT_REQUIRED);
      assertThat(finalRecord.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
      assertThat(finalRecord.inputRequests()).containsOnlyKeys("elicit-1");
      assertThat(finalRecord.ledger()).isEqualTo(ledger);
    }
  }

  @Test
  void runtime_exception_fails_task_with_internal_error() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) -> {
            throw new RuntimeException("boom");
          };
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      store.create(newRecord("t-failed"));
      engine.resume("t-failed");

      TaskRecord finalRecord = await(store, "t-failed", TaskStatus.FAILED);
      assertThat(finalRecord.status()).isEqualTo(TaskStatus.FAILED);
      assertThat(finalRecord.error().code()).isEqualTo(-32603);
    }
  }

  @Test
  void json_rpc_exception_fails_task_preserving_its_own_error_code() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) -> {
            throw new JsonRpcException(-32010, "Forbidden: nope");
          };
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      store.create(newRecord("t-json-rpc-failed"));
      engine.resume("t-json-rpc-failed");

      TaskRecord finalRecord = await(store, "t-json-rpc-failed", TaskStatus.FAILED);
      assertThat(finalRecord.status()).isEqualTo(TaskStatus.FAILED);
      assertThat(finalRecord.error().code()).isEqualTo(-32010);
      assertThat(finalRecord.error().message()).isEqualTo("Forbidden: nope");
    }
  }

  /**
   * {@link ElicitationLedgerMismatchException}'s constructor is package-private to {@code
   * com.callibrity.mocapi.server.mrtr} — it's only ever thrown internally by {@code
   * ReplayExecutor}. Reflection is the only way to construct one from this module's test to
   * exercise the engine's catch clause in isolation.
   */
  private static ElicitationLedgerMismatchException newLedgerMismatchException(String message) {
    try {
      Constructor<ElicitationLedgerMismatchException> ctor =
          ElicitationLedgerMismatchException.class.getDeclaredConstructor(String.class);
      ctor.setAccessible(true);
      return ctor.newInstance(message);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void ledger_mismatch_fails_task_with_invalid_params_and_status_message() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) -> {
            throw newLedgerMismatchException("ledger mismatch");
          };
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      store.create(newRecord("t-ledger-mismatch"));
      engine.resume("t-ledger-mismatch");

      TaskRecord finalRecord = await(store, "t-ledger-mismatch", TaskStatus.FAILED);
      assertThat(finalRecord.status()).isEqualTo(TaskStatus.FAILED);
      assertThat(finalRecord.error().code()).isEqualTo(-32602);
      assertThat(finalRecord.statusMessage()).isEqualTo("replay ledger mismatch");
    }
  }

  @Test
  void resume_of_a_deleted_task_is_a_silent_no_op() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      var invocations = new java.util.concurrent.atomic.AtomicInteger();
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) -> {
            invocations.incrementAndGet();
            return new ReplayOutcome.Completed<>(new CallToolResult(List.of(), false, null, "c"));
          };
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      // No record was ever created for this taskId: run() must see rec == null and return
      // immediately, never reaching the invoker.
      engine.resume("t-never-created");

      Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true);
      assertThat(invocations).hasValue(0);
      assertThat(store.get("t-never-created")).isEmpty();
    }
  }

  @Test
  void resume_of_an_already_terminal_task_does_not_re_invoke() {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      var invocations = new java.util.concurrent.atomic.AtomicInteger();
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) -> {
            invocations.incrementAndGet();
            return new ReplayOutcome.Completed<>(new CallToolResult(List.of(), false, null, "c"));
          };
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      store.create(newRecord("t-already-cancelled"));
      store.update("t-already-cancelled", r -> r.cancelled(BASE_TIME));

      // The record exists but is already terminal (CANCELLED): run() must see
      // status != WORKING and return immediately, never reaching the invoker.
      engine.resume("t-already-cancelled");

      Awaitility.await().pollDelay(Duration.ofMillis(100)).until(() -> true);
      assertThat(invocations).hasValue(0);
      assertThat(store.get("t-already-cancelled").orElseThrow().status())
          .isEqualTo(TaskStatus.CANCELLED);
    }
  }

  @Test
  void cancel_wins_race_discards_completed_output() throws InterruptedException {
    try (InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1))) {
      CountDownLatch releaseLatch = new CountDownLatch(1);
      CountDownLatch enteredLatch = new CountDownLatch(1);
      CallToolResult result = new CallToolResult(List.of(), false, null, "complete");
      ToolCallReplayInvoker invoker =
          (toolName, arguments, ledger, progress, exchange) -> {
            enteredLatch.countDown();
            try {
              releaseLatch.await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new RuntimeException(e);
            }
            return new ReplayOutcome.Completed<>(result);
          };
      TaskExecutionEngine engine =
          new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);

      TaskRecord rec = newRecord("t-cancel-race");
      engine.createAndStart(rec);
      assertThat(enteredLatch.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

      store.update("t-cancel-race", r -> r.cancelled(BASE_TIME));
      releaseLatch.countDown();

      // Give the run loop time to attempt (and fail) its terminal write.
      Awaitility.await().pollDelay(Duration.ofMillis(200)).until(() -> true);
      TaskRecord finalRecord = store.get("t-cancel-race").orElseThrow();
      assertThat(finalRecord.status()).isEqualTo(TaskStatus.CANCELLED);
      assertThat(finalRecord.result()).isNull();
    }
  }
}
