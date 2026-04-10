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
package com.callibrity.mocapi.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpPromptMethodsTest {

  private final McpPrompt testPrompt =
      new McpPrompt() {
        @Override
        public Prompt descriptor() {
          return new Prompt(
              "greet",
              null,
              "A greeting prompt",
              null,
              List.of(new PromptArgument("name", "The name to greet", true)));
        }

        @Override
        public GetPromptResult get(Map<String, String> arguments) {
          String name = arguments.getOrDefault("name", "World");
          return new GetPromptResult(
              "A greeting prompt",
              List.of(
                  new PromptMessage(
                      Role.USER, List.of(new TextContent("Hello, " + name + "!", null)))));
        }
      };

  private final PromptsRegistry registry = new PromptsRegistry(List.of(testPrompt), 50);
  private final McpPromptMethods methods = new McpPromptMethods(registry);

  @Test
  void listPromptsShouldReturnPrompts() {
    var response = methods.listPrompts(null);

    assertThat(response.prompts()).hasSize(1);
    assertThat(response.prompts().getFirst().name()).isEqualTo("greet");
    assertThat(response.prompts().getFirst().description()).isEqualTo("A greeting prompt");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void listPromptsShouldAcceptPaginatedParams() {
    var response = methods.listPrompts(new PaginatedRequestParams(null, null));

    assertThat(response.prompts()).hasSize(1);
  }

  @Test
  void getPromptShouldReturnMessages() {
    var response =
        methods.getPrompt(new GetPromptRequestParams("greet", Map.of("name", "Mocapi"), null));

    assertThat(response.messages()).hasSize(1);
    assertThat(response.messages().getFirst().role()).isEqualTo(Role.USER);
    assertThat(response.messages().getFirst().content()).hasSize(1);
    assertThat(response.messages().getFirst().content().getFirst()).isInstanceOf(TextContent.class);
    assertThat(((TextContent) response.messages().getFirst().content().getFirst()).text())
        .isEqualTo("Hello, Mocapi!");
  }

  @Test
  void getPromptWithUnknownNameShouldThrow() {
    assertThatThrownBy(
            () -> methods.getPrompt(new GetPromptRequestParams("unknown", Map.of(), null)))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Prompt not found: unknown");
  }

  @Test
  void argumentsShouldBePassedThrough() {
    var response1 =
        methods.getPrompt(new GetPromptRequestParams("greet", Map.of("name", "Alice"), null));
    var response2 =
        methods.getPrompt(new GetPromptRequestParams("greet", Map.of("name", "Bob"), null));

    assertThat(((TextContent) response1.messages().getFirst().content().getFirst()).text())
        .isEqualTo("Hello, Alice!");
    assertThat(((TextContent) response2.messages().getFirst().content().getFirst()).text())
        .isEqualTo("Hello, Bob!");
  }

  @Test
  void listPromptsShouldIncludeArguments() {
    var response = methods.listPrompts(null);

    var args = response.prompts().getFirst().arguments();
    assertThat(args).hasSize(1);
    assertThat(args.getFirst().name()).isEqualTo("name");
    assertThat(args.getFirst().required()).isTrue();
  }
}
