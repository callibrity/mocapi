/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end coverage of async tool handlers: the classifier picks the right inner mapper, the
 * await interceptor unwraps the future at innermost position, and the output-schema validator (if
 * enabled) sees the awaited value rather than the stage itself. Tests go through {@link
 * CallToolHandlers#build} so the whole chain is exercised, not just pieces in isolation.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AsyncToolHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  private CallToolHandler buildHandler(Object bean, boolean validateOutput) {
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();
    return CallToolHandlers.build(
        bean, method, generator, mapper, List.of(), s -> s, validateOutput);
  }

  @Nested
  class CompletionStage_of_structured_record {

    @Test
    void is_registered_with_an_object_output_schema_derived_from_inner_type() {
      var handler = buildHandler(new AsyncRecordTool(), false);
      assertThat(handler.descriptor().outputSchema()).isNotNull();
      assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    }

    @Test
    void handler_chain_installs_await_interceptor_innermost_so_validator_sees_unwrapped_value() {
      var handler = buildHandler(new AsyncRecordTool(), true);
      var interceptors = handler.describe().interceptors();
      assertThat(interceptors)
          .contains("Awaits the tool's CompletionStage return value")
          .contains("Validates tool return value against the tool's output JSON schema");
      // Output validation is registered BEFORE the await (outer to inner), so when the chain runs
      // the validator's proceed() returns the awaited value, not a CompletionStage.
      int awaitIdx = interceptors.indexOf("Awaits the tool's CompletionStage return value");
      int validatorIdx =
          interceptors.indexOf("Validates tool return value against the tool's output JSON schema");
      assertThat(validatorIdx).isLessThan(awaitIdx);
    }

    @Test
    void returns_awaited_record_instance_when_future_succeeds() {
      var handler = buildHandler(new AsyncRecordTool(), false);
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(Widget.class);
      assertThat(((Widget) result).name()).isEqualTo("ok");
    }

    @Test
    void output_validator_catches_schema_violation_in_awaited_value() {
      var handler = buildHandler(new AsyncNullFieldTool(), true);
      assertThatThrownBy(() -> handler.call(mapper.createObjectNode()))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcProtocol.INTERNAL_ERROR));
    }
  }

  @Nested
  class CompletableFuture_alias {

    @Test
    void is_treated_the_same_as_CompletionStage() {
      var handler = buildHandler(new AsyncCompletableFutureTool(), false);
      assertThat(handler.descriptor().outputSchema()).isNotNull();
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(Widget.class);
    }
  }

  @Nested
  class CompletionStage_of_Void {

    @Test
    void has_no_output_schema_and_returns_null_from_the_invoker_after_awaiting() {
      var handler = buildHandler(new AsyncVoidTool(), true);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(handler.describe().interceptors())
          .doesNotContain("Validates tool return value against the tool's output JSON schema");
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isNull();
    }
  }

  @Nested
  class CompletionStage_of_CallToolResult {

    @Test
    void has_no_output_schema_and_passes_through_the_awaited_result() {
      var handler = buildHandler(new AsyncCallToolResultTool(), true);
      assertThat(handler.descriptor().outputSchema()).isNull();
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(CallToolResult.class);
      assertThat(((TextContent) ((CallToolResult) result).content().getFirst()).text())
          .isEqualTo("hand-built");
    }
  }

  @Nested
  class CompletionStage_of_String {

    @Test
    void has_no_output_schema_and_returns_the_awaited_string() {
      var handler = buildHandler(new AsyncStringTool(), true);
      assertThat(handler.descriptor().outputSchema()).isNull();
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isEqualTo("async text");
    }
  }

  @Nested
  class Nested_CompletionStage_layers {

    @Test
    void doubly_nested_stage_unwraps_through_two_await_interceptors_to_yield_the_inner_value() {
      // CompletionStage<CompletionStage<Widget>>: two await interceptors stacked, each peels
      // one layer. Verifies the recursive design end-to-end through CallToolHandlers.build.
      var handler = buildHandler(new DoublyNestedStageTool(), false);
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(Widget.class);
      assertThat(((Widget) result).name()).isEqualTo("nested");
    }

    @Test
    void doubly_nested_stage_installs_two_await_interceptors_in_the_handler_chain() {
      var handler = buildHandler(new DoublyNestedStageTool(), false);
      long awaitCount =
          handler.describe().interceptors().stream()
              .filter(s -> s.equals("Awaits the tool's CompletionStage return value"))
              .count();
      assertThat(awaitCount).isEqualTo(2);
    }

    @Test
    void triply_nested_stage_unwraps_through_three_await_interceptors() {
      var handler = buildHandler(new TriplyNestedStageTool(), false);
      Object result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(Widget.class);
      assertThat(((Widget) result).name()).isEqualTo("deep");
      long awaitCount =
          handler.describe().interceptors().stream()
              .filter(s -> s.equals("Awaits the tool's CompletionStage return value"))
              .count();
      assertThat(awaitCount).isEqualTo(3);
    }

    @Test
    void doubly_nested_stage_runs_output_schema_validation_against_the_innermost_value() {
      // Output validator sits OUTSIDE both await interceptors, so when validateOutput=true the
      // schema check sees the unwrapped Widget — not a CompletionStage of any depth.
      var handler = buildHandler(new DoublyNestedNullFieldTool(), true);
      assertThatThrownBy(() -> handler.call(mapper.createObjectNode()))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcProtocol.INTERNAL_ERROR));
    }
  }

  @Nested
  class Async_failure_paths {

    @Test
    void domain_exception_in_failed_future_surfaces_unwrapped_to_the_caller() {
      var handler = buildHandler(new AsyncThrowingTool(), false);
      var args = mapper.createObjectNode();
      assertThatThrownBy(() -> handler.call(args))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("kaboom");
    }

    @Test
    void JsonRpcException_in_failed_future_preserves_its_code_through_await_interceptor() {
      // Simulates a guard-style denial happening inside async work. The await interceptor unwraps
      // CompletionException so McpToolsService's special FORBIDDEN handling can still fire.
      var handler = buildHandler(new AsyncForbiddenTool(), false);
      var args = mapper.createObjectNode();
      assertThatThrownBy(() -> handler.call(args))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e -> assertThat(e.getCode()).isEqualTo(JsonRpcErrorCodes.FORBIDDEN));
    }

    @Test
    void failed_future_still_runs_through_output_validator_only_if_successful() {
      // A tool that fails asynchronously never reaches the output validator — the await
      // interceptor rethrows before output validation can run.
      var handler = buildHandler(new AsyncThrowingTool(), true);
      var args = mapper.createObjectNode();
      assertThatThrownBy(() -> handler.call(args)).isInstanceOf(IllegalStateException.class);
    }
  }

  // --- test beans --------------------------------------------------------

  record Widget(String name, int count) {}

  static class AsyncRecordTool {
    @McpTool
    public CompletionStage<Widget> make() {
      return CompletableFuture.completedFuture(new Widget("ok", 1));
    }
  }

  static class AsyncCompletableFutureTool {
    @McpTool
    public CompletableFuture<Widget> make() {
      return CompletableFuture.completedFuture(new Widget("ok", 1));
    }
  }

  static class AsyncNullFieldTool {
    @McpTool
    public CompletionStage<Widget> make() {
      // `name` is a required field in the derived schema, so a null here violates output schema
      return CompletableFuture.completedFuture(new Widget(null, 1));
    }
  }

  static class AsyncVoidTool {
    @McpTool
    public CompletionStage<Void> run() {
      return CompletableFuture.completedFuture(null);
    }
  }

  static class AsyncCallToolResultTool {
    @McpTool
    public CompletionStage<CallToolResult> make() {
      return CompletableFuture.completedFuture(
          new CallToolResult(List.of(new TextContent("hand-built", null)), null, null));
    }
  }

  static class AsyncStringTool {
    @McpTool
    public CompletionStage<String> make() {
      return CompletableFuture.completedFuture("async text");
    }
  }

  static class AsyncThrowingTool {
    @McpTool
    public CompletionStage<Widget> make() {
      return CompletableFuture.failedFuture(new IllegalStateException("kaboom"));
    }
  }

  static class AsyncForbiddenTool {
    @McpTool
    public CompletionStage<Widget> make() {
      return CompletableFuture.failedFuture(
          new JsonRpcException(JsonRpcErrorCodes.FORBIDDEN, "nope"));
    }
  }

  static class DoublyNestedStageTool {
    @McpTool
    public CompletionStage<CompletionStage<Widget>> make() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Widget("nested", 2)));
    }
  }

  static class TriplyNestedStageTool {
    @McpTool
    public CompletionStage<CompletionStage<CompletionStage<Widget>>> make() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(
              CompletableFuture.completedFuture(new Widget("deep", 3))));
    }
  }

  static class DoublyNestedNullFieldTool {
    @McpTool
    public CompletionStage<CompletionStage<Widget>> make() {
      return CompletableFuture.completedFuture(
          CompletableFuture.completedFuture(new Widget(null, 1)));
    }
  }
}
