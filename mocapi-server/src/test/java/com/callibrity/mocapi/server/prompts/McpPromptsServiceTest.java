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

import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpPromptsServiceTest {

  private McpPromptsService service;

  private static GetPromptHandler handler(String name, String description) {
    Prompt descriptor =
        new Prompt(
            name,
            name,
            description,
            null,
            List.of(new PromptArgument("arg1", "An argument", true)));
    return new GetPromptHandler(
        descriptor,
        null,
        null,
        args -> {
          @SuppressWarnings("unchecked")
          Map<String, String> typed = (Map<String, String>) args;
          String arg1 = typed.getOrDefault("arg1", "default");
          return new GetPromptResult(
              description,
              List.of(new PromptMessage(Role.USER, new TextContent(name + ": " + arg1, null))));
        },
        List.of(),
        List.of());
  }

  @BeforeEach
  void setUp() {
    service =
        new McpPromptsService(
            List.of(handler("beta-prompt", "Beta desc"), handler("alpha-prompt", "Alpha desc")));
  }

  @Test
  void list_prompts_returns_sorted_descriptors() {
    var result = service.listPrompts(null);

    assertThat(result.prompts()).hasSize(2);
    assertThat(result.prompts().get(0).name()).isEqualTo("alpha-prompt");
    assertThat(result.prompts().get(1).name()).isEqualTo("beta-prompt");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void get_prompt_returns_result() {
    var params = new GetPromptRequestParams("alpha-prompt", Map.of("arg1", "hello"), null);

    var result = service.getPrompt(params);

    assertThat(result.description()).isEqualTo("Alpha desc");
    assertThat(result.messages()).hasSize(1);
    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("alpha-prompt: hello");
  }

  @Test
  void get_prompt_with_null_arguments_uses_empty_map() {
    var params = new GetPromptRequestParams("alpha-prompt", null, null);

    var result = service.getPrompt(params);

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("alpha-prompt: default");
  }

  @Test
  void get_prompt_throws_for_unknown_name() {
    var params = new GetPromptRequestParams("nonexistent", null, null);

    assertThatThrownBy(() -> service.getPrompt(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Prompt nonexistent not found.");
  }

  @Test
  void lookup_returns_handler() {
    GetPromptHandler found = service.lookup("beta-prompt");
    assertThat(found.descriptor().name()).isEqualTo("beta-prompt");
  }

  @Test
  void lookup_throws_for_unknown_name() {
    assertThatThrownBy(() -> service.lookup("missing"))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Prompt missing not found.");
  }

  @Test
  void is_empty_returns_true_when_no_prompts() {
    var emptyService = new McpPromptsService(List.of());
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void is_empty_returns_false_when_prompts_exist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void pagination_works() {
    List<GetPromptHandler> prompts =
        IntStream.range(0, 5)
            .mapToObj(i -> handler(String.format("prompt-%03d", i), "desc " + i))
            .toList();
    var svc = new McpPromptsService(prompts, 2);

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
  void invalid_cursor_throws_exception() {
    var params = new PaginatedRequestParams("not-valid-base64!!!", null);
    assertThatThrownBy(() -> service.listPrompts(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Invalid cursor");
  }

  private static GetPromptHandler guardedHandler(String name, Guard guard) {
    Prompt descriptor = new Prompt(name, name, name + " desc", null, List.of());
    return new GetPromptHandler(
        descriptor,
        null,
        null,
        args -> new GetPromptResult(name, List.of()),
        List.of(),
        List.of(guard));
  }

  @Test
  void denied_get_throws_json_rpc_forbidden_with_reason() {
    var guarded =
        new McpPromptsService(
            List.of(guardedHandler("blocked", () -> new GuardDecision.Deny("nope"))));
    assertThatThrownBy(
            () -> guarded.getPrompt(new GetPromptRequestParams("blocked", Map.of(), null)))
        .isInstanceOf(JsonRpcException.class)
        .matches(e -> ((JsonRpcException) e).getCode() == JsonRpcErrorCodes.FORBIDDEN)
        .hasMessageContaining("Forbidden")
        .hasMessageContaining("nope");
  }

  @Test
  void denied_prompt_is_absent_from_list() {
    var guarded =
        new McpPromptsService(
            List.of(
                guardedHandler("visible", () -> new GuardDecision.Allow()),
                guardedHandler("hidden", () -> new GuardDecision.Deny("no"))));
    var names = guarded.listPrompts(null).prompts().stream().map(Prompt::name).toList();
    assertThat(names).contains("visible").doesNotContain("hidden");
  }

  @Test
  void mixed_guards_with_one_deny_hides_and_rejects() {
    var handler = guardedHandler("mixed", () -> new GuardDecision.Deny("x"));
    // Add a second guard that allows — spec says any deny wins
    var withExtra =
        new GetPromptHandler(
            handler.descriptor(),
            null,
            null,
            args -> new GetPromptResult("mixed", List.of()),
            List.of(),
            List.of(() -> new GuardDecision.Allow(), () -> new GuardDecision.Deny("blocked")));
    var guarded = new McpPromptsService(List.of(withExtra));

    assertThat(guarded.listPrompts(null).prompts()).isEmpty();
    assertThatThrownBy(() -> guarded.getPrompt(new GetPromptRequestParams("mixed", Map.of(), null)))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("blocked");
  }

  @Test
  void out_of_range_cursor_returns_empty_page() {
    var largeOffset =
        java.util.Base64.getEncoder()
            .encodeToString(java.nio.ByteBuffer.allocate(4).putInt(100).array());
    var result = service.listPrompts(new PaginatedRequestParams(largeOffset, null));

    assertThat(result.prompts()).isEmpty();
    assertThat(result.nextCursor()).isNull();
  }
}
