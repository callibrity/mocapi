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

import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.PropertyPlaceholderHelper;

class SpringPromptTemplateTest {

  private static final PropertyPlaceholderHelper HELPER =
      new PropertyPlaceholderHelper("${", "}", ":", '\\', true);

  @Test
  void rendersSimpleInterpolation() {
    var template = new SpringPromptTemplate(Role.USER, "Hello ${name}!", HELPER);

    var result = template.render(Map.of("name", "Mocapi"));

    assertThat(firstText(result)).isEqualTo("Hello Mocapi!");
    assertThat(result.messages().getFirst().role()).isEqualTo(Role.USER);
  }

  @Test
  void bindsRolePassedAtConstruction() {
    var template = new SpringPromptTemplate(Role.ASSISTANT, "system directive", HELPER);

    assertThat(template.render(Map.of()).messages().getFirst().role()).isEqualTo(Role.ASSISTANT);
  }

  @Test
  void emitsExactlyOneMessage() {
    var template = new SpringPromptTemplate(Role.USER, "one", HELPER);

    var result = template.render(Map.of());

    assertThat(result.messages()).hasSize(1);
    assertThat(result.description()).isNull();
  }

  @Test
  void defaultValueIsUsedWhenArgumentMissing() {
    var template = new SpringPromptTemplate(Role.USER, "Hi ${name:stranger}", HELPER);

    assertThat(firstText(template.render(Map.of()))).isEqualTo("Hi stranger");
  }

  @Test
  void escapedPlaceholderRendersLiterally() {
    var template = new SpringPromptTemplate(Role.USER, "literal: \\${name}", HELPER);

    var result = template.render(Map.of("name", "ignored"));

    assertThat(firstText(result)).isEqualTo("literal: ${name}");
  }

  @Test
  void unresolvedPlaceholderIsLeftInPlace() {
    var template = new SpringPromptTemplate(Role.USER, "x=${missing}", HELPER);

    assertThat(firstText(template.render(Map.of()))).isEqualTo("x=${missing}");
  }

  @Test
  void nullArgumentsAreTreatedAsNoResolvableValues() {
    var template = new SpringPromptTemplate(Role.USER, "x=${a:default}", HELPER);

    assertThat(firstText(template.render(null))).isEqualTo("x=default");
  }

  @Test
  void doesNotEscapeHtmlSpecialCharacters() {
    var template = new SpringPromptTemplate(Role.USER, "${v}", HELPER);

    var result = template.render(Map.of("v", "<b>hi & bye</b>"));

    assertThat(firstText(result)).isEqualTo("<b>hi & bye</b>");
  }

  @Test
  void attachesDescriptionToRenderedResult() {
    var template = new SpringPromptTemplate(Role.USER, "summarize text", "${v}", HELPER);

    assertThat(template.render(Map.of("v", "ok")).description()).isEqualTo("summarize text");
  }

  @Test
  void descriptionDefaultsToNullWhenOmitted() {
    var template = new SpringPromptTemplate(Role.USER, "hi", HELPER);

    assertThat(template.render(Map.of()).description()).isNull();
  }

  @Test
  void emptyTemplateYieldsEmptyRender() {
    var template = new SpringPromptTemplate(Role.USER, "", HELPER);

    assertThat(firstText(template.render(Map.of()))).isEmpty();
  }

  @Test
  void reusableAcrossManyRenders() {
    var template = new SpringPromptTemplate(Role.USER, "Hi ${who}!", HELPER);

    assertThat(firstText(template.render(Map.of("who", "one")))).isEqualTo("Hi one!");
    assertThat(firstText(template.render(Map.of("who", "two")))).isEqualTo("Hi two!");
    assertThat(firstText(template.render(Map.of("who", "three")))).isEqualTo("Hi three!");
  }

  private static String firstText(GetPromptResult result) {
    return ((TextContent) result.messages().getFirst().content()).text();
  }
}
