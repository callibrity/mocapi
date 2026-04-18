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

import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MustachePromptTemplateTest {

  private static final Mustache.Compiler COMPILER =
      Mustache.compiler().escapeHTML(false).defaultValue("");

  @Test
  void renders_simple_interpolation() {
    var template = newTemplate(Role.USER, "Hello {{name}}!");

    var result = template.render(Map.of("name", "Mocapi"));

    assertThat(firstText(result)).isEqualTo("Hello Mocapi!");
    assertThat(result.messages().getFirst().role()).isEqualTo(Role.USER);
  }

  @Test
  void binds_role_passed_at_construction() {
    var template = newTemplate(Role.ASSISTANT, "system directive");

    var result = template.render(Map.of());

    assertThat(result.messages().getFirst().role()).isEqualTo(Role.ASSISTANT);
  }

  @Test
  void emits_exactly_one_message() {
    var template = newTemplate(Role.USER, "one");

    var result = template.render(Map.of());

    assertThat(result.messages()).hasSize(1);
    assertThat(result.description()).isNull();
  }

  @Test
  void does_not_escape_html_special_characters_in_values() {
    var template = newTemplate(Role.USER, "{{greeting}}");

    var result = template.render(Map.of("greeting", "<b>hi & bye</b>"));

    assertThat(firstText(result)).isEqualTo("<b>hi & bye</b>");
  }

  @Test
  void missing_value_renders_as_empty_string() {
    var template = newTemplate(Role.USER, "A={{a}},B={{b}}");

    var result = template.render(Map.of("a", "1"));

    assertThat(firstText(result)).isEqualTo("A=1,B=");
  }

  @Test
  void null_arguments_are_treated_as_empty_map() {
    var template = newTemplate(Role.USER, "A={{a}}");

    var result = template.render(null);

    assertThat(firstText(result)).isEqualTo("A=");
  }

  @Test
  void compiled_template_is_reusable_across_renders() {
    var template = newTemplate(Role.USER, "Hi {{who}}!");

    assertThat(firstText(template.render(Map.of("who", "one")))).isEqualTo("Hi one!");
    assertThat(firstText(template.render(Map.of("who", "two")))).isEqualTo("Hi two!");
    assertThat(firstText(template.render(Map.of("who", "three")))).isEqualTo("Hi three!");
  }

  @Test
  void attaches_description_to_rendered_result() {
    var template =
        new MustachePromptTemplate(Role.USER, "summarize text", COMPILER.compile("{{x}}"));

    var result = template.render(Map.of("x", "ok"));

    assertThat(result.description()).isEqualTo("summarize text");
  }

  @Test
  void description_defaults_to_null_when_omitted() {
    var template = new MustachePromptTemplate(Role.USER, COMPILER.compile("hi"));

    assertThat(template.render(Map.of()).description()).isNull();
  }

  @Test
  void empty_template_yields_empty_render() {
    var template = newTemplate(Role.USER, "");

    assertThat(firstText(template.render(Map.of()))).isEmpty();
  }

  @Test
  void respects_custom_compiler_settings() {
    // Compiler that *does* escape HTML — verifies the template uses whatever compiler it was built
    // with, not a hard-coded setting inside the template class.
    Template escaping = Mustache.compiler().compile("{{v}}");
    var template = new MustachePromptTemplate(Role.USER, escaping);

    var result = template.render(Map.of("v", "<b>hi</b>"));

    assertThat(firstText(result)).isEqualTo("&lt;b&gt;hi&lt;/b&gt;");
  }

  private static MustachePromptTemplate newTemplate(Role role, String source) {
    return new MustachePromptTemplate(role, COMPILER.compile(source));
  }

  private static String firstText(GetPromptResult result) {
    return ((TextContent) result.messages().getFirst().content()).text();
  }
}
