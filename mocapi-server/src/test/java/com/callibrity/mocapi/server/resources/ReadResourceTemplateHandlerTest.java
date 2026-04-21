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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolver;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReadResourceTemplateHandlerTest {

  private final ConversionService conversionService = DefaultConversionService.getSharedInstance();

  private List<ReadResourceTemplateHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpResourceTemplate.class)
        .stream()
        .map(
            m ->
                ReadResourceTemplateHandlers.build(target, m, conversionService, List.of(), s -> s))
        .toList();
  }

  public static class Fixture {
    @McpResourceTemplate(uriTemplate = "test://items/{id}", name = "Item", mimeType = "text/plain")
    public ReadResourceResult item(int id) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)));
    }
  }

  public static class StringPathFixture {
    @McpResourceTemplate(uriTemplate = "test://greet/{name}", name = "Greet")
    public ReadResourceResult greet(String name) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://greet/" + name, "text/plain", "hi " + name)));
    }
  }

  public static class WholeVarsMapFixture {
    @McpResourceTemplate(uriTemplate = "test://raw/{a}/{b}", name = "Raw")
    public ReadResourceResult raw(Map<String, String> vars) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://raw", "text/plain", vars.toString())));
    }
  }

  public static class DefaultedFixture {
    @McpResourceTemplate(uriTemplate = "test://defaulted/{x}")
    public ReadResourceResult defaulted(String x) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://defaulted/" + x, "text/plain", x)));
    }
  }

  public static class BadTemplate {
    @McpResourceTemplate(uriTemplate = "test://bad/{x}")
    public String oops(String x) {
      return x;
    }
  }

  @Test
  void discover_builds_handler_from_annotated_method() {
    var handler = createHandlers(new Fixture()).getFirst();

    assertThat(handler.uriTemplate()).isEqualTo("test://items/{id}");
    assertThat(handler.descriptor().name()).isEqualTo("Item");
    assertThat(handler.descriptor().mimeType()).isEqualTo("text/plain");
    assertThat(handler.method().getName()).isEqualTo("item");
    assertThat(handler.bean()).isInstanceOf(Fixture.class);
  }

  @Test
  void describe_flattens_kind_declaring_class_method_and_interceptors_from_invoker() {
    var handler = createHandlers(new Fixture()).getFirst();
    var descriptor = handler.describe();
    assertThat(descriptor.kind()).isEqualTo(HandlerKind.RESOURCE_TEMPLATE);
    assertThat(descriptor.declaringClassName()).isEqualTo(Fixture.class.getName());
    assertThat(descriptor.methodName()).isEqualTo("item");
    assertThat(descriptor.interceptors()).isNotNull();
  }

  @Test
  void read_invokes_underlying_method_with_converted_path_variables() {
    var handler = createHandlers(new Fixture()).getFirst();

    var result = handler.read(Map.of("id", "42"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("item 42");
    assertThat(content.uri()).isEqualTo("test://items/42");
  }

  @Test
  void read_with_null_path_variables_invokes_with_empty_map() {
    var handler = createHandlers(new StringPathFixture()).getFirst();

    var result = handler.read(null);

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("hi null");
  }

  @Test
  void whole_vars_map_parameter_receives_all_path_variables_and_registers_no_completions() {
    var handler = createHandlers(new WholeVarsMapFixture()).getFirst();

    var result = handler.read(Map.of("a", "1", "b", "2"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).contains("a=1").contains("b=2");
    assertThat(handler.completionCandidates()).isEmpty();
  }

  @Test
  void name_and_description_default_when_annotation_values_are_blank() {
    var handler = createHandlers(new DefaultedFixture()).getFirst();

    assertThat(handler.descriptor().name()).isNotBlank();
    assertThat(handler.descriptor().description()).isEqualTo(handler.descriptor().name());
    assertThat(handler.descriptor().mimeType()).isNull();
  }

  @Test
  void customizer_receives_config_and_attached_interceptor_runs_during_invocation() {
    var bean = new Fixture();
    var captured = new ArrayList<ReadResourceTemplateHandlerConfig>();
    var hits = new AtomicInteger();
    ReadResourceTemplateHandlerCustomizer customizer =
        config -> {
          captured.add(config);
          config.observationInterceptor(
              invocation -> {
                hits.incrementAndGet();
                return invocation.proceed();
              });
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResourceTemplate.class)
            .getFirst();

    var handler =
        ReadResourceTemplateHandlers.build(
            bean, method, conversionService, List.of(customizer), s -> s);

    assertThat(captured).hasSize(1);
    var config = captured.getFirst();
    assertThat(config.descriptor().uriTemplate()).isEqualTo("test://items/{id}");
    assertThat(config.method()).isEqualTo(method);
    assertThat(config.bean()).isSameAs(bean);

    handler.read(Map.of("id", "1"));
    assertThat(hits).hasValue(1);
  }

  @Test
  void resource_template_method_with_non_result_return_type_is_rejected() {
    var target = new BadTemplate();
    assertThatThrownBy(() -> createHandlers(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadResourceResult");
  }

  @Test
  void customizer_contributions_to_every_stratum_land_in_outer_to_inner_order() {
    var bean = new Fixture();
    var order = new ArrayList<String>();
    MethodInterceptor<Map<String, String>> correlation =
        invocation -> {
          order.add("correlation");
          return invocation.proceed();
        };
    MethodInterceptor<Map<String, String>> observation =
        invocation -> {
          order.add("observation");
          return invocation.proceed();
        };
    MethodInterceptor<Map<String, String>> audit =
        invocation -> {
          order.add("audit");
          return invocation.proceed();
        };
    MethodInterceptor<Map<String, String>> validation =
        invocation -> {
          order.add("validation");
          return invocation.proceed();
        };
    MethodInterceptor<Map<String, String>> invocation =
        inv -> {
          order.add("invocation");
          return inv.proceed();
        };
    ReadResourceTemplateHandlerCustomizer customizer =
        config ->
            config
                .correlationInterceptor(correlation)
                .observationInterceptor(observation)
                .auditInterceptor(audit)
                .validationInterceptor(validation)
                .invocationInterceptor(invocation);
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResourceTemplate.class)
            .getFirst();

    var handler =
        ReadResourceTemplateHandlers.build(
            bean, method, conversionService, List.of(customizer), s -> s);
    handler.read(Map.of("id", "42"));

    assertThat(order)
        .containsExactly("correlation", "observation", "audit", "validation", "invocation");
  }

  @Test
  void customizer_added_resolver_binds_custom_parameter() {
    var bean = new TenantTemplate();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResourceTemplate.class)
            .getFirst();
    ReadResourceTemplateHandlerCustomizer customizer =
        config -> config.resolver(new CurrentTenantResolver());

    var handler =
        ReadResourceTemplateHandlers.build(
            bean, method, conversionService, List.of(customizer), s -> s);
    var result = handler.read(Map.of("id", "7"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("tenant=acme id=7");
  }

  @Test
  void customizer_added_resolver_wins_over_string_map_catchall() {
    var bean = new StringArgTemplate();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResourceTemplate.class)
            .getFirst();
    ReadResourceTemplateHandlerCustomizer customizer =
        config ->
            config.resolver(
                info ->
                    info.resolvedType() == String.class
                        ? Optional.of(vars -> "from-resolver")
                        : Optional.empty());

    var handler =
        ReadResourceTemplateHandlers.build(
            bean, method, conversionService, List.of(customizer), s -> s);
    var result = handler.read(Map.of("value", "from-vars"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("from-resolver");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface CurrentTenant {}

  static final class CurrentTenantResolver implements ParameterResolver<Map<String, String>> {
    @Override
    public Optional<ParameterResolver.Binding<Map<String, String>>> bind(ParameterInfo info) {
      if (!info.parameter().isAnnotationPresent(CurrentTenant.class)
          || info.resolvedType() != String.class) {
        return Optional.empty();
      }
      return Optional.of(vars -> "acme");
    }
  }

  public static class TenantTemplate {
    @McpResourceTemplate(uriTemplate = "test://tenants/{id}")
    public ReadResourceResult read(@CurrentTenant String tenant, String id) {
      return new ReadResourceResult(
          List.of(
              new TextResourceContents(
                  "test://tenants/" + id, "text/plain", "tenant=" + tenant + " id=" + id)));
    }
  }

  public static class StringArgTemplate {
    @McpResourceTemplate(uriTemplate = "test://echo/{value}")
    public ReadResourceResult echo(String value) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://echo/" + value, "text/plain", value)));
    }
  }

  @Test
  void guards_run_after_customizer_interceptors() {
    var bean = new StringPathFixture();
    var customizerHits = new AtomicInteger();
    ReadResourceTemplateHandlerCustomizer customizer =
        config -> {
          config.observationInterceptor(
              invocation -> {
                customizerHits.incrementAndGet();
                return invocation.proceed();
              });
          config.guard(() -> new GuardDecision.Deny("no-access"));
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResourceTemplate.class)
            .getFirst();
    var handler =
        ReadResourceTemplateHandlers.build(
            bean, method, conversionService, List.of(customizer), s -> s);

    var args = Map.of("name", "World");
    assertThatThrownBy(() -> handler.read(args))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("no-access");
    assertThat(customizerHits).hasValue(1);
  }
}
