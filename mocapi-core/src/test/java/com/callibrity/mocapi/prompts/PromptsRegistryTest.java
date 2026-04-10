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

import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.util.Cursors;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PromptsRegistryTest {

  private static PaginatedRequestParams cursor(String c) {
    return new PaginatedRequestParams(c, null);
  }

  private McpPrompt createPrompt(String name) {
    return new McpPrompt() {
      @Override
      public Prompt descriptor() {
        return new Prompt(
            name,
            null,
            "Description of " + name,
            null,
            List.of(new PromptArgument("arg1", "First argument", true)));
      }

      @Override
      public GetPromptResult get(Map<String, String> arguments) {
        return new GetPromptResult(
            descriptor().description(),
            List.of(
                new PromptMessage(
                    Role.USER, List.of(new TextContent("Hello from " + name, null)))));
      }
    };
  }

  @Test
  void isEmptyShouldReturnTrueWhenNoPrompts() {
    var registry = new PromptsRegistry(List.of(), 50);
    assertThat(registry.isEmpty()).isTrue();
  }

  @Test
  void isEmptyShouldReturnFalseWhenPromptsExist() {
    var registry = new PromptsRegistry(List.of(createPrompt("test")), 50);
    assertThat(registry.isEmpty()).isFalse();
  }

  @Test
  void shouldListAllPrompts() {
    var prompts = List.of(createPrompt("beta"), createPrompt("alpha"));
    var registry = new PromptsRegistry(prompts, 50);

    var response = registry.listPrompts(null);

    assertThat(response.prompts()).hasSize(2);
    assertThat(response.prompts().get(0).name()).isEqualTo("alpha");
    assertThat(response.prompts().get(1).name()).isEqualTo("beta");
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void shouldCollectPromptsFromList() {
    var prompts = List.of(createPrompt("alpha"), createPrompt("beta"));
    var registry = new PromptsRegistry(prompts, 50);

    var response = registry.listPrompts(null);

    assertThat(response.prompts()).hasSize(2);
  }

  @Test
  void shouldLookupByName() {
    var registry = new PromptsRegistry(List.of(createPrompt("my-prompt")), 50);

    var prompt = registry.lookup("my-prompt");

    assertThat(prompt.descriptor().name()).isEqualTo("my-prompt");
    assertThat(prompt.descriptor().description()).isEqualTo("Description of my-prompt");
  }

  @Test
  void shouldThrowWhenLookingUpUnknownName() {
    var registry = new PromptsRegistry(List.of(createPrompt("exists")), 50);

    assertThatThrownBy(() -> registry.lookup("unknown"))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Prompt not found: unknown");
  }

  @Test
  void shouldPaginatePrompts() {
    List<McpPrompt> prompts =
        IntStream.range(0, 5).mapToObj(i -> createPrompt(String.format("prompt-%03d", i))).toList();
    var registry = new PromptsRegistry(prompts, 2);

    var page1 = registry.listPrompts(null);
    assertThat(page1.prompts()).hasSize(2);
    assertThat(page1.prompts().get(0).name()).isEqualTo("prompt-000");
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = registry.listPrompts(cursor(page1.nextCursor()));
    assertThat(page2.prompts()).hasSize(2);
    assertThat(page2.prompts().get(0).name()).isEqualTo("prompt-002");
    assertThat(page2.nextCursor()).isNotNull();

    var page3 = registry.listPrompts(cursor(page2.nextCursor()));
    assertThat(page3.prompts()).hasSize(1);
    assertThat(page3.prompts().getFirst().name()).isEqualTo("prompt-004");
    assertThat(page3.nextCursor()).isNull();
  }

  @Test
  void shouldIterateThroughAllPages() {
    int totalPrompts = 7;
    List<McpPrompt> prompts =
        IntStream.range(0, totalPrompts)
            .mapToObj(i -> createPrompt(String.format("p-%03d", i)))
            .toList();
    var registry = new PromptsRegistry(prompts, 3);

    int totalRetrieved = 0;
    String nextCursor = null;
    do {
      var response = registry.listPrompts(cursor(nextCursor));
      totalRetrieved += response.prompts().size();
      nextCursor = response.nextCursor();
    } while (nextCursor != null);

    assertThat(totalRetrieved).isEqualTo(totalPrompts);
  }

  @Test
  void shouldThrowOnInvalidCursor() {
    var registry = new PromptsRegistry(List.of(createPrompt("a")), 2);

    assertThatThrownBy(() -> registry.listPrompts(cursor("not-valid-base64!!!")))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void outOfRangeCursorReturnsEmptyPage() {
    var registry = new PromptsRegistry(List.of(createPrompt("a")), 2);
    var response = registry.listPrompts(cursor(Cursors.encode(100)));
    assertThat(response.prompts()).isEmpty();
    assertThat(response.nextCursor()).isNull();
  }

  @Test
  void cursorEncodingRoundTrips() {
    assertThat(Cursors.decode(Cursors.encode(42))).isEqualTo(42);
  }

  @Test
  void listPromptsShouldIncludeArguments() {
    var registry = new PromptsRegistry(List.of(createPrompt("with-args")), 50);

    var response = registry.listPrompts(null);

    assertThat(response.prompts().getFirst().arguments()).hasSize(1);
    assertThat(response.prompts().getFirst().arguments().getFirst().name()).isEqualTo("arg1");
    assertThat(response.prompts().getFirst().arguments().getFirst().required()).isTrue();
  }
}
