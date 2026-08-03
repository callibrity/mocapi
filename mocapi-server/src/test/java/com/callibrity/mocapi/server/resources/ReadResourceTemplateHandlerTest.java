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
package com.callibrity.mocapi.server.resources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.model.BlobResourceContents;
import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.completions.CompletionCandidate;
import com.callibrity.mocapi.server.guards.Guard;
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
import tools.jackson.databind.ObjectMapper;

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
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  public static class StringPathFixture {
    @McpResourceTemplate(uriTemplate = "test://greet/{name}", name = "Greet")
    public ReadResourceResult greet(String name) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://greet/" + name, "text/plain", "hi " + name)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  public static class WholeVarsMapFixture {
    @McpResourceTemplate(uriTemplate = "test://raw/{a}/{b}", name = "Raw")
    public ReadResourceResult raw(Map<String, String> vars) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://raw", "text/plain", vars.toString())),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  public static class DefaultedFixture {
    @McpResourceTemplate(uriTemplate = "test://defaulted/{x}")
    public ReadResourceResult defaulted(String x) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://defaulted/" + x, "text/plain", x)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  public static class BadTemplate {
    @McpResourceTemplate(uriTemplate = "test://bad/{x}")
    public int oops(String x) {
      return x.length();
    }
  }

  public static class StringTemplateFixture {
    @McpResourceTemplate(uriTemplate = "test://pages/{slug}", mimeType = "text/markdown")
    public String page(String slug) {
      return "# " + slug;
    }
  }

  public enum Stage {
    DEV,
    PROD
  }

  public static class EnumParamFixture {
    @McpResourceTemplate(uriTemplate = "test://stages/{stage}", name = "Stage")
    public ReadResourceResult stage(Stage stage) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://stages/" + stage, "text/plain", stage.name())),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  public static class ByteArrayTemplateFixture {
    @McpResourceTemplate(uriTemplate = "test://bytes/{id}", mimeType = "application/octet-stream")
    public byte[] bytes(String id) {
      return id.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  public static class ByteBufferTemplateFixture {
    @McpResourceTemplate(uriTemplate = "test://buffers/{id}", mimeType = "application/octet-stream")
    public java.nio.ByteBuffer buffer(String id) {
      return java.nio.ByteBuffer.wrap(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  public static class SpringResourceTemplateFixture {
    @McpResourceTemplate(uriTemplate = "test://spring/{id}", mimeType = "text/plain")
    public org.springframework.core.io.Resource resource(String id) {
      return new org.springframework.core.io.ByteArrayResource(
          id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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

    var result = handler.read("test://items/42", Map.of("id", "42"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("item 42");
    assertThat(content.uri()).isEqualTo("test://items/42");
  }

  @Test
  void read_with_null_path_variables_invokes_with_empty_map() {
    var handler = createHandlers(new StringPathFixture()).getFirst();

    var result = handler.read("test://greet/null", null);

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("hi null");
  }

  @Test
  void whole_vars_map_parameter_receives_all_path_variables_and_registers_no_completions() {
    var handler = createHandlers(new WholeVarsMapFixture()).getFirst();

    var result = handler.read("test://raw/1/2", Map.of("a", "1", "b", "2"));

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

    handler.read("test://items/1", Map.of("id", "1"));
    assertThat(hits).hasValue(1);
  }

  @Test
  void customizer_can_replace_the_resource_template_descriptor() {
    var bean = new Fixture();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResourceTemplate.class)
            .getFirst();
    ReadResourceTemplateHandlerCustomizer customizer =
        config ->
            config.descriptor(
                config.descriptor().withMeta(new ObjectMapper().createObjectNode().put("k", "v")));

    var handler =
        ReadResourceTemplateHandlers.build(
            bean, method, conversionService, List.of(customizer), s -> s);

    assertThat(handler.descriptor().meta().path("k").asString()).isEqualTo("v");
  }

  @Test
  void resource_template_method_with_unsupported_return_type_is_rejected() {
    var target = new BadTemplate();
    assertThatThrownBy(() -> createHandlers(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must return one of");
  }

  @Test
  void enum_typed_parameter_registers_completion_candidates_from_its_constants() {
    var handler = createHandlers(new EnumParamFixture()).getFirst();

    assertThat(handler.completionCandidates()).hasSize(1);
    assertThat(handler.completionCandidates().getFirst().values()).containsExactly("DEV", "PROD");
  }

  @Test
  void byte_array_return_is_wrapped_as_blob() {
    var handler = createHandlers(new ByteArrayTemplateFixture()).getFirst();

    var result = handler.read("test://bytes/42", Map.of("id", "42"));

    assertThat(result.contents().getFirst()).isInstanceOf(BlobResourceContents.class);
  }

  @Test
  void byte_buffer_return_is_wrapped_as_blob() {
    var handler = createHandlers(new ByteBufferTemplateFixture()).getFirst();

    var result = handler.read("test://buffers/42", Map.of("id", "42"));

    assertThat(result.contents().getFirst()).isInstanceOf(BlobResourceContents.class);
  }

  @Test
  void spring_resource_return_is_wrapped_per_resource_content_mode() {
    var handler = createHandlers(new SpringResourceTemplateFixture()).getFirst();

    var result = handler.read("test://spring/42", Map.of("id", "42"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("42");
  }

  @Test
  void string_return_is_wrapped_as_text_against_the_matched_uri() {
    var handler = createHandlers(new StringTemplateFixture()).getFirst();

    var content =
        (TextResourceContents)
            handler.read("test://pages/intro", Map.of("slug", "intro")).contents().getFirst();

    assertThat(content.uri()).isEqualTo("test://pages/intro");
    assertThat(content.mimeType()).isEqualTo("text/markdown");
    assertThat(content.text()).isEqualTo("# intro");
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
    handler.read("test://items/42", Map.of("id", "42"));

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
    var result = handler.read("test://tenants/7", Map.of("id", "7"));

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
    var result = handler.read("test://echo/from-vars", Map.of("value", "from-vars"));

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
                  "test://tenants/" + id, "text/plain", "tenant=" + tenant + " id=" + id)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
    }
  }

  public static class StringArgTemplate {
    @McpResourceTemplate(uriTemplate = "test://echo/{value}")
    public ReadResourceResult echo(String value) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://echo/" + value, "text/plain", value)),
          0L,
          CacheScope.PRIVATE,
          ResultTypes.COMPLETE);
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
    assertThatThrownBy(() -> handler.read("test://greet/World", args))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("no-access");
    assertThat(customizerHits).hasValue(1);
  }

  @Test
  void reader_only_handler_has_no_reflective_method_bean_or_invoker() {
    var descriptor =
        new ResourceTemplate("test://contrib/{id}", "Contrib", "Contrib", "text/plain");
    var candidates = List.of(new CompletionCandidate("id", List.of("a", "b")));
    Guard guard = GuardDecision.Allow::new;
    ResourceTemplateReader reader =
        (uri, variables) ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uri, "text/plain", "id=" + variables.get("id"))),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE);

    var handler = new ReadResourceTemplateHandler(descriptor, candidates, List.of(guard), reader);

    assertThat(handler.method()).isNull();
    assertThat(handler.bean()).isNull();
    assertThat(handler.descriptor()).isEqualTo(descriptor);
    assertThat(handler.uriTemplate()).isEqualTo("test://contrib/{id}");
    assertThat(handler.completionCandidates()).containsExactly(candidates.getFirst());
    assertThat(handler.guards()).containsExactly(guard);
  }

  @Test
  void reader_only_handler_dispatches_to_the_supplied_reader() {
    ResourceTemplateReader reader =
        (uri, variables) ->
            new ReadResourceResult(
                List.of(new TextResourceContents(uri, "text/plain", "id=" + variables.get("id"))),
                0L,
                CacheScope.PRIVATE,
                ResultTypes.COMPLETE);
    var handler =
        new ReadResourceTemplateHandler(
            new ResourceTemplate("test://contrib/{id}", "Contrib", "Contrib", "text/plain"),
            List.of(),
            List.of(),
            reader);

    var result = handler.read("test://contrib/9", Map.of("id", "9"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.uri()).isEqualTo("test://contrib/9");
    assertThat(content.text()).isEqualTo("id=9");
  }

  @Test
  void
      describe_on_reader_only_handler_reports_resource_template_kind_with_no_reflective_metadata() {
    ResourceTemplateReader reader =
        (uri, variables) ->
            new ReadResourceResult(List.of(), 0L, CacheScope.PRIVATE, ResultTypes.COMPLETE);
    var handler =
        new ReadResourceTemplateHandler(
            new ResourceTemplate("test://contrib/{id}", "Contrib", "Contrib", "text/plain"),
            List.of(),
            List.of(),
            reader);

    var descriptor = handler.describe();

    assertThat(descriptor.kind()).isEqualTo(HandlerKind.RESOURCE_TEMPLATE);
    assertThat(descriptor.declaringClassName()).isNull();
    assertThat(descriptor.methodName()).isNull();
    assertThat(descriptor.interceptors()).isEmpty();
  }
}
