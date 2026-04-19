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
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
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
  void snapshot_reports_server_info_counts_and_descriptors() {
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

    McpToolsService tools = mock(McpToolsService.class);
    when(tools.allDescriptors()).thenReturn(List.of(tool));
    McpPromptsService prompts = mock(McpPromptsService.class);
    when(prompts.allDescriptors()).thenReturn(List.of(prompt));
    McpResourcesService resources = mock(McpResourcesService.class);
    when(resources.allResourceDescriptors()).thenReturn(List.of(resource));
    when(resources.allResourceTemplateDescriptors()).thenReturn(List.of(template));

    var endpoint =
        new McpActuatorEndpoint(
            new Implementation("mocapi", "Mocapi", "1.2.3"), tools, prompts, resources);

    McpActuatorSnapshot snapshot = endpoint.snapshot();

    assertThat(snapshot.server().name()).isEqualTo("mocapi");
    assertThat(snapshot.server().version()).isEqualTo("1.2.3");
    assertThat(snapshot.server().protocolVersion()).isEqualTo("2025-11-25");

    assertThat(snapshot.counts().tools()).isEqualTo(1);
    assertThat(snapshot.counts().prompts()).isEqualTo(1);
    assertThat(snapshot.counts().resources()).isEqualTo(1);
    assertThat(snapshot.counts().resourceTemplates()).isEqualTo(1);

    assertThat(snapshot.tools()).hasSize(1);
    var toolInfo = snapshot.tools().getFirst();
    assertThat(toolInfo.name()).isEqualTo("get_weather");
    assertThat(toolInfo.title()).isEqualTo("Get Weather");
    assertThat(toolInfo.description()).isEqualTo("Weather tool");
    assertThat(toolInfo.inputSchemaDigest()).startsWith("sha256:").hasSize("sha256:".length() + 64);
    assertThat(toolInfo.outputSchemaDigest())
        .startsWith("sha256:")
        .hasSize("sha256:".length() + 64);

    assertThat(snapshot.prompts()).hasSize(1);
    var promptInfo = snapshot.prompts().getFirst();
    assertThat(promptInfo.name()).isEqualTo("summarize");
    assertThat(promptInfo.arguments()).hasSize(1);
    assertThat(promptInfo.arguments().getFirst().name()).isEqualTo("text");
    assertThat(promptInfo.arguments().getFirst().required()).isTrue();

    assertThat(snapshot.resources()).hasSize(1);
    assertThat(snapshot.resources().getFirst().uri()).isEqualTo("docs://readme");

    assertThat(snapshot.resourceTemplates()).hasSize(1);
    assertThat(snapshot.resourceTemplates().getFirst().uriTemplate())
        .isEqualTo("docs://pages/{slug}");
  }

  @Test
  void snapshot_omits_digest_when_schema_is_null() {
    Tool tool = new Tool("no_schema", null, null, null, null);
    McpToolsService tools = mock(McpToolsService.class);
    when(tools.allDescriptors()).thenReturn(List.of(tool));
    McpPromptsService prompts = mock(McpPromptsService.class);
    when(prompts.allDescriptors()).thenReturn(List.of());
    McpResourcesService resources = mock(McpResourcesService.class);
    when(resources.allResourceDescriptors()).thenReturn(List.of());
    when(resources.allResourceTemplateDescriptors()).thenReturn(List.of());

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
}
