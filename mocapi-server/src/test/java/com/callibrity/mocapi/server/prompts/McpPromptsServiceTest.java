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

import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.GetPromptRequestParams;
import com.callibrity.mocapi.model.GetPromptResult;
import com.callibrity.mocapi.model.PaginatedRequestParams;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.PromptMessage;
import com.callibrity.mocapi.model.ResultTypes;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpPromptsServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static MrtrElicitationEngine engine() {
    return new MrtrElicitationEngine(
        RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, MAPPER), MAPPER);
  }

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
              List.of(new PromptMessage(Role.USER, new TextContent(name + ": " + arg1, null))),
              ResultTypes.COMPLETE);
        },
        List.of(),
        List.of());
  }

  @BeforeEach
  void setUp() {
    service =
        new McpPromptsService(
            List.of(handler("beta-prompt", "Beta desc"), handler("alpha-prompt", "Alpha desc")),
            engine());
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
    var params =
        new GetPromptRequestParams("alpha-prompt", Map.of("arg1", "hello"), null, null, null);

    var result = (GetPromptResult) service.getPrompt(params);

    assertThat(result.description()).isEqualTo("Alpha desc");
    assertThat(result.messages()).hasSize(1);
    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("alpha-prompt: hello");
  }

  @Test
  void get_prompt_with_null_arguments_uses_empty_map() {
    var params = new GetPromptRequestParams("alpha-prompt", null, null, null, null);

    var result = (GetPromptResult) service.getPrompt(params);

    var content = (TextContent) result.messages().getFirst().content();
    assertThat(content.text()).isEqualTo("alpha-prompt: default");
  }

  @Test
  void get_prompt_throws_for_unknown_name() {
    var params = new GetPromptRequestParams("nonexistent", null, null, null, null);

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
    var emptyService = new McpPromptsService(List.of(), engine());
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
    var svc = new McpPromptsService(prompts, engine(), 2);

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
        args -> new GetPromptResult(name, List.of(), ResultTypes.COMPLETE),
        List.of(),
        List.of(guard));
  }

  @Test
  void denied_prompt_is_absent_from_list() {
    var guarded =
        new McpPromptsService(
            List.of(
                guardedHandler("visible", GuardDecision.Allow::new),
                guardedHandler("hidden", () -> new GuardDecision.Deny("no"))),
            engine());
    var names = guarded.listPrompts(null).prompts().stream().map(Prompt::name).toList();
    assertThat(names).contains("visible").doesNotContain("hidden");
  }

  @Test
  void mixed_guards_with_one_deny_hides_from_list() {
    // List-time filtering: any deny hides the descriptor. Call-time denial moved to
    // GuardEvaluationInterceptor and is covered by its unit tests.
    var descriptor = new Prompt("mixed", "mixed", "mixed desc", null, List.of());
    var handler =
        new GetPromptHandler(
            descriptor,
            null,
            null,
            args -> new GetPromptResult("mixed", List.of(), ResultTypes.COMPLETE),
            List.of(),
            List.of(GuardDecision.Allow::new, () -> new GuardDecision.Deny("blocked")));
    var guarded = new McpPromptsService(List.of(handler), engine());

    assertThat(guarded.listPrompts(null).prompts()).isEmpty();
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

  @Nested
  class Cache_directives {

    @Test
    void list_prompts_carries_conservative_defaults_when_unconfigured() {
      var result = service.listPrompts(null);

      assertThat(result.ttlMs()).isZero();
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PRIVATE);
      assertThat(result.resultType()).isEqualTo(ResultTypes.COMPLETE);
    }

    @Test
    void list_prompts_carries_configured_list_ttl_and_scope() {
      var settings = new CacheSettings(Duration.ofSeconds(90), Duration.ZERO, CacheScope.PUBLIC);
      var configured =
          new McpPromptsService(
              List.of(handler("alpha-prompt", "Alpha desc")),
              engine(),
              McpPromptsService.DEFAULT_PAGE_SIZE,
              settings);

      var result = configured.listPrompts(null);

      assertThat(result.ttlMs()).isEqualTo(90_000L);
      assertThat(result.cacheScope()).isEqualTo(CacheScope.PUBLIC);
    }
  }

  @Nested
  class Deterministic_ordering {

    @Test
    void list_prompts_order_is_sorted_by_name_regardless_of_registration_order() {
      var shuffledRegistration =
          new McpPromptsService(
              List.of(
                  handler("delta-prompt", "d"),
                  handler("alpha-prompt", "a"),
                  handler("charlie-prompt", "c"),
                  handler("bravo-prompt", "b")),
              engine());

      assertThat(
              shuffledRegistration.listPrompts(null).prompts().stream().map(Prompt::name).toList())
          .containsExactly("alpha-prompt", "bravo-prompt", "charlie-prompt", "delta-prompt");
    }
  }
}
