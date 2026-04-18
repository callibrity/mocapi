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
package com.callibrity.mocapi.api.prompts.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PromptTemplateFactoryTest {

  @Test
  void default_two_arg_create_delegates_to_three_arg_with_null_description() {
    AtomicReference<String> capturedDescription = new AtomicReference<>("sentinel");
    PromptTemplateFactory factory =
        (role, description, template) -> {
          capturedDescription.set(description);
          return args ->
              new GetPromptResult(
                  description, List.of(new PromptMessage(role, new TextContent(template, null))));
        };

    PromptTemplate template = factory.create(Role.USER, "hello");

    assertThat(capturedDescription.get()).isNull();
    var result = template.render(Map.of());
    assertThat(result.description()).isNull();
    assertThat(((TextContent) result.messages().getFirst().content()).text()).isEqualTo("hello");
    assertThat(result.messages().getFirst().role()).isEqualTo(Role.USER);
  }

  @Test
  void three_arg_create_preserves_description() {
    PromptTemplateFactory factory =
        (role, description, template) ->
            args ->
                new GetPromptResult(
                    description, List.of(new PromptMessage(role, new TextContent(template, null))));

    PromptTemplate template = factory.create(Role.ASSISTANT, "a description", "body");

    assertThat(template.render(Map.of()).description()).isEqualTo("a description");
  }
}
