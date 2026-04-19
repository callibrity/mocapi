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
class ReadResourceTemplateHandlerTest {

  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory();
  private final List<ParameterResolver<? super Map<String, String>>> templateResolvers =
      List.of(new StringMapArgResolver(DefaultConversionService.getSharedInstance()));

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
    var handler =
        ReadResourceTemplateHandlers.discover(
                new Fixture(), invokerFactory, templateResolvers, List.of(), s -> s)
            .getFirst();

    assertThat(handler.uriTemplate()).isEqualTo("test://items/{id}");
    assertThat(handler.descriptor().name()).isEqualTo("Item");
    assertThat(handler.descriptor().mimeType()).isEqualTo("text/plain");
    assertThat(handler.method().getName()).isEqualTo("item");
    assertThat(handler.bean()).isInstanceOf(Fixture.class);
  }

  @Test
  void read_invokes_underlying_method_with_converted_path_variables() {
    var handler =
        ReadResourceTemplateHandlers.discover(
                new Fixture(), invokerFactory, templateResolvers, List.of(), s -> s)
            .getFirst();

    var result = handler.read(Map.of("id", "42"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("item 42");
    assertThat(content.uri()).isEqualTo("test://items/42");
  }

  @Test
  void read_with_null_path_variables_invokes_with_empty_map() {
    var handler =
        ReadResourceTemplateHandlers.discover(
                new StringPathFixture(), invokerFactory, templateResolvers, List.of(), s -> s)
            .getFirst();

    var result = handler.read(null);

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).isEqualTo("hi null");
  }

  @Test
  void whole_vars_map_parameter_receives_all_path_variables_and_registers_no_completions() {
    var handler =
        ReadResourceTemplateHandlers.discover(
                new WholeVarsMapFixture(), invokerFactory, templateResolvers, List.of(), s -> s)
            .getFirst();

    var result = handler.read(Map.of("a", "1", "b", "2"));

    var content = (TextResourceContents) result.contents().getFirst();
    assertThat(content.text()).contains("a=1").contains("b=2");
    assertThat(handler.completionCandidates()).isEmpty();
  }

  @Test
  void name_and_description_default_when_annotation_values_are_blank() {
    var handler =
        ReadResourceTemplateHandlers.discover(
                new DefaultedFixture(), invokerFactory, templateResolvers, List.of(), s -> s)
            .getFirst();

    assertThat(handler.descriptor().name()).isNotBlank();
    assertThat(handler.descriptor().description()).isEqualTo(handler.descriptor().name());
    assertThat(handler.descriptor().mimeType()).isNull();
  }

  @Test
  void resource_template_method_with_non_result_return_type_is_rejected() {
    var target = new BadTemplate();
    assertThatThrownBy(
            () ->
                ReadResourceTemplateHandlers.discover(
                    target, invokerFactory, templateResolvers, List.of(), s -> s))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ReadResourceResult");
  }
}
