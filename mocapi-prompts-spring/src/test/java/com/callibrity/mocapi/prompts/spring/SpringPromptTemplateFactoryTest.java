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
package com.callibrity.mocapi.prompts.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SpringPromptTemplateFactoryTest {

  private final SpringPromptTemplateFactory factory = new SpringPromptTemplateFactory();

  @Test
  void returns_spring_prompt_template_instance() {
    assertThat(factory.create(Role.USER, "hello")).isInstanceOf(SpringPromptTemplate.class);
  }

  @Test
  void produces_templates_that_render_against_arguments() {
    var template = factory.create(Role.USER, "Hello ${name}!");

    var result = template.render(Map.of("name", "Mocapi"));

    assertThat(((TextContent) result.messages().getFirst().content()).text())
        .isEqualTo("Hello Mocapi!");
  }

  @Test
  void propagates_description_to_rendered_results() {
    var template = factory.create(Role.USER, "a summary prompt", "${v}");

    var result = template.render(Map.of("v", "hi"));

    assertThat(result.description()).isEqualTo("a summary prompt");
  }

  @Test
  void two_arg_overload_omits_description() {
    var template = factory.create(Role.USER, "${v}");

    assertThat(template.render(Map.of("v", "hi")).description()).isNull();
  }

  @Test
  void default_helper_supports_backslash_escape() {
    var template = factory.create(Role.USER, "literal: \\${name}");

    assertThat(
            ((TextContent) template.render(Map.of("name", "x")).messages().getFirst().content())
                .text())
        .isEqualTo("literal: ${name}");
  }

  @Test
  void supports_default_value_syntax() {
    var template = factory.create(Role.USER, "Hi ${name:stranger}");

    assertThat(((TextContent) template.render(Map.of()).messages().getFirst().content()).text())
        .isEqualTo("Hi stranger");
  }
}
