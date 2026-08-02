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
package com.callibrity.mocapi.tasks.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ElicitAction;
import com.callibrity.mocapi.model.ElicitResult;
import com.callibrity.mocapi.model.InputRequest;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.tasks.TasksExtension;
import com.callibrity.ripcurl.core.JsonRpcErrorDetail;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Exact-JSON assertions for the Tasks extension wire types (io.modelcontextprotocol/tasks). */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TaskWireShapesTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  private JsonNode tree(String json) {
    return mapper.readTree(json);
  }

  @Nested
  class Create_task_result {

    @Test
    void serializes_the_working_shape_exactly() throws Exception {
      var result =
          new CreateTaskResult(
              "t1",
              TaskStatus.WORKING,
              null,
              "2026-08-02T14:00:00Z",
              "2026-08-02T14:00:00Z",
              3600000L,
              2000L,
              TasksExtension.RESULT_TYPE_TASK);

      String json = mapper.writeValueAsString(result);

      assertThat(tree(json))
          .isEqualTo(
              tree(
                  "{\"taskId\":\"t1\",\"status\":\"working\",\"createdAt\":\"2026-08-02T14:00:00Z\","
                      + "\"lastUpdatedAt\":\"2026-08-02T14:00:00Z\",\"ttlMs\":3600000,"
                      + "\"pollIntervalMs\":2000,\"resultType\":\"task\"}"));
    }
  }

  @Nested
  class Get_task_result {

    @Test
    void serializes_the_working_status_variant_exactly() throws Exception {
      var result =
          new GetTaskResult(
              "t1",
              TaskStatus.WORKING,
              null,
              "2026-08-02T14:00:00Z",
              "2026-08-02T14:00:05Z",
              3600000L,
              2000L,
              null,
              null,
              null,
              ResultTypes.COMPLETE);

      String json = mapper.writeValueAsString(result);

      assertThat(tree(json))
          .isEqualTo(
              tree(
                  "{\"taskId\":\"t1\",\"status\":\"working\",\"createdAt\":\"2026-08-02T14:00:00Z\","
                      + "\"lastUpdatedAt\":\"2026-08-02T14:00:05Z\",\"ttlMs\":3600000,"
                      + "\"pollIntervalMs\":2000,\"resultType\":\"complete\"}"));
    }

    @Test
    void serializes_the_input_required_status_variant_exactly() throws Exception {
      Map<String, InputRequest> inputRequests = Map.of();
      var result =
          new GetTaskResult(
              "t1",
              TaskStatus.INPUT_REQUIRED,
              "waiting on user",
              "2026-08-02T14:00:00Z",
              "2026-08-02T14:00:05Z",
              3600000L,
              2000L,
              inputRequests,
              null,
              null,
              ResultTypes.COMPLETE);

      String json = mapper.writeValueAsString(result);

      assertThat(tree(json))
          .isEqualTo(
              tree(
                  "{\"taskId\":\"t1\",\"status\":\"input_required\","
                      + "\"statusMessage\":\"waiting on user\","
                      + "\"createdAt\":\"2026-08-02T14:00:00Z\","
                      + "\"lastUpdatedAt\":\"2026-08-02T14:00:05Z\",\"ttlMs\":3600000,"
                      + "\"pollIntervalMs\":2000,\"inputRequests\":{},\"resultType\":\"complete\"}"));
    }

    @Test
    void serializes_the_completed_status_variant_exactly() throws Exception {
      var toolResult = new CallToolResult(List.of(), null, null, "complete");
      var result =
          new GetTaskResult(
              "t1",
              TaskStatus.COMPLETED,
              null,
              "2026-08-02T14:00:00Z",
              "2026-08-02T14:00:10Z",
              3600000L,
              2000L,
              null,
              toolResult,
              null,
              ResultTypes.COMPLETE);

      String json = mapper.writeValueAsString(result);

      assertThat(tree(json))
          .isEqualTo(
              tree(
                  "{\"taskId\":\"t1\",\"status\":\"completed\","
                      + "\"createdAt\":\"2026-08-02T14:00:00Z\","
                      + "\"lastUpdatedAt\":\"2026-08-02T14:00:10Z\",\"ttlMs\":3600000,"
                      + "\"pollIntervalMs\":2000,\"result\":{\"content\":[],\"resultType\":\"complete\"},"
                      + "\"resultType\":\"complete\"}"));
    }

    @Test
    void serializes_the_failed_status_variant_exactly() throws Exception {
      var error = new JsonRpcErrorDetail(-32000, "boom");
      var result =
          new GetTaskResult(
              "t1",
              TaskStatus.FAILED,
              null,
              "2026-08-02T14:00:00Z",
              "2026-08-02T14:00:10Z",
              3600000L,
              2000L,
              null,
              null,
              error,
              ResultTypes.COMPLETE);

      String json = mapper.writeValueAsString(result);

      assertThat(tree(json))
          .isEqualTo(
              tree(
                  "{\"taskId\":\"t1\",\"status\":\"failed\","
                      + "\"createdAt\":\"2026-08-02T14:00:00Z\","
                      + "\"lastUpdatedAt\":\"2026-08-02T14:00:10Z\",\"ttlMs\":3600000,"
                      + "\"pollIntervalMs\":2000,\"error\":{\"code\":-32000,\"message\":\"boom\"},"
                      + "\"resultType\":\"complete\"}"));
    }

    @Test
    void serializes_the_cancelled_status_variant_exactly() throws Exception {
      var result =
          new GetTaskResult(
              "t1",
              TaskStatus.CANCELLED,
              null,
              "2026-08-02T14:00:00Z",
              "2026-08-02T14:00:10Z",
              3600000L,
              2000L,
              null,
              null,
              null,
              ResultTypes.COMPLETE);

      String json = mapper.writeValueAsString(result);

      assertThat(tree(json))
          .isEqualTo(
              tree(
                  "{\"taskId\":\"t1\",\"status\":\"cancelled\","
                      + "\"createdAt\":\"2026-08-02T14:00:00Z\","
                      + "\"lastUpdatedAt\":\"2026-08-02T14:00:10Z\",\"ttlMs\":3600000,"
                      + "\"pollIntervalMs\":2000,\"resultType\":\"complete\"}"));
    }
  }

  @Nested
  class Update_task_params {

    @Test
    void round_trips_an_elicit_result_input_response() throws Exception {
      Map<String, JsonNode> inputResponses =
          Map.of("elicit-1", mapper.valueToTree(new ElicitResult(ElicitAction.ACCEPT, null)));
      var original = new UpdateTaskParams("t1", inputResponses, null);

      String json = mapper.writeValueAsString(original);
      var deserialized = mapper.readValue(json, UpdateTaskParams.class);

      assertThat(deserialized.taskId()).isEqualTo("t1");
      ElicitResult response =
          mapper.treeToValue(deserialized.inputResponses().get("elicit-1"), ElicitResult.class);
      assertThat(response.action()).isEqualTo(ElicitAction.ACCEPT);
    }
  }

  @Nested
  class Update_task_result {

    @Test
    void always_carries_the_complete_result_type() throws Exception {
      var result = new UpdateTaskResult(ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(result);
      assertThat(tree(json)).isEqualTo(tree("{\"resultType\":\"complete\"}"));
    }
  }

  @Nested
  class Cancel_task_result {

    @Test
    void always_carries_the_complete_result_type() throws Exception {
      var result = new CancelTaskResult(ResultTypes.COMPLETE);
      String json = mapper.writeValueAsString(result);
      assertThat(tree(json)).isEqualTo(tree("{\"resultType\":\"complete\"}"));
    }
  }
}
