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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.mocapi.server.mrtr.ResponseLedgerEntry;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlers;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.model.CreateTaskResult;
import com.callibrity.mocapi.tasks.model.TaskStatus;
import com.callibrity.mocapi.tasks.store.InMemoryTaskStore;
import com.callibrity.mocapi.tasks.store.TaskRecord;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.micrometer.context.ContextSnapshotFactory;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskToolCallDispatcherTest {

  private static final Instant BASE_TIME = Instant.parse("2026-08-02T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
  private static final TaskToolCallDispatcher.Defaults DEFAULTS =
      new TaskToolCallDispatcher.Defaults(Duration.ofHours(1), Duration.ofSeconds(2));

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);
  private final TaskStore store = new InMemoryTaskStore(CLOCK, Duration.ofHours(1));

  @AfterEach
  void closeStore() {
    ((InMemoryTaskStore) store).close();
  }

  private static final class StubPrincipalSource implements McpPrincipalSource {
    @Override
    public String currentPrincipal() {
      return "alice";
    }
  }

  private static final class NoopInvoker
      implements com.callibrity.mocapi.server.tools.ToolCallReplayInvoker {
    @Override
    public Outcome invoke(
        String toolName,
        tools.jackson.databind.JsonNode arguments,
        List<ResponseLedgerEntry> ledger,
        com.callibrity.mocapi.api.progress.McpProgressSource progressOverride,
        com.callibrity.mocapi.server.exchange.McpExchange exchange) {
      return null;
    }
  }

  private TaskExecutionEngine engine() {
    return engine(store);
  }

  private TaskExecutionEngine engine(TaskStore taskStore) {
    return new TaskExecutionEngine(
        taskStore, new NoopInvoker(), ContextSnapshotFactory.builder().build(), CLOCK);
  }

  /** Bare {@link TaskStore} test double that records whether {@link #create} was ever invoked. */
  private static final class RecordingTaskStore implements TaskStore {
    private final Map<String, TaskRecord> records = new ConcurrentHashMap<>();

    @Override
    public void create(TaskRecord rec) {
      records.put(rec.taskId(), rec);
    }

    @Override
    public Optional<TaskRecord> get(String taskId) {
      return Optional.ofNullable(records.get(taskId));
    }

    @Override
    public Optional<TaskRecord> update(String taskId, UnaryOperator<TaskRecord> mutation) {
      return Optional.ofNullable(
          records.computeIfPresent(taskId, (id, rec) -> mutation.apply(rec)));
    }

    @Override
    public void delete(String taskId) {
      records.remove(taskId);
    }
  }

  private CallToolHandler handlerFor(String methodName) throws NoSuchMethodException {
    return handlerFor(methodName, List.of());
  }

  private CallToolHandler handlerFor(String methodName, List<CallToolHandlerCustomizer> customizers)
      throws NoSuchMethodException {
    Object bean = new Fixture();
    Method method = Fixture.class.getMethod(methodName);
    return CallToolHandlers.build(
        bean,
        method,
        new CallToolHandlers.BuildParams(generator, mapper, customizers, List.of(), s -> s, false));
  }

  private RequestMeta capableMeta() {
    return new RequestMeta(
        null,
        "2026-07-28",
        null,
        new ClientCapabilities(
            null,
            null,
            null,
            null,
            Map.of(TasksExtension.EXTENSION_ID, JsonNodeFactory.instance.objectNode())));
  }

  private RequestMeta nonCapableMeta() {
    return new RequestMeta(null, "2026-07-28", null, null);
  }

  private TaskToolCallDispatcher dispatcher() {
    return dispatcher(UnaryOperator.identity());
  }

  private TaskToolCallDispatcher dispatcher(UnaryOperator<String> valueResolver) {
    return new TaskToolCallDispatcher(
        engine(), new StubPrincipalSource(), mapper, DEFAULTS, CLOCK, valueResolver);
  }

  private TaskToolCallDispatcher dispatcher(TaskExecutionEngine engine) {
    return new TaskToolCallDispatcher(
        engine, new StubPrincipalSource(), mapper, DEFAULTS, CLOCK, UnaryOperator.identity());
  }

  private static final String SYNC_MARKER = "SYNC";
  private static final Supplier<Object> PROCEED_SYNC = () -> SYNC_MARKER;

  @Test
  void no_annotation_never_becomes_a_task() throws Exception {
    CallToolHandler handler = handlerFor("plain");
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, capableMeta());

    Object result = dispatcher().intercept(handler, params, PROCEED_SYNC);

    assertThat(result).isEqualTo(SYNC_MARKER);
  }

  @Test
  void annotated_and_capable_client_creates_a_working_task_with_annotation_ttl() throws Exception {
    CallToolHandler handler = handlerFor("taskTool");
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, capableMeta());

    Object result = dispatcher().intercept(handler, params, PROCEED_SYNC);

    assertThat(result).isInstanceOf(CreateTaskResult.class);
    var createResult = (CreateTaskResult) result;
    assertThat(createResult.ttlMs()).isEqualTo(Duration.parse("PT2M").toMillis());

    TaskRecord rec = store.get(createResult.taskId()).orElseThrow();
    assertThat(rec.status()).isEqualTo(TaskStatus.WORKING);
    assertThat(rec.ttl()).isEqualTo(Duration.parse("PT2M"));
    assertThat(rec.toolName()).isEqualTo(handler.name());
    assertThat(rec.principal()).isEqualTo("alice");
  }

  @Test
  void annotated_but_non_capable_client_degrades_to_synchronous_dispatch() throws Exception {
    CallToolHandler handler = handlerFor("taskTool");
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, nonCapableMeta());

    Object result = dispatcher().intercept(handler, params, PROCEED_SYNC);

    assertThat(result).isEqualTo(SYNC_MARKER);
  }

  @Test
  void required_annotation_and_non_capable_client_throws() throws Exception {
    CallToolHandler handler = handlerFor("requiredTaskTool");
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, nonCapableMeta());

    assertThatThrownBy(() -> dispatcher().intercept(handler, params, PROCEED_SYNC))
        .isInstanceOf(McpTaskRequiredException.class);
  }

  @Test
  void placeholder_ttl_is_resolved_before_parsing() throws Exception {
    CallToolHandler handler = handlerFor("placeholderTtlTool");
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, capableMeta());
    UnaryOperator<String> resolver = value -> "${demo.ttl}".equals(value) ? "PT7M" : value;

    Object result = dispatcher(resolver).intercept(handler, params, PROCEED_SYNC);

    var createResult = (CreateTaskResult) result;
    assertThat(createResult.ttlMs()).isEqualTo(Duration.ofMinutes(7).toMillis());
  }

  @Test
  void invalid_resolved_ttl_throws_a_diagnostic_naming_the_tool_and_value() throws Exception {
    CallToolHandler handler = handlerFor("placeholderTtlTool");
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, capableMeta());
    UnaryOperator<String> resolver =
        value -> "${demo.ttl}".equals(value) ? "not-a-duration" : value;

    assertThatThrownBy(() -> dispatcher(resolver).intercept(handler, params, PROCEED_SYNC))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(handler.name())
        .hasMessageContaining("not-a-duration");
  }

  @Test
  void a_denying_guard_aborts_before_the_task_is_created_with_the_sync_paths_forbidden_error()
      throws Exception {
    CallToolHandler handler =
        handlerFor(
            "guardedTaskTool",
            List.of(config -> config.guard(() -> new GuardDecision.Deny("requires scope admin"))));
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, capableMeta());
    RecordingTaskStore recordingStore = new RecordingTaskStore();
    TaskToolCallDispatcher dispatcher = dispatcher(engine(recordingStore));

    assertThatThrownBy(() -> dispatcher.intercept(handler, params, PROCEED_SYNC))
        .isInstanceOf(JsonRpcException.class)
        .satisfies(
            e -> {
              JsonRpcException ex = (JsonRpcException) e;
              assertThat(ex.getCode()).isEqualTo(JsonRpcErrorCodes.FORBIDDEN);
              assertThat(ex.getMessage()).isEqualTo("Forbidden: requires scope admin");
            });
    assertThat(recordingStore.records).isEmpty();
  }

  @Test
  void an_allowing_guard_creates_the_task_normally() throws Exception {
    CallToolHandler handler =
        handlerFor("guardedTaskTool", List.of(config -> config.guard(GuardDecision.Allow::new)));
    CallToolRequestParams params =
        new CallToolRequestParams(
            handler.name(), mapper.createObjectNode(), null, null, capableMeta());

    Object result = dispatcher().intercept(handler, params, PROCEED_SYNC);

    assertThat(result).isInstanceOf(CreateTaskResult.class);
  }

  static class Fixture {
    @McpTool(description = "plain tool")
    public String plain() {
      return "ok";
    }

    @McpTask(ttl = "PT2M")
    @McpTool(description = "task tool")
    public String taskTool() {
      return "ok";
    }

    @McpTask(required = true)
    @McpTool(description = "required task tool")
    public String requiredTaskTool() {
      return "ok";
    }

    @McpTask(ttl = "${demo.ttl}")
    @McpTool(description = "placeholder ttl tool")
    public String placeholderTtlTool() {
      return "ok";
    }

    @McpTask(ttl = "PT2M")
    @McpTool(description = "guarded task tool")
    public String guardedTaskTool() {
      return "ok";
    }
  }
}
