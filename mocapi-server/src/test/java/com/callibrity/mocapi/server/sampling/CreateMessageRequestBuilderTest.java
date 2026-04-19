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
package com.callibrity.mocapi.server.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.IncludeContext;
import com.callibrity.mocapi.model.ModelPreferences;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolChoice;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateMessageRequestBuilderTest {

  McpToolsService toolsService;

  @BeforeEach
  void setUp() {
    toolsService = mock(McpToolsService.class);
  }

  @Test
  void build_throws_when_no_messages_added() {
    var builder = new CreateMessageRequestBuilder(toolsService);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("At least one message is required");
  }

  @Test
  void default_maxTokens_is_1024_when_not_set() {
    var params = new CreateMessageRequestBuilder(toolsService).userMessage("hi").build();

    assertThat(params.maxTokens()).isEqualTo(1024);
  }

  @Test
  void maxTokens_override_is_honored() {
    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").maxTokens(42).build();

    assertThat(params.maxTokens()).isEqualTo(42);
  }

  @Test
  void userMessage_appends_USER_role_TextContent() {
    var params = new CreateMessageRequestBuilder(toolsService).userMessage("hello").build();

    assertThat(params.messages()).hasSize(1);
    assertThat(params.messages().getFirst().role()).isEqualTo(Role.USER);
    assertThat(((TextContent) params.messages().getFirst().content()).text()).isEqualTo("hello");
  }

  @Test
  void assistantMessage_appends_ASSISTANT_role_TextContent() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("q")
            .assistantMessage("a prior answer")
            .build();

    assertThat(params.messages()).hasSize(2);
    assertThat(params.messages().get(1).role()).isEqualTo(Role.ASSISTANT);
    assertThat(((TextContent) params.messages().get(1).content()).text())
        .isEqualTo("a prior answer");
  }

  @Test
  void message_accepts_arbitrary_ContentBlock() {
    var custom = new TextContent("custom", null);
    var params = new CreateMessageRequestBuilder(toolsService).message(Role.USER, custom).build();

    assertThat(params.messages()).hasSize(1);
    assertThat(params.messages().getFirst().content()).isSameAs(custom);
  }

  @Test
  void messages_are_copied_defensively_on_build() {
    var builder = new CreateMessageRequestBuilder(toolsService).userMessage("first");
    var params = builder.build();

    builder.userMessage("mutated after build");

    assertThat(params.messages()).hasSize(1);
  }

  @Test
  void scalar_overrides_round_trip_into_params() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .systemPrompt("you are helpful")
            .temperature(0.3)
            .includeContext(IncludeContext.THIS_SERVER)
            .stopSequences("STOP", "END")
            .maxTokens(200)
            .build();

    assertThat(params.systemPrompt()).isEqualTo("you are helpful");
    assertThat(params.temperature()).isEqualTo(0.3);
    assertThat(params.includeContext()).isEqualTo(IncludeContext.THIS_SERVER);
    assertThat(params.stopSequences()).containsExactly("STOP", "END");
    assertThat(params.maxTokens()).isEqualTo(200);
  }

  @Test
  void tool_accumulates_and_passes_through_to_params() {
    var t1 = new Tool("blast-radius", null, "radius", null, null);
    var t2 = new Tool("service-dependents", null, "deps", null, null);

    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").tool(t1).tool(t2).build();

    assertThat(params.tools()).containsExactly(t1, t2);
  }

  @Test
  void tools_is_null_when_none_added() {
    var params = new CreateMessageRequestBuilder(toolsService).userMessage("hi").build();

    assertThat(params.tools()).isNull();
  }

  @Test
  void toolChoice_passes_through_to_params() {
    var choice = ToolChoice.specific("blast-radius");

    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").toolChoice(choice).build();

    assertThat(params.toolChoice()).isSameAs(choice);
  }

  @Test
  void modelPreferences_are_null_when_no_hints_or_priorities_set() {
    var params = new CreateMessageRequestBuilder(toolsService).userMessage("hi").build();

    assertThat(params.modelPreferences()).isNull();
  }

  @Test
  void preferModel_aggregates_into_ModelPreferences_hints() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .preferModel("claude-3-sonnet")
            .preferModel("claude-3-opus")
            .build();

    assertThat(params.modelPreferences().hints())
        .extracting("name")
        .containsExactly("claude-3-sonnet", "claude-3-opus");
  }

  @Test
  void cost_priority_alone_builds_ModelPreferences() {
    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").costPriority(0.1).build();

    assertThat(params.modelPreferences()).isNotNull();
    assertThat(params.modelPreferences().costPriority()).isEqualTo(0.1);
    assertThat(params.modelPreferences().speedPriority()).isNull();
    assertThat(params.modelPreferences().intelligencePriority()).isNull();
  }

  @Test
  void speed_priority_alone_builds_ModelPreferences() {
    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").speedPriority(0.3).build();

    assertThat(params.modelPreferences()).isNotNull();
    assertThat(params.modelPreferences().costPriority()).isNull();
    assertThat(params.modelPreferences().speedPriority()).isEqualTo(0.3);
    assertThat(params.modelPreferences().intelligencePriority()).isNull();
  }

  @Test
  void intelligence_priority_alone_builds_ModelPreferences() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .intelligencePriority(0.7)
            .build();

    assertThat(params.modelPreferences()).isNotNull();
    assertThat(params.modelPreferences().costPriority()).isNull();
    assertThat(params.modelPreferences().speedPriority()).isNull();
    assertThat(params.modelPreferences().intelligencePriority()).isEqualTo(0.7);
  }

  @Test
  void priorities_aggregate_into_ModelPreferences() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .costPriority(0.2)
            .speedPriority(0.5)
            .intelligencePriority(0.9)
            .build();

    assertThat(params.modelPreferences().costPriority()).isEqualTo(0.2);
    assertThat(params.modelPreferences().speedPriority()).isEqualTo(0.5);
    assertThat(params.modelPreferences().intelligencePriority()).isEqualTo(0.9);
  }

  @Test
  void explicit_modelPreferences_overrides_hints_and_priorities() {
    var explicit = new ModelPreferences(List.of(), 1.0, 1.0, 1.0);

    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .preferModel("ignored")
            .costPriority(0.0)
            .modelPreferences(explicit)
            .build();

    assertThat(params.modelPreferences()).isSameAs(explicit);
  }

  @Test
  void tool_by_name_resolves_via_tools_service_and_appends() {
    var registered = new Tool("blast-radius", null, "radius", null, null);
    when(toolsService.findToolDescriptor("blast-radius")).thenReturn(registered);

    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .tool("blast-radius")
            .build();

    assertThat(params.tools()).containsExactly(registered);
  }

  @Test
  void tool_by_name_throws_when_service_returns_null() {
    when(toolsService.findToolDescriptor("nope")).thenReturn(null);

    var builder = new CreateMessageRequestBuilder(toolsService);

    assertThatThrownBy(() -> builder.userMessage("hi").tool("nope"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No tool registered with name: nope");
  }

  @Test
  void userMessages_varargs_appends_one_user_message_per_string() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessages("first", "second", "third")
            .build();

    assertThat(params.messages()).hasSize(3);
    assertThat(params.messages()).allMatch(m -> m.role() == Role.USER);
    assertThat(params.messages().stream().map(m -> ((TextContent) m.content()).text()))
        .containsExactly("first", "second", "third");
  }

  @Test
  void userMessages_empty_varargs_is_noop() {
    var builder = new CreateMessageRequestBuilder(toolsService).userMessages();

    assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void assistantMessages_varargs_appends_one_assistant_message_per_string() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("q")
            .assistantMessages("a1", "a2")
            .build();

    assertThat(params.messages()).hasSize(3);
    assertThat(params.messages().get(1).role()).isEqualTo(Role.ASSISTANT);
    assertThat(((TextContent) params.messages().get(1).content()).text()).isEqualTo("a1");
    assertThat(params.messages().get(2).role()).isEqualTo(Role.ASSISTANT);
    assertThat(((TextContent) params.messages().get(2).content()).text()).isEqualTo("a2");
  }

  @Test
  void preferModels_varargs_appends_one_hint_per_string() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .preferModels("claude-3-sonnet", "claude-3-opus", "claude-3-haiku")
            .build();

    assertThat(params.modelPreferences().hints())
        .extracting("name")
        .containsExactly("claude-3-sonnet", "claude-3-opus", "claude-3-haiku");
  }

  @Test
  void tools_varargs_resolves_each_name_via_tools_service() {
    var t1 = new Tool("blast-radius", null, "A", null, null);
    var t2 = new Tool("service-dependents", null, "B", null, null);
    when(toolsService.findToolDescriptor("blast-radius")).thenReturn(t1);
    when(toolsService.findToolDescriptor("service-dependents")).thenReturn(t2);

    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .tools("blast-radius", "service-dependents")
            .build();

    assertThat(params.tools()).containsExactly(t1, t2);
  }

  @Test
  void autoToolChoice_shortcut_sets_ToolChoice_Auto() {
    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").autoToolChoice().build();

    assertThat(params.toolChoice()).isEqualTo(ToolChoice.auto());
  }

  @Test
  void noneToolChoice_shortcut_sets_ToolChoice_None() {
    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").noneToolChoice().build();

    assertThat(params.toolChoice()).isEqualTo(ToolChoice.none());
  }

  @Test
  void mustUseTool_shortcut_sets_ToolChoice_Specific() {
    var params =
        new CreateMessageRequestBuilder(toolsService)
            .userMessage("hi")
            .mustUseTool("blast-radius")
            .build();

    assertThat(params.toolChoice()).isEqualTo(ToolChoice.specific("blast-radius"));
  }

  @Test
  void allServerTools_appends_every_tool_from_service() {
    var t1 = new Tool("a", null, "A", null, null);
    var t2 = new Tool("b", null, "B", null, null);
    when(toolsService.allToolDescriptors()).thenReturn(List.of(t1, t2));

    var params =
        new CreateMessageRequestBuilder(toolsService).userMessage("hi").allServerTools().build();

    assertThat(params.tools()).containsExactly(t1, t2);
  }
}
