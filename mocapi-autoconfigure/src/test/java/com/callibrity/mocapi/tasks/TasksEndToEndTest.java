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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.model.ElicitRequestFormParams;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.McpMetaKeys;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.oauth2.MocapiOAuth2AutoConfiguration;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * End-to-end verification of the {@code io.modelcontextprotocol/tasks} extension wired through
 * mocapi-autoconfigure, exercised entirely at the {@link JsonRpcDispatcher} level — the same
 * dispatcher a real transport hands requests to.
 *
 * <p>Covers the seven conversation shapes the extension promises: create-poll-complete, the
 * two-elicit replay contract (three handler executions for two round trips), cancel mid-{@code
 * input_required} (no resume, status sticks), the synchronous degrade for a non-capable client, the
 * {@code required = true} rejection ({@code -32021}), cross-principal task isolation, and unknown
 * task ids — all reported as {@code "Unknown task"} {@code -32602}s (spec §7.6).
 */
@SpringBootTest(classes = TasksEndToEndTest.TestApp.class, webEnvironment = WebEnvironment.NONE)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TasksEndToEndTest {

  private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

  @Autowired JsonRpcDispatcher dispatcher;
  @Autowired ObjectMapper objectMapper;
  @Autowired SwitchablePrincipalSource principalSource;
  @Autowired TestTools testTools;

  private final AtomicInteger idCounter = new AtomicInteger(1);

  @BeforeEach
  void resetFixtures() {
    principalSource.setPrincipal("alice");
    testTools.confirmTwiceExecutions.set(0);
  }

  // ---- 1: create -> poll -> complete ----

  @Test
  void create_poll_complete_delivers_the_tool_result_via_polling() {
    JsonNode created =
        resultOf(dispatcher.dispatch(callTool("slow_echo", Map.of("message", "hi"), true, false)));

    assertThat(created.path("resultType").asString()).isEqualTo(TasksExtension.RESULT_TYPE_TASK);
    String taskId = created.path("taskId").asString();
    assertThat(taskId).hasSize(43);

    JsonNode finalGet = pollTaskUntil(taskId, "completed");

    assertThat(finalGet.path("status").asString()).isEqualTo("completed");
    assertThat(finalGet.path("result").path("resultType").asString()).isEqualTo("complete");
    assertThat(textOf(finalGet.path("result"))).isEqualTo("echo:hi");
  }

  // ---- 2: two-elicit, three executions ----

  @Test
  void two_sequential_elicits_replay_the_handler_three_times() {
    JsonNode created =
        resultOf(dispatcher.dispatch(callTool("confirm_twice", Map.of(), true, true)));
    String taskId = created.path("taskId").asString();

    JsonNode firstPause = pollTaskUntil(taskId, "input_required");
    assertThat(firstPause.path("inputRequests").has("elicit-1")).isTrue();
    assertThat(
            firstPause
                .path("inputRequests")
                .path("elicit-1")
                .path("params")
                .path("message")
                .asString())
        .isEqualTo("Confirm step 1?");

    resultOf(
        dispatcher.dispatch(
            tasksUpdateAccept(taskId, "elicit-1", Map.of("confirmed", Boolean.TRUE))));
    JsonNode secondPause = pollTaskUntil(taskId, "input_required");
    assertThat(secondPause.path("inputRequests").has("elicit-2")).isTrue();
    assertThat(
            secondPause
                .path("inputRequests")
                .path("elicit-2")
                .path("params")
                .path("message")
                .asString())
        .isEqualTo("Confirm step 2?");

    resultOf(
        dispatcher.dispatch(
            tasksUpdateAccept(taskId, "elicit-2", Map.of("confirmed", Boolean.TRUE))));
    JsonNode completed = pollTaskUntil(taskId, "completed");

    assertThat(completed.path("status").asString()).isEqualTo("completed");
    assertThat(textOf(completed.path("result"))).isEqualTo("confirmed:ACCEPT:ACCEPT");
    // Documents the replay contract: execution #1 pauses at elicit-1, execution #2 replays
    // elicit-1 from the ledger and pauses at elicit-2, execution #3 replays both and finishes.
    assertThat(testTools.confirmTwiceExecutions).hasValue(3);
  }

  // ---- 3: cancel mid-input_required ----

  @Test
  void cancelling_mid_input_required_sticks_and_a_late_update_does_not_resume() {
    JsonNode created =
        resultOf(dispatcher.dispatch(callTool("confirm_twice", Map.of(), true, true)));
    String taskId = created.path("taskId").asString();
    pollTaskUntil(taskId, "input_required");
    int executionsAtCancel = testTools.confirmTwiceExecutions.get();

    resultOf(dispatcher.dispatch(tasksCancel(taskId)));
    JsonNode afterCancel = resultOf(dispatcher.dispatch(tasksGet(taskId)));
    assertThat(afterCancel.path("status").asString()).isEqualTo("cancelled");

    // A late update answering the (now moot) outstanding key: the update itself acks
    // synchronously, but the cancelled record is terminal, so no resume happens.
    JsonNode updateResult =
        resultOf(
            dispatcher.dispatch(
                tasksUpdateAccept(taskId, "elicit-1", Map.of("confirmed", Boolean.TRUE))));
    assertThat(updateResult.path("resultType").asString()).isEqualTo("complete");

    JsonNode stillCancelled = resultOf(dispatcher.dispatch(tasksGet(taskId)));
    assertThat(stillCancelled.path("status").asString()).isEqualTo("cancelled");
    assertThat(testTools.confirmTwiceExecutions).hasValue(executionsAtCancel);
  }

  // ---- 4: sync degrade ----

  @Test
  void a_non_capable_call_degrades_to_a_plain_synchronous_result_with_no_task() {
    JsonNode result =
        resultOf(
            dispatcher.dispatch(callTool("slow_echo", Map.of("message", "sync"), false, false)));

    assertThat(result.path("resultType").asString()).isEqualTo("complete");
    assertThat(result.has("taskId")).isFalse();
    assertThat(textOf(result)).isEqualTo("echo:sync");
  }

  // ---- 5: required ----

  @Test
  void a_required_task_tool_rejects_a_non_capable_caller_with_missing_capability_error() {
    var response = dispatcher.dispatch(callTool("must_task", Map.of("message", "x"), false, false));

    JsonRpcError error = errorOf(response);
    assertThat(error.error().code()).isEqualTo(-32021);
    JsonNode required = error.error().data().path("requiredCapabilities");
    assertThat(required.path("extensions").path(TasksExtension.EXTENSION_ID).isObject()).isTrue();
  }

  // ---- 6: cross-principal ----

  @Test
  void a_task_created_by_one_principal_is_invisible_to_another() {
    JsonNode created =
        resultOf(dispatcher.dispatch(callTool("slow_echo", Map.of("message", "hi"), true, false)));
    String taskId = created.path("taskId").asString();

    principalSource.setPrincipal("mallory");
    JsonRpcError error = errorOf(dispatcher.dispatch(tasksGet(taskId)));

    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
    assertThat(error.error().message()).isEqualTo("Unknown task");
  }

  // ---- 7: unknown task ----

  @Test
  void an_unknown_task_id_is_reported_as_invalid_params() {
    JsonRpcError error = errorOf(dispatcher.dispatch(tasksGet("does-not-exist")));

    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
    assertThat(error.error().message()).isEqualTo("Unknown task");
  }

  // ---- 8: tasks/* namespace capability gate ----

  @Test
  void tasks_get_from_a_non_capable_client_rejects_with_missing_capability_before_task_lookup() {
    // taskId doesn't need to exist — proves the dispatcher/translator wiring gates on the
    // capability before task lookup, mirroring McpTasksServiceTest's unit-level coverage of the
    // same rule at the JsonRpcDispatcher seam a real transport hands requests to.
    var response = dispatcher.dispatch(tasksGetNonCapable("does-not-exist"));

    JsonRpcError error = errorOf(response);
    assertThat(error.error().code()).isEqualTo(-32021);
    JsonNode required = error.error().data().path("requiredCapabilities");
    assertThat(required.path("extensions").path(TasksExtension.EXTENSION_ID).isObject()).isTrue();
  }

  // ---- helpers ----

  private JsonNode resultOf(Object response) {
    assertThat(response).isInstanceOf(JsonRpcResult.class);
    return ((JsonRpcResult) response).result();
  }

  private JsonRpcError errorOf(Object response) {
    assertThat(response).isInstanceOf(JsonRpcError.class);
    return (JsonRpcError) response;
  }

  /** Extracts the first text content block's text from a {@code CallToolResult}-shaped node. */
  private String textOf(JsonNode callToolResult) {
    return callToolResult.path("content").get(0).path("text").asString();
  }

  private JsonNode pollTaskUntil(String taskId, String status) {
    long deadline = System.nanoTime() + POLL_TIMEOUT.toNanos();
    JsonNode result = resultOf(dispatcher.dispatch(tasksGet(taskId)));
    while (!status.equals(result.path("status").asString()) && System.nanoTime() < deadline) {
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      result = resultOf(dispatcher.dispatch(tasksGet(taskId)));
    }
    return result;
  }

  private JsonNode nextId() {
    return IntNode.valueOf(idCounter.getAndIncrement());
  }

  private ObjectNode meta(boolean taskCapable, boolean elicitCapable) {
    ObjectNode meta = objectMapper.createObjectNode();
    meta.put(McpMetaKeys.PROTOCOL_VERSION, "2026-07-28");
    ObjectNode capabilities = meta.putObject(McpMetaKeys.CLIENT_CAPABILITIES);
    if (elicitCapable) {
      capabilities.putObject("elicitation");
    }
    if (taskCapable) {
      capabilities.putObject("extensions").putObject(TasksExtension.EXTENSION_ID);
    }
    return meta;
  }

  private JsonRpcCall callTool(
      String name, Map<String, Object> arguments, boolean taskCapable, boolean elicitCapable) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", name);
    ObjectNode args = params.putObject("arguments");
    arguments.forEach(args::putPOJO);
    params.set("_meta", meta(taskCapable, elicitCapable));
    return new JsonRpcCall(JsonRpcProtocol.VERSION, McpMethods.TOOLS_CALL, params, nextId());
  }

  private JsonRpcCall tasksGet(String taskId) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("taskId", taskId);
    params.set("_meta", meta(true, false));
    return new JsonRpcCall(JsonRpcProtocol.VERSION, TasksExtension.TASKS_GET, params, nextId());
  }

  private JsonRpcCall tasksGetNonCapable(String taskId) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("taskId", taskId);
    params.set("_meta", meta(false, false));
    return new JsonRpcCall(JsonRpcProtocol.VERSION, TasksExtension.TASKS_GET, params, nextId());
  }

  private JsonRpcCall tasksCancel(String taskId) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("taskId", taskId);
    params.set("_meta", meta(true, false));
    return new JsonRpcCall(JsonRpcProtocol.VERSION, TasksExtension.TASKS_CANCEL, params, nextId());
  }

  private JsonRpcCall tasksUpdateAccept(String taskId, String key, Map<String, Object> content) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("taskId", taskId);
    ObjectNode responses = params.putObject("inputResponses");
    ObjectNode entry = responses.putObject(key);
    entry.put("action", "accept");
    ObjectNode contentNode = entry.putObject("content");
    content.forEach(contentNode::putPOJO);
    params.set("_meta", meta(true, false));
    return new JsonRpcCall(JsonRpcProtocol.VERSION, TasksExtension.TASKS_UPDATE, params, nextId());
  }

  /** Switchable {@link McpPrincipalSource}: defaults to "alice", flippable to "mallory". */
  static final class SwitchablePrincipalSource implements McpPrincipalSource {
    private volatile String principal = "alice";

    void setPrincipal(String principal) {
      this.principal = principal;
    }

    @Override
    public String currentPrincipal() {
      return principal;
    }
  }

  /** Test tool component exercising the three {@code @McpTask} decision-rule outcomes. */
  static class TestTools {

    final AtomicInteger confirmTwiceExecutions = new AtomicInteger();

    @McpTool(name = "slow_echo", description = "Echoes its input after a short delay")
    @McpTask
    public String slowEcho(String message) {
      try {
        Thread.sleep(30);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return "echo:" + message;
    }

    @McpTool(name = "confirm_twice", description = "Asks for two sequential confirmations")
    @McpTask
    public String confirmTwice(McpToolContext ctx) {
      confirmTwiceExecutions.incrementAndGet();
      ElicitResult first = ctx.elicit(new ElicitRequestFormParams("Confirm step 1?", null));
      ElicitResult second = ctx.elicit(new ElicitRequestFormParams("Confirm step 2?", null));
      return "confirmed:" + first.action() + ":" + second.action();
    }

    @McpTool(name = "must_task", description = "Requires the tasks client capability")
    @McpTask(required = true)
    public String mustTask(String message) {
      return "must:" + message;
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = MocapiOAuth2AutoConfiguration.class)
  static class TestApp {

    @Bean
    TestTools testTools() {
      return new TestTools();
    }

    @Bean
    SwitchablePrincipalSource switchablePrincipalSource() {
      return new SwitchablePrincipalSource();
    }
  }
}
