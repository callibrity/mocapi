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
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.HelloTool;
import com.callibrity.mocapi.server.tools.util.InteractiveTool;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import jakarta.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CallToolHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  private List<CallToolHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpTool.class).stream()
        .map(m -> CallToolHandlers.build(target, m, generator, mapper, List.of(), s -> s, false))
        .toList();
  }

  @Test
  void default_annotation_should_generate_correct_metadata() {
    var handlers = createHandlers(new HelloTool());
    assertThat(handlers).hasSize(1);

    var handler = handlers.getFirst();
    assertThat(handler.descriptor().name()).isEqualTo("hello-tool.say-hello");
    assertThat(handler.descriptor().title()).isEqualTo("Hello Tool - Say Hello");
    assertThat(handler.descriptor().description()).isEqualTo("Hello Tool - Say Hello");
    assertThat(handler.descriptor().inputSchema().get("type").asString()).isEqualTo("object");
    assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    assertThat(handler.name()).isEqualTo("hello-tool.say-hello");
    assertThat(handler.method().getName()).isEqualTo("sayHello");
    assertThat(handler.bean()).isInstanceOf(HelloTool.class);
  }

  @Test
  void describe_flattens_kind_declaring_class_method_and_interceptors_from_invoker() {
    var handler = createHandlers(new HelloTool()).getFirst();
    var descriptor = handler.describe();
    assertThat(descriptor.kind()).isEqualTo(HandlerKind.TOOL);
    assertThat(descriptor.declaringClassName()).isEqualTo(HelloTool.class.getName());
    assertThat(descriptor.methodName()).isEqualTo("sayHello");
    assertThat(descriptor.interceptors())
        .contains("Validates tool arguments against the tool's input JSON schema");
  }

  @Test
  void custom_annotation_should_return_correct_metadata() {
    var handlers = createHandlers(new CustomizedTool());
    assertThat(handlers).hasSize(1);

    var handler = handlers.getFirst();
    assertThat(handler.descriptor().name()).isEqualTo("custom-name");
    assertThat(handler.descriptor().title()).isEqualTo("Custom Title");
    assertThat(handler.descriptor().description()).isEqualTo("Custom description");
  }

  @Test
  void should_call_simple_tool_correctly() {
    var handler = createHandlers(new HelloTool()).getFirst();
    var result = handler.call(mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(result).isNotNull();
    var json = mapper.valueToTree(result);
    assertThat(json.get("message").stringValue()).isEqualTo("Hello, Mocapi!");
  }

  @Test
  void interactive_tool_should_have_output_schema() {
    var handler = createHandlers(new InteractiveTool()).getFirst();
    assertThat(handler.descriptor().outputSchema()).isNotNull();
    assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void void_tool_should_have_null_output_schema() {
    var handlers = createHandlers(new BoxedVoidTool());
    assertThat(handlers).hasSize(1);
    assertThat(handlers.getFirst().descriptor().outputSchema()).isNull();
  }

  @Test
  void mcp_tool_params_with_other_non_context_param_should_throw() {
    var target = new InvalidMixedParamsTool();
    assertThatThrownBy(() -> createHandlers(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("@McpToolParams");
  }

  @Test
  void mcp_tool_params_with_context_param_only_should_succeed() {
    var handlers = createHandlers(new ValidParamsWithContextTool());
    assertThat(handlers).hasSize(1);
  }

  @Test
  void customizer_receives_config_and_attached_interceptor_runs_during_invocation() {
    var bean = new HelloTool();
    var captured = new ArrayList<CallToolHandlerConfig>();
    var hits = new AtomicInteger();
    CallToolHandlerCustomizer customizer =
        config -> {
          captured.add(config);
          config.observationInterceptor(
              invocation -> {
                hits.incrementAndGet();
                return invocation.proceed();
              });
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();

    var handler =
        CallToolHandlers.build(bean, method, generator, mapper, List.of(customizer), s -> s, false);

    assertThat(captured).hasSize(1);
    var config = captured.getFirst();
    assertThat(config.descriptor().name()).isEqualTo(handler.descriptor().name());
    assertThat(config.method()).isEqualTo(method);
    assertThat(config.bean()).isSameAs(bean);

    handler.call(mapper.createObjectNode().put("name", "World"));
    assertThat(hits).hasValue(1);
  }

  @Test
  void customizer_contributions_to_every_stratum_land_in_outer_to_inner_order() {
    var bean = new HelloTool();
    var order = new ArrayList<String>();
    MethodInterceptor<JsonNode> correlation =
        invocation -> {
          order.add("correlation");
          return invocation.proceed();
        };
    MethodInterceptor<JsonNode> observation =
        invocation -> {
          order.add("observation");
          return invocation.proceed();
        };
    MethodInterceptor<JsonNode> audit =
        invocation -> {
          order.add("audit");
          return invocation.proceed();
        };
    MethodInterceptor<JsonNode> validation =
        invocation -> {
          order.add("validation");
          return invocation.proceed();
        };
    MethodInterceptor<JsonNode> invocation =
        inv -> {
          order.add("invocation");
          return inv.proceed();
        };
    CallToolHandlerCustomizer customizer =
        config ->
            config
                .correlationInterceptor(correlation)
                .observationInterceptor(observation)
                .auditInterceptor(audit)
                .validationInterceptor(validation)
                .invocationInterceptor(invocation);
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();

    var handler =
        CallToolHandlers.build(bean, method, generator, mapper, List.of(customizer), s -> s, false);
    handler.call(mapper.createObjectNode().put("name", "World"));

    assertThat(order)
        .containsExactly("correlation", "observation", "audit", "validation", "invocation");
  }

  @Test
  void customizer_added_resolver_binds_custom_parameter() {
    var bean = new TenantTool();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();
    CallToolHandlerCustomizer customizer = config -> config.resolver(new CurrentTenantResolver());

    var handler =
        CallToolHandlers.build(bean, method, generator, mapper, List.of(customizer), s -> s, false);
    var result = handler.call(mapper.createObjectNode());

    assertThat(mapper.valueToTree(result).get("tenant").stringValue()).isEqualTo("acme");
  }

  @Test
  void customizer_added_resolver_wins_over_jackson_catchall() {
    var bean = new StringArgTool();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();
    // Resolver claims any String parameter and supplies a fixed value — if ordering were wrong
    // the catch-all Jackson3ParameterResolver would read "input" from the arguments tree instead.
    CallToolHandlerCustomizer customizer =
        config ->
            config.resolver(
                info ->
                    info.resolvedType() == String.class
                        ? Optional.of(arguments -> "from-resolver")
                        : Optional.empty());

    var handler =
        CallToolHandlers.build(bean, method, generator, mapper, List.of(customizer), s -> s, false);
    var result = handler.call(mapper.createObjectNode().put("input", "from-json"));

    assertThat(mapper.valueToTree(result).get("value").stringValue()).isEqualTo("from-resolver");
  }

  @Test
  void guards_run_after_customizer_interceptors_and_before_schema_validation() {
    var bean = new HelloTool();
    var customizerHits = new AtomicInteger();
    var bodyHits = new AtomicInteger();
    CallToolHandlerCustomizer customizer =
        config -> {
          config.observationInterceptor(
              invocation -> {
                customizerHits.incrementAndGet();
                return invocation.proceed();
              });
          config.guard(() -> new GuardDecision.Deny("no-scope"));
          config.observationInterceptor(
              invocation -> {
                bodyHits.incrementAndGet();
                return invocation.proceed();
              });
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();
    var handler =
        CallToolHandlers.build(bean, method, generator, mapper, List.of(customizer), s -> s, false);

    // Invalid args (missing required "name") would trip schema validation — but guards evaluate
    // first, so we get FORBIDDEN rather than a schema error.
    var args = mapper.createObjectNode();
    assertThatThrownBy(() -> handler.call(args))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("no-scope");

    // Both customizer-contributed interceptors ran before the guard decision aborted the call,
    // proving the guard interceptor sits after all customizer interceptors.
    assertThat(customizerHits).hasValue(1);
    assertThat(bodyHits).hasValue(1);
  }

  @Test
  void no_guards_means_no_guard_interceptor_overhead() {
    var bean = new HelloTool();
    // Guard list is empty, so GuardEvaluationInterceptor should not be wired in.
    // We can only assert indirectly: the call succeeds with no denial.
    CallToolHandlerCustomizer customizer =
        config -> config.observationInterceptor(MethodInvocation::proceed);
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();
    var handler =
        CallToolHandlers.build(bean, method, generator, mapper, List.of(customizer), s -> s, false);

    var result = handler.call(mapper.createObjectNode().put("name", "World"));
    assertThat(result).isNotNull();
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface CurrentTenant {}

  static final class CurrentTenantResolver implements ParameterResolver<JsonNode> {
    @Override
    public Optional<ParameterResolver.Binding<JsonNode>> bind(ParameterInfo info) {
      if (!info.parameter().isAnnotationPresent(CurrentTenant.class)
          || info.resolvedType() != String.class) {
        return Optional.empty();
      }
      return Optional.of(arguments -> "acme");
    }
  }

  record TenantResult(String tenant) {}

  static class TenantTool {
    @McpTool
    public TenantResult read(@CurrentTenant @Nullable String tenant) {
      return new TenantResult(tenant);
    }
  }

  record StringResult(String value) {}

  static class StringArgTool {
    @McpTool
    public StringResult echo(String input) {
      return new StringResult(input);
    }
  }

  record WorkResult(String input) {}

  static class CustomizedTool {
    @McpTool(name = "custom-name", title = "Custom Title", description = "Custom description")
    public WorkResult doWork(String input) {
      return new WorkResult(input);
    }
  }

  static class BoxedVoidTool {
    @McpTool
    public Void doNothing(String input) {
      return null;
    }
  }

  static class InvalidMixedParamsTool {
    @McpTool
    public String doWork(@McpToolParams String params, String extra) {
      return params;
    }
  }

  record SimpleParams(String value) {}

  static class ValidParamsWithContextTool {
    @McpTool
    public WorkResult doWork(@McpToolParams SimpleParams params, McpToolContext ctx) {
      return new WorkResult(params.value());
    }
  }

  @Nested
  class When_output_validation_is_enabled {

    private CallToolHandler buildHandler(Object bean, boolean validateOutput) {
      var method =
          MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpTool.class).getFirst();
      return CallToolHandlers.build(
          bean, method, generator, mapper, List.of(), s -> s, validateOutput);
    }

    @Test
    void installs_output_interceptor_when_flag_is_true_and_tool_declares_output_schema() {
      var handler = buildHandler(new Widgets.OkTool(), true);
      assertThat(handler.describe().interceptors())
          .contains("Validates tool return value against the tool's output JSON schema");
    }

    @Test
    void omits_output_interceptor_when_flag_is_false() {
      var handler = buildHandler(new Widgets.OkTool(), false);
      assertThat(handler.describe().interceptors())
          .contains("Validates tool arguments against the tool's input JSON schema")
          .doesNotContain("Validates tool return value against the tool's output JSON schema");
    }

    @Test
    void omits_output_interceptor_for_void_tool_even_when_flag_is_true() {
      var handler = buildHandler(new BoxedVoidTool(), true);
      assertThat(handler.describe().interceptors())
          .contains("Validates tool arguments against the tool's input JSON schema")
          .doesNotContain("Validates tool return value against the tool's output JSON schema");
    }

    @Test
    void happy_path_passes_through_when_return_value_matches_schema() {
      var handler = buildHandler(new Widgets.OkTool(), true);
      var result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(Widgets.Widget.class);
    }

    @Test
    void throws_internal_error_when_return_value_violates_schema() {
      var handler = buildHandler(new Widgets.NullFieldTool(), true);
      assertThatThrownBy(() -> handler.call(mapper.createObjectNode()))
          .isInstanceOfSatisfying(
              JsonRpcException.class,
              e ->
                  assertThat(e.getCode())
                      .isEqualTo(com.callibrity.ripcurl.core.JsonRpcProtocol.INTERNAL_ERROR));
    }

    @Test
    void does_not_throw_when_flag_is_false_even_if_return_value_would_violate_schema() {
      var handler = buildHandler(new Widgets.NullFieldTool(), false);
      var result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(Widgets.Widget.class);
    }

    @Test
    void does_not_install_output_validator_for_call_tool_result_returning_tool() {
      // CallToolResult-returning tools advertise no outputSchema — there's no inferable schema
      // for structuredContent the author will construct manually — so the validator is never
      // installed, regardless of the flag.
      var handler = buildHandler(new Widgets.CallToolResultOkTool(), true);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(handler.describe().interceptors())
          .doesNotContain("Validates tool return value against the tool's output JSON schema");
    }

    @Test
    void passes_through_call_tool_result_author_built_regardless_of_flag() {
      var handler = buildHandler(new Widgets.CallToolResultOkTool(), true);
      var result = handler.call(mapper.createObjectNode());
      assertThat(result).isInstanceOf(CallToolResult.class);
    }

    @Test
    void does_not_install_output_validator_for_char_sequence_returning_tool() {
      var handler = buildHandler(new Widgets.StringReturningTool(), true);
      assertThat(handler.descriptor().outputSchema()).isNull();
      assertThat(handler.describe().interceptors())
          .doesNotContain("Validates tool return value against the tool's output JSON schema");
    }
  }

  static class Widgets {
    record Widget(String name, int count) {}

    static class OkTool {
      @McpTool
      public Widget make() {
        return new Widget("ok", 1);
      }
    }

    static class NullFieldTool {
      @McpTool
      public Widget make() {
        return new Widget(null, 1);
      }
    }

    static class CallToolResultOkTool {
      @McpTool
      public CallToolResult make() {
        var mapper = new ObjectMapper();
        var structured = mapper.createObjectNode().put("name", "ok").put("count", 1);
        return new CallToolResult(List.of(new TextContent("ok", null)), null, structured);
      }
    }

    static class TextOnlyCallToolResultTool {
      @McpTool
      public CallToolResult make() {
        return new CallToolResult(List.of(new TextContent("just text", null)), null, null);
      }
    }

    static class StringReturningTool {
      @McpTool
      public String make() {
        return "hello";
      }
    }
  }
}
