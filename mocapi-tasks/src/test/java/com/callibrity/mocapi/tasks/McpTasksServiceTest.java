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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitRequest;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.InputRequest;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.server.tools.ToolCallReplayInvoker;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.model.CancelTaskParams;
import com.callibrity.mocapi.tasks.model.GetTaskParams;
import com.callibrity.mocapi.tasks.model.GetTaskResult;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.model.UpdateTaskParams;
import com.callibrity.mocapi.tasks.store.InMemoryTaskStore;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import io.micrometer.context.ContextSnapshotFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Tests for {@link McpTasksService} — {@code tasks/get}, {@code tasks/update}, {@code
 * tasks/cancel}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpTasksServiceTest {

  private static final Instant BASE_TIME = Instant.parse("2026-08-02T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
  private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(2);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** {@code _meta} declaring the {@code io.modelcontextprotocol/tasks} capability. */
  private static final RequestMeta CAPABLE_META =
      new RequestMeta(
          null,
          "2026-07-28",
          null,
          new ClientCapabilities(
              null,
              null,
              null,
              null,
              Map.of(TasksExtension.EXTENSION_ID, JsonNodeFactory.instance.objectNode())));

  /** {@code _meta} with no {@code io.modelcontextprotocol/tasks} capability declared. */
  private static final RequestMeta NON_CAPABLE_META =
      new RequestMeta(
          null, "2026-07-28", null, new ClientCapabilities(null, null, null, null, null));

  private final InMemoryTaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1));

  @AfterEach
  void closeStore() {
    store.close();
  }

  private static final class StubPrincipalSource implements McpPrincipalSource {
    private String principal = "alice";

    @Override
    public String currentPrincipal() {
      return principal;
    }
  }

  private static final class CountingInvoker implements ToolCallReplayInvoker {
    private final AtomicInteger invocations = new AtomicInteger();
    private volatile Outcome outcome;

    private CountingInvoker(Outcome outcome) {
      this.outcome = outcome;
    }

    @Override
    public Outcome invoke(
        String toolName,
        tools.jackson.databind.JsonNode arguments,
        List<ResponseLedgerEntry> ledger,
        com.callibrity.mocapi.api.progress.McpProgressSource progressOverride,
        com.callibrity.mocapi.server.exchange.McpExchange exchange) {
      invocations.incrementAndGet();
      return outcome;
    }

    int invocationCount() {
      return invocations.get();
    }
  }

  private TaskRecord baseRecord(String taskId, TaskStatus status) {
    return new TaskRecord(
        taskId,
        "demo.tool",
        null,
        "alice",
        "2026-07-28",
        null,
        status,
        "in progress",
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

  private TaskRecord await(String taskId, TaskStatus status) {
    long deadline = System.nanoTime() + AWAIT_TIMEOUT.toNanos();
    TaskRecord rec = store.get(taskId).orElseThrow();
    while (rec.status() != status && System.nanoTime() < deadline) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      rec = store.get(taskId).orElseThrow();
    }
    return rec;
  }

  private McpTasksService service(McpPrincipalSource principalSource, TaskExecutionEngine engine) {
    return new McpTasksService(store, engine, principalSource, CLOCK, MAPPER);
  }

  private TaskExecutionEngine engine(ToolCallReplayInvoker invoker) {
    return new TaskExecutionEngine(store, invoker, ContextSnapshotFactory.builder().build(), CLOCK);
  }

  // ---- tasks/get ----

  @Test
  void get_working_task_populates_only_common_fields() {
    store.create(baseRecord("t-working", TaskStatus.WORKING));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    GetTaskResult result = service.getTask(new GetTaskParams("t-working", CAPABLE_META));

    assertThat(result.taskId()).isEqualTo("t-working");
    assertThat(result.status()).isEqualTo(TaskStatus.WORKING);
    assertThat(result.statusMessage()).isEqualTo("in progress");
    assertThat(result.createdAt()).isEqualTo(BASE_TIME.toString());
    assertThat(result.lastUpdatedAt()).isEqualTo(BASE_TIME.toString());
    assertThat(result.ttlMs()).isEqualTo(Duration.ofMinutes(5).toMillis());
    assertThat(result.pollIntervalMs()).isEqualTo(Duration.ofSeconds(1).toMillis());
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    assertThat(result.inputRequests()).isNull();
    assertThat(result.result()).isNull();
    assertThat(result.error()).isNull();
  }

  @Test
  void get_input_required_task_populates_input_requests_only() {
    ElicitRequest request = new ElicitRequest(new ElicitRequestFormParams("please answer", null));
    Map<String, InputRequest> inputRequests = Map.of("elicit-1", request);
    TaskRecord rec = baseRecord("t-input-required", TaskStatus.INPUT_REQUIRED);
    rec =
        new TaskRecord(
            rec.taskId(),
            rec.toolName(),
            rec.arguments(),
            rec.principal(),
            rec.protocolVersion(),
            rec.clientCapabilities(),
            rec.status(),
            rec.statusMessage(),
            rec.createdAt(),
            rec.lastUpdatedAt(),
            rec.ttl(),
            rec.pollInterval(),
            List.of(new ResponseLedgerEntry("elicit-1", "fp-1", null)),
            inputRequests,
            null,
            null,
            rec.version());
    store.create(rec);
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    GetTaskResult result = service.getTask(new GetTaskParams("t-input-required", CAPABLE_META));

    assertThat(result.status()).isEqualTo(TaskStatus.INPUT_REQUIRED);
    assertThat(result.inputRequests()).isEqualTo(inputRequests);
    assertThat(result.result()).isNull();
    assertThat(result.error()).isNull();
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
  }

  @Test
  void get_completed_task_populates_result_only() {
    CallToolResult toolResult = new CallToolResult(List.of(), false, null, "complete");
    TaskRecord rec = baseRecord("t-completed", TaskStatus.WORKING).completed(toolResult, BASE_TIME);
    store.create(rec);
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    GetTaskResult result = service.getTask(new GetTaskParams("t-completed", CAPABLE_META));

    assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(result.result()).isEqualTo(toolResult);
    assertThat(result.inputRequests()).isNull();
    assertThat(result.error()).isNull();
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
  }

  @Test
  void get_failed_task_populates_error_only() {
    JsonRpcErrorDetail error = new JsonRpcErrorDetail(JsonRpcProtocol.INTERNAL_ERROR, "boom", null);
    TaskRecord rec =
        baseRecord("t-failed", TaskStatus.WORKING)
            .failed(error, "task execution failed", BASE_TIME);
    store.create(rec);
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    GetTaskResult result = service.getTask(new GetTaskParams("t-failed", CAPABLE_META));

    assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
    assertThat(result.error()).isEqualTo(error);
    assertThat(result.inputRequests()).isNull();
    assertThat(result.result()).isNull();
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
  }

  @Test
  void get_cancelled_task_populates_only_common_fields() {
    TaskRecord rec = baseRecord("t-cancelled", TaskStatus.WORKING).cancelled(BASE_TIME);
    store.create(rec);
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    GetTaskResult result = service.getTask(new GetTaskParams("t-cancelled", CAPABLE_META));

    assertThat(result.status()).isEqualTo(TaskStatus.CANCELLED);
    assertThat(result.inputRequests()).isNull();
    assertThat(result.result()).isNull();
    assertThat(result.error()).isNull();
    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
  }

  @Test
  void get_unknown_task_id_throws_invalid_params_with_generic_message() {
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(() -> service.getTask(new GetTaskParams("nope", CAPABLE_META)))
        .isInstanceOf(JsonRpcException.class)
        .satisfies(
            ex ->
                assertThat(((JsonRpcException) ex).getCode())
                    .isEqualTo(JsonRpcProtocol.INVALID_PARAMS))
        .hasMessage("Unknown task");
  }

  @Test
  void get_task_owned_by_a_different_principal_throws_the_same_generic_message() {
    store.create(baseRecord("t-alice-owned", TaskStatus.WORKING));
    StubPrincipalSource principals = new StubPrincipalSource();
    principals.principal = "mallory";
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(() -> service.getTask(new GetTaskParams("t-alice-owned", CAPABLE_META)))
        .isInstanceOf(JsonRpcException.class)
        .satisfies(
            ex ->
                assertThat(((JsonRpcException) ex).getCode())
                    .isEqualTo(JsonRpcProtocol.INVALID_PARAMS))
        .hasMessage("Unknown task");
  }

  @Test
  void
      get_without_the_tasks_capability_rejects_with_missing_capability_before_checking_ownership() {
    // No task named "nope" exists — proves the capability gate runs before task lookup, per
    // SEP-2575/SEP-2663: a non-capable caller always sees -32021, never a -32602 existence signal.
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(() -> service.getTask(new GetTaskParams("nope", NON_CAPABLE_META)))
        .isInstanceOf(McpTaskRequiredException.class);
  }

  // ---- tasks/update ----

  private TaskRecord inputRequiredRecord(String taskId) {
    ElicitRequest request = new ElicitRequest(new ElicitRequestFormParams("please answer", null));
    TaskRecord base = baseRecord(taskId, TaskStatus.INPUT_REQUIRED);
    return new TaskRecord(
        base.taskId(),
        base.toolName(),
        base.arguments(),
        base.principal(),
        base.protocolVersion(),
        base.clientCapabilities(),
        base.status(),
        base.statusMessage(),
        base.createdAt(),
        base.lastUpdatedAt(),
        base.ttl(),
        base.pollInterval(),
        List.of(new ResponseLedgerEntry("elicit-1", "fp-1", null)),
        Map.of("elicit-1", request),
        null,
        null,
        base.version());
  }

  private static JsonNode acceptNode() {
    return MAPPER.valueToTree(new ElicitResult(ElicitAction.ACCEPT, null));
  }

  @Test
  void update_answering_the_outstanding_key_flips_to_working_and_resumes_exactly_once() {
    store.create(inputRequiredRecord("t-update"));
    CallToolResult toolResult = new CallToolResult(List.of(), false, null, "complete");
    CountingInvoker invoker =
        new CountingInvoker(new ToolCallReplayInvoker.Outcome.Completed(toolResult));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine(invoker));

    Map<String, JsonNode> responses = Map.of("elicit-1", acceptNode());
    var result = service.updateTask(new UpdateTaskParams("t-update", responses, CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    TaskRecord finalRecord = await("t-update", TaskStatus.COMPLETED);
    assertThat(finalRecord.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(finalRecord.result()).isEqualTo(toolResult);
    assertThat(invoker.invocationCount()).isEqualTo(1);
  }

  @Test
  void duplicate_update_acks_without_a_second_resume() {
    store.create(inputRequiredRecord("t-dup"));
    CallToolResult toolResult = new CallToolResult(List.of(), false, null, "complete");
    CountingInvoker invoker =
        new CountingInvoker(new ToolCallReplayInvoker.Outcome.Completed(toolResult));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine(invoker));
    Map<String, JsonNode> responses = Map.of("elicit-1", acceptNode());

    service.updateTask(new UpdateTaskParams("t-dup", responses, CAPABLE_META));
    await("t-dup", TaskStatus.COMPLETED);
    assertThat(invoker.invocationCount()).isEqualTo(1);

    var result = service.updateTask(new UpdateTaskParams("t-dup", responses, CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    assertThat(invoker.invocationCount()).isEqualTo(1);
    assertThat(store.get("t-dup").orElseThrow().status()).isEqualTo(TaskStatus.COMPLETED);
  }

  @Test
  void update_with_unknown_key_only_acks_but_stays_input_required_with_no_resume() {
    store.create(inputRequiredRecord("t-unknown-key"));
    CountingInvoker invoker =
        new CountingInvoker(
            new ToolCallReplayInvoker.Outcome.Completed(
                new CallToolResult(List.of(), false, null, "complete")));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine(invoker));
    Map<String, JsonNode> responses = Map.of("elicit-not-outstanding", acceptNode());

    var result = service.updateTask(new UpdateTaskParams("t-unknown-key", responses, CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    assertThat(store.get("t-unknown-key").orElseThrow().status())
        .isEqualTo(TaskStatus.INPUT_REQUIRED);
    assertThat(invoker.invocationCount()).isEqualTo(0);
  }

  @Test
  void update_ignores_a_malformed_entry_but_still_merges_a_valid_one_in_the_same_call() {
    // Two outstanding keys on one record: real production tasks only ever have one (ADR-0021's
    // single-pending-request-per-round replay model), but the store's shape doesn't forbid it, and
    // this directly exercises mergeResponses' per-entry tolerance (SEP-2322 "SHOULD ignore
    // information it does not recognize") independent of how the ledger was populated.
    ElicitRequest request = new ElicitRequest(new ElicitRequestFormParams("please answer", null));
    TaskRecord base = inputRequiredRecord("t-mixed-update");
    TaskRecord twoOutstanding =
        new TaskRecord(
            base.taskId(),
            base.toolName(),
            base.arguments(),
            base.principal(),
            base.protocolVersion(),
            base.clientCapabilities(),
            base.status(),
            base.statusMessage(),
            base.createdAt(),
            base.lastUpdatedAt(),
            base.ttl(),
            base.pollInterval(),
            List.of(
                new ResponseLedgerEntry("elicit-1", "fp-1", null),
                new ResponseLedgerEntry("elicit-2", "fp-2", null)),
            Map.of("elicit-1", request, "elicit-2", request),
            null,
            null,
            base.version());
    store.create(twoOutstanding);
    CountingInvoker invoker =
        new CountingInvoker(
            new ToolCallReplayInvoker.Outcome.Completed(
                new CallToolResult(List.of(), false, null, "complete")));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine(invoker));
    // "ignored" has no recognizable ElicitResult/CreateMessageResult/ListRootsResult fingerprint —
    // exactly the {"ignored":true} shape the conformance suite's
    // tasks-result-type-complete-on-non-task-responses check sends.
    Map<String, JsonNode> responses =
        Map.of(
            "elicit-1", acceptNode(), "elicit-2", MAPPER.createObjectNode().put("ignored", true));

    var result =
        service.updateTask(new UpdateTaskParams("t-mixed-update", responses, CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    TaskRecord updated = store.get("t-mixed-update").orElseThrow();
    assertThat(updated.ledger())
        .filteredOn(e -> e.key().equals("elicit-1"))
        .singleElement()
        .satisfies(e -> assertThat(e.isAnswered()).isTrue());
    assertThat(updated.ledger())
        .filteredOn(e -> e.key().equals("elicit-2"))
        .singleElement()
        .satisfies(e -> assertThat(e.isAnswered()).isFalse());
  }

  @Test
  void update_with_no_input_responses_is_a_no_op_ack() {
    store.create(inputRequiredRecord("t-no-responses"));
    CountingInvoker invoker =
        new CountingInvoker(
            new ToolCallReplayInvoker.Outcome.Completed(
                new CallToolResult(List.of(), false, null, "complete")));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine(invoker));

    var result = service.updateTask(new UpdateTaskParams("t-no-responses", null, CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    assertThat(store.get("t-no-responses").orElseThrow().status())
        .isEqualTo(TaskStatus.INPUT_REQUIRED);
    assertThat(invoker.invocationCount()).isEqualTo(0);
  }

  @Test
  void update_unknown_task_id_throws_invalid_params() {
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(() -> service.updateTask(new UpdateTaskParams("nope", null, CAPABLE_META)))
        .isInstanceOf(JsonRpcException.class)
        .hasMessage("Unknown task");
  }

  @Test
  void update_without_the_tasks_capability_rejects_with_missing_capability() {
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(
            () -> service.updateTask(new UpdateTaskParams("nope", null, NON_CAPABLE_META)))
        .isInstanceOf(McpTaskRequiredException.class);
  }

  // ---- tasks/cancel ----

  @Test
  void cancel_non_terminal_task_transitions_to_cancelled() {
    store.create(baseRecord("t-cancel-me", TaskStatus.WORKING));
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    var result = service.cancelTask(new CancelTaskParams("t-cancel-me", CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    assertThat(store.get("t-cancel-me").orElseThrow().status()).isEqualTo(TaskStatus.CANCELLED);
  }

  @Test
  void cancel_completed_task_stays_completed() {
    CallToolResult toolResult = new CallToolResult(List.of(), false, null, "complete");
    TaskRecord rec =
        baseRecord("t-already-done", TaskStatus.WORKING).completed(toolResult, BASE_TIME);
    store.create(rec);
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    var result = service.cancelTask(new CancelTaskParams("t-already-done", CAPABLE_META));

    assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    TaskRecord finalRecord = store.get("t-already-done").orElseThrow();
    assertThat(finalRecord.status()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(finalRecord.result()).isEqualTo(toolResult);
  }

  @Test
  void cancel_unknown_task_id_throws_invalid_params() {
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(() -> service.cancelTask(new CancelTaskParams("nope", CAPABLE_META)))
        .isInstanceOf(JsonRpcException.class)
        .hasMessage("Unknown task");
  }

  @Test
  void cancel_without_the_tasks_capability_rejects_with_missing_capability() {
    StubPrincipalSource principals = new StubPrincipalSource();
    McpTasksService service = service(principals, engine((n, a, l, p, e) -> null));

    assertThatThrownBy(() -> service.cancelTask(new CancelTaskParams("nope", NON_CAPABLE_META)))
        .isInstanceOf(McpTaskRequiredException.class);
  }
}
