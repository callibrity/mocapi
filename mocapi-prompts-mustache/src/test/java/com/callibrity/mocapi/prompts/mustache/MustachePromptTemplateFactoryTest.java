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
package com.callibrity.mocapi.prompts.mustache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MustachePromptTemplateFactoryTest {

  private final MustachePromptTemplateFactory factory = new MustachePromptTemplateFactory();

  @Test
  void returns_mustache_prompt_template_instance() {
    assertThat(factory.create(Role.USER, "hello")).isInstanceOf(MustachePromptTemplate.class);
  }

  @Test
  void produces_templates_that_render_against_arguments() {
    var template = factory.create(Role.USER, "Hello {{name}}!");

    var result = template.render(Map.of("name", "Mocapi"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("Hello Mocapi!");
    assertThat(result.messages().getFirst().role()).isEqualTo(Role.USER);
  }

  @Test
  void default_compiler_leaves_html_unescaped() {
    var template = factory.create(Role.USER, "{{v}}");

    var result = template.render(Map.of("v", "<b>hi & bye</b>"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("<b>hi & bye</b>");
  }

  @Test
  void default_compiler_treats_missing_values_as_empty() {
    var template = factory.create(Role.USER, "x={{missing}}");

    var result = template.render(Map.of());

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("x=");
  }

  @Test
  void propagates_description_to_rendered_results() {
    var template = factory.create(Role.USER, "a summary prompt", "{{v}}");

    var result = template.render(Map.of("v", "hi"));

    assertThat(result.description()).isEqualTo("a summary prompt");
  }

  @Test
  void two_arg_overload_omits_description() {
    var template = factory.create(Role.USER, "{{v}}");

    var result = template.render(Map.of("v", "hi"));

    assertThat(result.description()).isNull();
  }

  @Test
  void invalid_template_is_wrapped_in_illegal_argument_exception() {
    assertThatThrownBy(() -> factory.create(Role.USER, "{{#section}}no end tag"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Mustache");
  }
}
