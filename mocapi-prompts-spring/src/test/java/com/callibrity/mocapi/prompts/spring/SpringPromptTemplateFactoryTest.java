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
import org.junit.jupiter.api.Test;

class SpringPromptTemplateFactoryTest {

  private final SpringPromptTemplateFactory factory = new SpringPromptTemplateFactory();

  @Test
  void returnsSpringPromptTemplateInstance() {
    assertThat(factory.create(Role.USER, "hello")).isInstanceOf(SpringPromptTemplate.class);
  }

  @Test
  void producesTemplatesThatRenderAgainstArguments() {
    var template = factory.create(Role.USER, "Hello ${name}!");

    var result = template.render(Map.of("name", "Mocapi"));

    assertThat(((TextContent) result.messages().getFirst().content()).text())
        .isEqualTo("Hello Mocapi!");
  }

  @Test
  void propagatesDescriptionToRenderedResults() {
    var template = factory.create(Role.USER, "a summary prompt", "${v}");

    var result = template.render(Map.of("v", "hi"));

    assertThat(result.description()).isEqualTo("a summary prompt");
  }

  @Test
  void twoArgOverloadOmitsDescription() {
    var template = factory.create(Role.USER, "${v}");

    assertThat(template.render(Map.of("v", "hi")).description()).isNull();
  }

  @Test
  void defaultHelperSupportsBackslashEscape() {
    var template = factory.create(Role.USER, "literal: \\${name}");

    assertThat(
            ((TextContent) template.render(Map.of("name", "x")).messages().getFirst().content())
                .text())
        .isEqualTo("literal: ${name}");
  }

  @Test
  void supportsDefaultValueSyntax() {
    var template = factory.create(Role.USER, "Hi ${name:stranger}");

    assertThat(((TextContent) template.render(Map.of()).messages().getFirst().content()).text())
        .isEqualTo("Hi stranger");
  }
}
