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
package com.callibrity.mocapi.server.resources.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.resources.ResourceMethod;
import com.callibrity.mocapi.api.resources.ResourceTemplateMethod;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.util.StringMapArgResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.core.convert.support.DefaultConversionService;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnotationResourceTest {

  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory(List.of());
  private final List<ParameterResolver<? super Map<String, String>>> templateResolvers =
      List.of(new StringMapArgResolver(DefaultConversionService.getSharedInstance()));

  public static class Fixture {
    @ResourceMethod(
        uri = "test://hello",
        name = "Hello",
        description = "hello",
        mimeType = "text/plain")
    public ReadResourceResult hello() {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://hello", "text/plain", "hi")));
    }

    @ResourceTemplateMethod(
        uriTemplate = "test://items/{id}",
        name = "Item",
        mimeType = "text/plain")
    public ReadResourceResult item(int id) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://items/" + id, "text/plain", "item " + id)));
    }
  }

  public static class StringPathFixture {
    @ResourceTemplateMethod(uriTemplate = "test://greet/{name}", name = "Greet")
    public ReadResourceResult greet(String name) {
      return new ReadResourceResult(
          List.of(new TextResourceContents("test://greet/" + name, "text/plain", "hi " + name)));
    }
  }

  public static class BadResource {
    @ResourceMethod(uri = "test://bad")
    public String oops() {
      return "nope";
    }
  }

  public static class BadTemplate {
    @ResourceTemplateMethod(uriTemplate = "test://bad/{x}")
    public String oops(String x) {
      return x;
    }
  }

  @Test
  void fixed_resource_builds_descriptor_and_invokes() {
    var resource = ResourceServiceScanner.createResources(invokerFactory, new Fixture()).getFirst();

    assertThat(resource.descriptor().uri()).isEqualTo("test://hello");
    assertThat(resource.descriptor().name()).isEqualTo("Hello");
    assertThat(resource.descriptor().description()).isEqualTo("hello");
    assertThat(resource.descriptor().mimeType()).isEqualTo("text/plain");

    var result = resource.read();
    assertThat(result.contents()).hasSize(1);
  }

  @Test
  void template_builds_descriptor_and_invokes_with_converted_path_variables() {
    var template =
        ResourceServiceScanner.createResourceTemplates(
                invokerFactory, templateResolvers, new Fixture())
            .getFirst();

    assertThat(template.descriptor().uriTemplate()).isEqualTo("test://items/{id}");
    assertThat(template.descriptor().name()).isEqualTo("Item");

    var result = template.read(Map.of("id", "42"));
    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("item 42");
    assertThat(content.uri()).isEqualTo("test://items/42");
  }

  @Test
  void template_read_with_null_path_variables_invokes_with_empty_map() {
    var template =
        ResourceServiceScanner.createResourceTemplates(
                invokerFactory, templateResolvers, new StringPathFixture())
            .getFirst();

    var result = template.read(null);

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("hi null");
  }

  @Test
  void resource_method_with_non_result_return_type_is_rejected() {
    var target = new BadResource();
    assertThatThrownBy(() -> ResourceServiceScanner.createResources(invokerFactory, target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadResourceResult");
  }

  @Test
  void template_method_with_non_result_return_type_is_rejected() {
    var target = new BadTemplate();
    assertThatThrownBy(
            () ->
                ResourceServiceScanner.createResourceTemplates(
                    invokerFactory, templateResolvers, target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadResourceResult");
  }
}
