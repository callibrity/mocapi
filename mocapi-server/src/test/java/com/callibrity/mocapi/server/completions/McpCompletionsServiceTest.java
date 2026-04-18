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
package com.callibrity.mocapi.server.completions;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.CompleteRequestParams;
import com.callibrity.mocapi.model.CompletionArgument;
import com.callibrity.mocapi.model.PromptReference;
import com.callibrity.mocapi.model.ResourceTemplateReference;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpCompletionsServiceTest {

  private McpCompletionsService service;

  @BeforeEach
  void setUp() {
    service = new McpCompletionsService();
  }

  @Nested
  class Prompt_completions {

    @BeforeEach
    void registerDetailEnum() {
      service.registerPromptArgument(
          "summarize", "detail", List.of("BRIEF", "STANDARD", "DETAILED"));
    }

    @Test
    void returns_all_registered_values_when_prefix_is_empty() {
      var result = service.complete(completeRequest("summarize", "detail", ""));

      assertThat(result.completion().values()).containsExactly("BRIEF", "STANDARD", "DETAILED");
    }

    @Test
    void filters_by_prefix() {
      var result = service.complete(completeRequest("summarize", "detail", "B"));

      assertThat(result.completion().values()).containsExactly("BRIEF");
    }

    @Test
    void prefix_match_is_case_insensitive_and_preserves_canonical_values() {
      var result = service.complete(completeRequest("summarize", "detail", "br"));

      assertThat(result.completion().values()).containsExactly("BRIEF");
    }

    @Test
    void returns_empty_for_unknown_prompt_name() {
      var result = service.complete(completeRequest("unknown", "detail", ""));

      assertThat(result.completion().values()).isEmpty();
    }

    @Test
    void returns_empty_for_unknown_argument_name() {
      var result = service.complete(completeRequest("summarize", "missing", ""));

      assertThat(result.completion().values()).isEmpty();
    }

    @Test
    void returns_empty_when_no_candidate_matches_prefix() {
      var result = service.complete(completeRequest("summarize", "detail", "Z"));

      assertThat(result.completion().values()).isEmpty();
    }

    @Test
    void null_argument_value_treated_as_empty_prefix() {
      var result =
          service.complete(
              new CompleteRequestParams(
                  new PromptReference("ref/prompt", "summarize"),
                  new CompletionArgument("detail", null),
                  null,
                  null));

      assertThat(result.completion().values()).hasSize(3);
    }
  }

  @Nested
  class Resource_template_completions {

    @BeforeEach
    void registerStageEnum() {
      service.registerResourceTemplateVariable(
          "env://{stage}/config", "stage", List.of("DEV", "STAGE", "PROD"));
    }

    @Test
    void returns_registered_values_for_template_variable() {
      var result = service.complete(templateRequest("env://{stage}/config", "stage", ""));

      assertThat(result.completion().values()).containsExactly("DEV", "STAGE", "PROD");
    }

    @Test
    void filters_by_prefix() {
      var result = service.complete(templateRequest("env://{stage}/config", "stage", "P"));

      assertThat(result.completion().values()).containsExactly("PROD");
    }

    @Test
    void returns_empty_for_unknown_template() {
      var result = service.complete(templateRequest("unknown://{x}", "x", ""));

      assertThat(result.completion().values()).isEmpty();
    }
  }

  @Nested
  class Cap {

    @Test
    void caps_results_at_MAX_VALUES() {
      List<String> many =
          IntStream.range(0, 200).mapToObj(i -> String.format("val%03d", i)).toList();
      service.registerPromptArgument("p", "a", many);

      var result = service.complete(completeRequest("p", "a", "val"));

      assertThat(result.completion().values()).hasSize(McpCompletionsService.MAX_VALUES);
      assertThat(result.completion().total()).isEqualTo(McpCompletionsService.MAX_VALUES);
    }
  }

  @Nested
  class Registry_semantics {

    @Test
    void last_registration_wins() {
      service.registerPromptArgument("p", "a", List.of("old-1", "old-2"));
      service.registerPromptArgument("p", "a", List.of("new-1"));

      var result = service.complete(completeRequest("p", "a", ""));

      assertThat(result.completion().values()).containsExactly("new-1");
    }

    @Test
    void registered_values_are_immutable() {
      var mutable = new java.util.ArrayList<>(List.of("A", "B"));
      service.registerPromptArgument("p", "a", mutable);
      mutable.add("C");

      var result = service.complete(completeRequest("p", "a", ""));

      assertThat(result.completion().values()).containsExactly("A", "B");
    }
  }

  private static CompleteRequestParams completeRequest(
      String promptName, String argName, String argValue) {
    return new CompleteRequestParams(
        new PromptReference("ref/prompt", promptName),
        new CompletionArgument(argName, argValue),
        null,
        null);
  }

  private static CompleteRequestParams templateRequest(
      String uriTemplate, String varName, String varValue) {
    return new CompleteRequestParams(
        new ResourceTemplateReference("ref/resource", uriTemplate),
        new CompletionArgument(varName, varValue),
        null,
        null);
  }
}
