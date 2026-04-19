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
package com.callibrity.mocapi.server.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GetPromptHandlerTest {

  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory();
  private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

  private List<GetPromptHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpPrompt.class).stream()
        .map(
            m ->
                GetPromptHandlers.build(
                    target, m, invokerFactory, conversionService, List.of(), s -> s))
        .toList();
  }

  enum Detail {
    BRIEF,
    STANDARD,
    DETAILED
  }

  public static class SummarizePrompt {
    @McpPrompt(
        name = "summarize",
        title = "Summarize",
        description = "Summarize text at a specified detail level")
    public GetPromptResult summarize(
        @Schema(description = "The text to summarize") String text,
        @Schema(description = "Detail level") @Nullable Detail detail) {
      var level = detail == null ? Detail.STANDARD : detail;
      return new GetPromptResult(
          "summary",
          List.of(new PromptMessage(Role.USER, new TextContent(level + ": " + text, null))));
    }
  }

  public static class WholeMapPrompt {
    @McpPrompt(name = "raw")
    public GetPromptResult raw(Map<String, String> args) {
      return new GetPromptResult(
          "raw", List.of(new PromptMessage(Role.USER, new TextContent(args.toString(), null))));
    }
  }

  public static class BadReturnPrompt {
    @McpPrompt(name = "oops")
    public String oops() {
      return "nope";
    }
  }

  @Test
  void generates_descriptor_from_annotation_and_parameters() {
    var handler = createHandlers(new SummarizePrompt()).getFirst();

    var d = handler.descriptor();
    assertThat(d.name()).isEqualTo("summarize");
    assertThat(d.title()).isEqualTo("Summarize");
    assertThat(d.description()).isEqualTo("Summarize text at a specified detail level");
    assertThat(d.arguments()).hasSize(2);
    assertThat(d.arguments().get(0).name()).isEqualTo("text");
    assertThat(d.arguments().get(0).required()).isTrue();
    assertThat(d.arguments().get(1).name()).isEqualTo("detail");
    assertThat(d.arguments().get(1).required()).isFalse();
    assertThat(handler.name()).isEqualTo("summarize");
    assertThat(handler.method().getName()).isEqualTo("summarize");
    assertThat(handler.bean()).isInstanceOf(SummarizePrompt.class);
  }

  @Test
  void invokes_method_with_converted_args() {
    var handler = createHandlers(new SummarizePrompt()).getFirst();

    var result = handler.get(Map.of("text", "hello world", "detail", "BRIEF"));

    assertThat(result.messages()).hasSize(1);
    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("BRIEF: hello world");
  }

  @Test
  void missing_optional_arg_comes_through_as_null() {
    var handler = createHandlers(new SummarizePrompt()).getFirst();

    var result = handler.get(Map.of("text", "hi"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("STANDARD: hi");
  }

  @Test
  void whole_map_parameter_is_not_declared_as_an_argument() {
    var handler = createHandlers(new WholeMapPrompt()).getFirst();

    assertThat(handler.descriptor().arguments()).isEmpty();
  }

  @Test
  void map_typed_param_receives_whole_arguments_map() {
    var handler = createHandlers(new WholeMapPrompt()).getFirst();

    var result = handler.get(Map.of("a", "1", "b", "2"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).contains("a=1").contains("b=2");
  }

  @Test
  void get_with_null_arguments_invokes_with_empty_map() {
    var handler = createHandlers(new WholeMapPrompt()).getFirst();

    var result = handler.get(null);

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("{}");
  }

  @Test
  void rejects_method_with_non_get_prompt_result_return_type() {
    var target = new BadReturnPrompt();
    assertThatThrownBy(() -> createHandlers(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("GetPromptResult");
  }

  @Test
  void customizer_receives_config_and_attached_interceptor_runs_during_invocation() {
    var bean = new SummarizePrompt();
    var captured = new ArrayList<GetPromptHandlerConfig>();
    var hits = new AtomicInteger();
    GetPromptHandlerCustomizer customizer =
        config -> {
          captured.add(config);
          config.interceptor(
              invocation -> {
                hits.incrementAndGet();
                return invocation.proceed();
              });
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpPrompt.class).getFirst();

    var handler =
        GetPromptHandlers.build(
            bean, method, invokerFactory, conversionService, List.of(customizer), s -> s);

    assertThat(captured).hasSize(1);
    var config = captured.getFirst();
    assertThat(config.descriptor().name()).isEqualTo("summarize");
    assertThat(config.method()).isEqualTo(method);
    assertThat(config.bean()).isSameAs(bean);

    handler.get(Map.of("text", "hello"));
    assertThat(hits).hasValue(1);
  }

  @Test
  void customizer_added_resolver_binds_custom_parameter() {
    var bean = new TenantPrompt();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpPrompt.class).getFirst();
    GetPromptHandlerCustomizer customizer = config -> config.resolver(new CurrentTenantResolver());

    var handler =
        GetPromptHandlers.build(
            bean, method, invokerFactory, conversionService, List.of(customizer), s -> s);
    var result = handler.get(Map.of());

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("tenant=acme");
  }

  @Test
  void customizer_added_resolver_wins_over_string_map_catchall() {
    var bean = new StringArgPrompt();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpPrompt.class).getFirst();
    GetPromptHandlerCustomizer customizer =
        config ->
            config.resolver(
                new ParameterResolver<Map<String, String>>() {
                  @Override
                  public boolean supports(ParameterInfo info) {
                    return info.resolvedType() == String.class;
                  }

                  @Override
                  public Object resolve(ParameterInfo info, Map<String, String> args) {
                    return "from-resolver";
                  }
                });

    var handler =
        GetPromptHandlers.build(
            bean, method, invokerFactory, conversionService, List.of(customizer), s -> s);
    var result = handler.get(Map.of("value", "from-args"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("from-resolver");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface CurrentTenant {}

  static final class CurrentTenantResolver implements ParameterResolver<Map<String, String>> {
    @Override
    public boolean supports(ParameterInfo info) {
      return info.parameter().isAnnotationPresent(CurrentTenant.class)
          && info.resolvedType() == String.class;
    }

    @Override
    public Object resolve(ParameterInfo info, Map<String, String> args) {
      return "acme";
    }
  }

  public static class TenantPrompt {
    @McpPrompt(name = "tenant")
    public GetPromptResult tenant(@CurrentTenant String tenant) {
      return new GetPromptResult(
          null, List.of(new PromptMessage(Role.USER, new TextContent("tenant=" + tenant, null))));
    }
  }

  public static class StringArgPrompt {
    @McpPrompt(name = "string-arg")
    public GetPromptResult echo(String value) {
      return new GetPromptResult(
          null, List.of(new PromptMessage(Role.USER, new TextContent(value, null))));
    }
  }

  @Test
  void guards_run_after_customizer_interceptors() {
    var bean = new StringArgPrompt();
    var customizerHits = new AtomicInteger();
    GetPromptHandlerCustomizer customizer =
        config -> {
          config.interceptor(
              invocation -> {
                customizerHits.incrementAndGet();
                return invocation.proceed();
              });
          config.guard(() -> new GuardDecision.Deny("no-access"));
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpPrompt.class).getFirst();
    var handler =
        GetPromptHandlers.build(
            bean, method, invokerFactory, conversionService, List.of(customizer), s -> s);

    assertThatThrownBy(() -> handler.get(Map.of("value", "hi")))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("no-access");
    assertThat(customizerHits).hasValue(1);
  }

  @Test
  void completion_candidates_include_enum_parameters() {
    var handler = createHandlers(new SummarizePrompt()).getFirst();

    assertThat(handler.completionCandidates()).hasSize(1);
    assertThat(handler.completionCandidates().getFirst().argumentName()).isEqualTo("detail");
    assertThat(handler.completionCandidates().getFirst().values())
        .containsExactly("BRIEF", "STANDARD", "DETAILED");
  }
}
