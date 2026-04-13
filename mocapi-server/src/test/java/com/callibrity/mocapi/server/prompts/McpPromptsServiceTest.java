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
package com.callibrity.mocapi.server.prompts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.prompts.McpPromptProvider;
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
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpPromptsServiceTest {

  private McpPromptsService service;

  private static McpPrompt prompt(String name, String description) {
    return new McpPrompt() {
      @Override
      public Prompt descriptor() {
        return new Prompt(
            name,
            name,
            description,
            null,
            List.of(new PromptArgument("arg1", "An argument", true)));
      }

      @Override
      public GetPromptResult get(Map<String, String> arguments) {
        String arg1 = arguments.getOrDefault("arg1", "default");
        return new GetPromptResult(
            description,
            List.of(new PromptMessage(Role.USER, new TextContent(name + ": " + arg1, null))));
      }
    };
  }

  @BeforeEach
  void setUp() {
    var provider =
        (McpPromptProvider)
            () -> List.of(prompt("beta-prompt", "Beta desc"), prompt("alpha-prompt", "Alpha desc"));
    service = new McpPromptsService(List.of(provider));
  }

  @Test
  void listPromptsReturnsSortedDescriptors() {
    var result = service.listPrompts(null);

    assertThat(result.prompts()).hasSize(2);
    assertThat(result.prompts().get(0).name()).isEqualTo("alpha-prompt");
    assertThat(result.prompts().get(1).name()).isEqualTo("beta-prompt");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void getPromptReturnsResult() {
    var params = new GetPromptRequestParams("alpha-prompt", Map.of("arg1", "hello"), null);

    var result = service.getPrompt(params);

    assertThat(result.description()).isEqualTo("Alpha desc");
    assertThat(result.messages()).hasSize(1);
    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("alpha-prompt: hello");
  }

  @Test
  void getPromptWithNullArgumentsUsesEmptyMap() {
    var params = new GetPromptRequestParams("alpha-prompt", null, null);

    var result = service.getPrompt(params);

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("alpha-prompt: default");
  }

  @Test
  void getPromptThrowsForUnknownName() {
    var params = new GetPromptRequestParams("nonexistent", null, null);

    assertThatThrownBy(() -> service.getPrompt(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Prompt nonexistent not found.");
  }

  @Test
  void lookupReturnsPrompt() {
    McpPrompt found = service.lookup("beta-prompt");
    assertThat(found.descriptor().name()).isEqualTo("beta-prompt");
  }

  @Test
  void lookupThrowsForUnknownName() {
    assertThatThrownBy(() -> service.lookup("missing"))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Prompt missing not found.");
  }

  @Test
  void isEmptyReturnsTrueWhenNoPrompts() {
    var emptyService = new McpPromptsService(List.of());
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void isEmptyReturnsFalseWhenPromptsExist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void paginationWorks() {
    List<McpPrompt> prompts =
        IntStream.range(0, 5)
            .mapToObj(i -> prompt(String.format("prompt-%03d", i), "desc " + i))
            .toList();
    var svc = new McpPromptsService(List.of(() -> prompts), 2);

    var page1 = svc.listPrompts(null);
    assertThat(page1.prompts()).hasSize(2);
    assertThat(page1.prompts().getFirst().name()).isEqualTo("prompt-000");
    assertThat(page1.nextCursor()).isNotNull();

    var page2 = svc.listPrompts(new PaginatedRequestParams(page1.nextCursor(), null));
    assertThat(page2.prompts()).hasSize(2);
    assertThat(page2.prompts().getFirst().name()).isEqualTo("prompt-002");
    assertThat(page2.nextCursor()).isNotNull();

    var page3 = svc.listPrompts(new PaginatedRequestParams(page2.nextCursor(), null));
    assertThat(page3.prompts()).hasSize(1);
    assertThat(page3.prompts().getFirst().name()).isEqualTo("prompt-004");
    assertThat(page3.nextCursor()).isNull();
  }

  @Test
  void invalidCursorThrowsException() {
    var params = new PaginatedRequestParams("not-valid-base64!!!", null);
    assertThatThrownBy(() -> service.listPrompts(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  @Test
  void outOfRangeCursorReturnsEmptyPage() {
    var largeOffset =
        java.util.Base64.getEncoder()
            .encodeToString(java.nio.ByteBuffer.allocate(4).putInt(100).array());
    var result = service.listPrompts(new PaginatedRequestParams(largeOffset, null));

    assertThat(result.prompts()).isEmpty();
    assertThat(result.nextCursor()).isNull();
  }
}
