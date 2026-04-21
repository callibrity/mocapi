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
package com.callibrity.mocapi.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.PromptArgument;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.handler.HandlerDescriptor;
import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.prompts.GetPromptHandler;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandler;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpActuatorEndpointTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  @Test
  void snapshot_reports_server_info() {
    McpActuatorSnapshot snapshot = fullFixtureSnapshot();
    assertThat(snapshot.server().name()).isEqualTo("mocapi");
    assertThat(snapshot.server().version()).isEqualTo("1.2.3");
    assertThat(snapshot.server().protocolVersion()).isEqualTo("2025-11-25");
  }

  @Test
  void snapshot_reports_counts_per_handler_kind() {
    McpActuatorSnapshot snapshot = fullFixtureSnapshot();
    assertThat(snapshot.counts().tools()).isEqualTo(1);
    assertThat(snapshot.counts().prompts()).isEqualTo(1);
    assertThat(snapshot.counts().resources()).isEqualTo(1);
    assertThat(snapshot.counts().resourceTemplates()).isEqualTo(1);
  }

  @Test
  void snapshot_tool_entry_includes_descriptor_digests_and_handler_descriptor() {
    McpActuatorSnapshot snapshot = fullFixtureSnapshot();
    assertThat(snapshot.tools()).hasSize(1);
    var toolInfo = snapshot.tools().getFirst();
    assertThat(toolInfo.name()).isEqualTo("get_weather");
    assertThat(toolInfo.title()).isEqualTo("Get Weather");
    assertThat(toolInfo.description()).isEqualTo("Weather tool");
    assertThat(toolInfo.inputSchemaDigest()).startsWith("sha256:").hasSize("sha256:".length() + 64);
    assertThat(toolInfo.outputSchemaDigest())
        .startsWith("sha256:")
        .hasSize("sha256:".length() + 64);
    assertThat(toolInfo.handler().kind()).isEqualTo(HandlerKind.TOOL);
    assertThat(toolInfo.handler().declaringClassName()).isEqualTo("com.example.WeatherTool");
    assertThat(toolInfo.handler().methodName()).isEqualTo("getWeather");
    assertThat(toolInfo.handler().interceptors())
        .containsExactly("Validates tool arguments against the tool's input JSON schema");
  }

  @Test
  void snapshot_prompt_entry_includes_arguments_and_handler_descriptor() {
    McpActuatorSnapshot snapshot = fullFixtureSnapshot();
    assertThat(snapshot.prompts()).hasSize(1);
    var promptInfo = snapshot.prompts().getFirst();
    assertThat(promptInfo.name()).isEqualTo("summarize");
    assertThat(promptInfo.arguments()).hasSize(1);
    assertThat(promptInfo.arguments().getFirst().name()).isEqualTo("text");
    assertThat(promptInfo.arguments().getFirst().required()).isTrue();
    assertThat(promptInfo.handler().kind()).isEqualTo(HandlerKind.PROMPT);
    assertThat(promptInfo.handler().methodName()).isEqualTo("summarize");
  }

  @Test
  void snapshot_resource_and_resource_template_entries_include_handler_descriptors() {
    McpActuatorSnapshot snapshot = fullFixtureSnapshot();
    assertThat(snapshot.resources()).hasSize(1);
    var resourceInfo = snapshot.resources().getFirst();
    assertThat(resourceInfo.uri()).isEqualTo("docs://readme");
    assertThat(resourceInfo.handler().kind()).isEqualTo(HandlerKind.RESOURCE);

    assertThat(snapshot.resourceTemplates()).hasSize(1);
    var templateInfo = snapshot.resourceTemplates().getFirst();
    assertThat(templateInfo.uriTemplate()).isEqualTo("docs://pages/{slug}");
    assertThat(templateInfo.handler().kind()).isEqualTo(HandlerKind.RESOURCE_TEMPLATE);
  }

  private static McpActuatorSnapshot fullFixtureSnapshot() {
    ObjectNode inputSchema = MAPPER.createObjectNode().put("type", "object");
    ObjectNode outputSchema = MAPPER.createObjectNode().put("type", "string");
    Tool tool = new Tool("get_weather", "Get Weather", "Weather tool", inputSchema, outputSchema);
    Prompt prompt =
        new Prompt(
            "summarize",
            "Summarize",
            "Summarize text",
            null,
            List.of(new PromptArgument("text", "Input text", true)));
    Resource resource = new Resource("docs://readme", "README", "Project README", "text/markdown");
    ResourceTemplate template =
        new ResourceTemplate("docs://pages/{slug}", "Page", "Doc page", "text/markdown");

    CallToolHandler toolHandler =
        toolHandler(
            tool,
            new HandlerDescriptor(
                HandlerKind.TOOL,
                "com.example.WeatherTool",
                "getWeather",
                List.of("Validates tool arguments against the tool's input JSON schema")));
    GetPromptHandler promptHandler =
        promptHandler(
            prompt,
            new HandlerDescriptor(
                HandlerKind.PROMPT, "com.example.SummarizePrompt", "summarize", List.of()));
    ReadResourceHandler resourceHandler =
        resourceHandler(
            resource,
            new HandlerDescriptor(HandlerKind.RESOURCE, "com.example.Docs", "readme", List.of()));
    ReadResourceTemplateHandler templateHandler =
        templateHandler(
            template,
            new HandlerDescriptor(
                HandlerKind.RESOURCE_TEMPLATE, "com.example.Docs", "page", List.of()));

    McpToolsService tools = mock(McpToolsService.class);
    when(tools.allItems()).thenReturn(List.of(toolHandler));
    McpPromptsService prompts = mock(McpPromptsService.class);
    when(prompts.allItems()).thenReturn(List.of(promptHandler));
    McpResourcesService resources = mock(McpResourcesService.class);
    when(resources.allResourceHandlers()).thenReturn(List.of(resourceHandler));
    when(resources.allResourceTemplateHandlers()).thenReturn(List.of(templateHandler));

    var endpoint =
        new McpActuatorEndpoint(
            new Implementation("mocapi", "Mocapi", "1.2.3"), tools, prompts, resources);
    return endpoint.snapshot();
  }

  @Test
  void snapshot_omits_digest_when_schema_is_null() {
    Tool tool = new Tool("no_schema", null, null, null, null);
    CallToolHandler toolHandler =
        toolHandler(tool, new HandlerDescriptor(HandlerKind.TOOL, "com.example.X", "m", List.of()));

    McpToolsService tools = mock(McpToolsService.class);
    when(tools.allItems()).thenReturn(List.of(toolHandler));
    McpPromptsService prompts = mock(McpPromptsService.class);
    when(prompts.allItems()).thenReturn(List.of());
    McpResourcesService resources = mock(McpResourcesService.class);
    when(resources.allResourceHandlers()).thenReturn(List.of());
    when(resources.allResourceTemplateHandlers()).thenReturn(List.of());

    var endpoint =
        new McpActuatorEndpoint(
            new Implementation("mocapi", null, null), tools, prompts, resources);

    McpActuatorSnapshot snapshot = endpoint.snapshot();
    var toolInfo = snapshot.tools().getFirst();
    assertThat(toolInfo.inputSchemaDigest()).isNull();
    assertThat(toolInfo.outputSchemaDigest()).isNull();
    assertThat(snapshot.server().version()).isNull();
  }

  @Test
  void digest_is_deterministic_for_equivalent_schemas() {
    ObjectNode a = MAPPER.createObjectNode().put("type", "object");
    ObjectNode b = MAPPER.createObjectNode().put("type", "object");
    assertThat(McpActuatorSnapshots.schemaDigest(a))
        .isEqualTo(McpActuatorSnapshots.schemaDigest(b));
  }

  private static CallToolHandler toolHandler(Tool tool, HandlerDescriptor descriptor) {
    CallToolHandler handler = mock(CallToolHandler.class);
    when(handler.descriptor()).thenReturn(tool);
    when(handler.describe()).thenReturn(descriptor);
    return handler;
  }

  private static GetPromptHandler promptHandler(Prompt prompt, HandlerDescriptor descriptor) {
    GetPromptHandler handler = mock(GetPromptHandler.class);
    when(handler.descriptor()).thenReturn(prompt);
    when(handler.describe()).thenReturn(descriptor);
    return handler;
  }

  private static ReadResourceHandler resourceHandler(
      Resource resource, HandlerDescriptor descriptor) {
    ReadResourceHandler handler = mock(ReadResourceHandler.class);
    when(handler.descriptor()).thenReturn(resource);
    when(handler.describe()).thenReturn(descriptor);
    return handler;
  }

  private static ReadResourceTemplateHandler templateHandler(
      ResourceTemplate template, HandlerDescriptor descriptor) {
    ReadResourceTemplateHandler handler = mock(ReadResourceTemplateHandler.class);
    when(handler.descriptor()).thenReturn(template);
    when(handler.describe()).thenReturn(descriptor);
    return handler;
  }
}
