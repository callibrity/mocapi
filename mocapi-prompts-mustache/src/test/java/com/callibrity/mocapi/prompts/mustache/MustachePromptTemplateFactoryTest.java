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
import org.junit.jupiter.api.Test;

class MustachePromptTemplateFactoryTest {

  private final MustachePromptTemplateFactory factory = new MustachePromptTemplateFactory();

  @Test
  void returnsMustachePromptTemplateInstance() {
    assertThat(factory.create(Role.USER, "hello")).isInstanceOf(MustachePromptTemplate.class);
  }

  @Test
  void producesTemplatesThatRenderAgainstArguments() {
    var template = factory.create(Role.USER, "Hello {{name}}!");

    var result = template.render(Map.of("name", "Mocapi"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("Hello Mocapi!");
    assertThat(result.messages().getFirst().role()).isEqualTo(Role.USER);
  }

  @Test
  void defaultCompilerLeavesHtmlUnescaped() {
    var template = factory.create(Role.USER, "{{v}}");

    var result = template.render(Map.of("v", "<b>hi & bye</b>"));

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("<b>hi & bye</b>");
  }

  @Test
  void defaultCompilerTreatsMissingValuesAsEmpty() {
    var template = factory.create(Role.USER, "x={{missing}}");

    var result = template.render(Map.of());

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("x=");
  }

  @Test
  void propagatesDescriptionToRenderedResults() {
    var template = factory.create(Role.USER, "a summary prompt", "{{v}}");

    var result = template.render(Map.of("v", "hi"));

    assertThat(result.description()).isEqualTo("a summary prompt");
  }

  @Test
  void twoArgOverloadOmitsDescription() {
    var template = factory.create(Role.USER, "{{v}}");

    var result = template.render(Map.of("v", "hi"));

    assertThat(result.description()).isNull();
  }

  @Test
  void invalidTemplateIsWrappedInIllegalArgumentException() {
    assertThatThrownBy(() -> factory.create(Role.USER, "{{#section}}no end tag"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Mustache");
  }
}
