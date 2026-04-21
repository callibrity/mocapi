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

import com.callibrity.mocapi.api.resources.McpResource;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolver;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ReadResourceHandlerTest {

  private List<ReadResourceHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpResource.class).stream()
        .map(m -> ReadResourceHandlers.build(target, m, List.of(), s -> s))
        .toList();
  }

  public static class Fixture {
    @McpResource(
        uri = "test://hello",
        name = "Hello",
        description = "hello",
        mimeType = "text/plain")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")));
    }
  }

  public static class DefaultedFixture {
    @McpResource(uri = "test://defaulted")
    public ReadResourceResult defaulted() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://defaulted", "text/plain", "ok")));
    }
  }

  public static class BadResource {
    @McpResource(uri = "test://bad")
    public String oops() {
      return "nope";
    }
  }

  @Test
  void discover_builds_handler_from_annotated_method() {
    var handler = createHandlers(new Fixture()).getFirst();

    assertThat(handler.uri()).isEqualTo("test://hello");
    assertThat(handler.descriptor().name()).isEqualTo("Hello");
    assertThat(handler.descriptor().description()).isEqualTo("hello");
    assertThat(handler.descriptor().mimeType()).isEqualTo("text/plain");
    assertThat(handler.method().getName()).isEqualTo("hello");
    assertThat(handler.bean()).isInstanceOf(Fixture.class);
  }

  @Test
  void describe_flattens_kind_declaring_class_method_and_interceptors_from_invoker() {
    var handler = createHandlers(new Fixture()).getFirst();
    var descriptor = handler.describe();
    assertThat(descriptor.kind()).isEqualTo(HandlerKind.RESOURCE);
    assertThat(descriptor.declaringClassName()).isEqualTo(Fixture.class.getName());
    assertThat(descriptor.methodName()).isEqualTo("hello");
    assertThat(descriptor.interceptors()).isNotNull();
  }

  @Test
  void read_invokes_underlying_method() {
    var handler = createHandlers(new Fixture()).getFirst();

    var result = handler.read();

    assertThat(result.contents()).hasSize(1);
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("hi");
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
    var captured = new ArrayList<ReadResourceHandlerConfig>();
    var hits = new AtomicInteger();
    ReadResourceHandlerCustomizer customizer =
        config -> {
          captured.add(config);
          config.observationInterceptor(
              invocation -> {
                hits.incrementAndGet();
                return invocation.proceed();
              });
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResource.class).getFirst();

    var handler = ReadResourceHandlers.build(bean, method, List.of(customizer), s -> s);

    assertThat(captured).hasSize(1);
    var config = captured.getFirst();
    assertThat(config.descriptor().uri()).isEqualTo("test://hello");
    assertThat(config.method()).isEqualTo(method);
    assertThat(config.bean()).isSameAs(bean);

    handler.read();
    assertThat(hits).hasValue(1);
  }

  @Test
  void customizer_added_resolver_binds_custom_parameter() {
    var bean = new TenantResource();
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResource.class).getFirst();
    ReadResourceHandlerCustomizer customizer =
        config -> config.resolver(new CurrentTenantResolver());

    var handler = ReadResourceHandlers.build(bean, method, List.of(customizer), s -> s);
    var result = handler.read();

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("tenant=acme");
  }

  @Test
  void guards_run_after_customizer_interceptors() {
    var bean = new Fixture();
    var customizerHits = new AtomicInteger();
    ReadResourceHandlerCustomizer customizer =
        config -> {
          config.observationInterceptor(
              invocation -> {
                customizerHits.incrementAndGet();
                return invocation.proceed();
              });
          config.guard(() -> new GuardDecision.Deny("no-access"));
        };
    var method =
        MethodUtils.getMethodsListWithAnnotation(bean.getClass(), McpResource.class).getFirst();
    var handler = ReadResourceHandlers.build(bean, method, List.of(customizer), s -> s);

    assertThatThrownBy(handler::read)
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("no-access");
    assertThat(customizerHits).hasValue(1);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface CurrentTenant {}

  static final class CurrentTenantResolver implements ParameterResolver<Object> {
    @Override
    public Optional<ParameterResolver.Binding<Object>> bind(ParameterInfo info) {
      if (!info.parameter().isAnnotationPresent(CurrentTenant.class)
          || info.resolvedType() != String.class) {
        return Optional.empty();
      }
      return Optional.of(args -> "acme");
    }
  }

  public static class TenantResource {
    @McpResource(uri = "test://tenant")
    public ReadResourceResult read(@CurrentTenant String tenant) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://tenant", "text/plain", "tenant=" + tenant)));
    }
  }

  @Test
  void resource_method_with_non_result_return_type_is_rejected() {
    var target = new BadResource();
    assertThatThrownBy(() -> createHandlers(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadResourceResult");
  }
}
