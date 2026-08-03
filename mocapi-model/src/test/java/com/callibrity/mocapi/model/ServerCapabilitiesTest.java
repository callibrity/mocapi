/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ServerCapabilitiesTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void build_with_no_customization_reproduces_the_historical_hardcoded_defaults() {
    ServerCapabilities caps = ServerCapabilities.builder().build();

    assertThat(caps.tools()).isEqualTo(new ToolsCapability(false));
    assertThat(caps.completions()).isEqualTo(new CompletionsCapability());
    assertThat(caps.resources()).isEqualTo(new ResourcesCapability(false, false));
    assertThat(caps.prompts()).isEqualTo(new PromptsCapability(false));
    assertThat(caps.logging()).isNull();
    assertThat(caps.experimental()).isNull();
    assertThat(caps.extensions()).isEqualTo(Map.of());
  }

  @Test
  void build_with_zero_extensions_returns_the_empty_map_singleton() {
    ServerCapabilities caps = ServerCapabilities.builder().build();

    assertThat(caps.extensions()).isEqualTo(Map.of());
    assertThatThrownBy(() -> caps.extensions().put("x", mapper.createObjectNode()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void extension_accumulates_every_distinct_id_added_to_the_builder() {
    // Builder#extensions is a LinkedHashMap internally, but build() hands the contents to
    // Map.copyOf(), whose immutable-map implementation does not preserve insertion order (it
    // iterates in a hash/salt-based order) — so only membership, not order, is guaranteed here.
    ObjectNode first = mapper.createObjectNode().put("k", "first");
    ObjectNode second = mapper.createObjectNode().put("k", "second");
    ObjectNode third = mapper.createObjectNode().put("k", "third");

    ServerCapabilities caps =
        ServerCapabilities.builder()
            .extension("io.modelcontextprotocol/c", third)
            .extension("io.modelcontextprotocol/a", first)
            .extension("io.modelcontextprotocol/b", second)
            .build();

    assertThat(caps.extensions().keySet())
        .containsExactlyInAnyOrder(
            "io.modelcontextprotocol/c", "io.modelcontextprotocol/a", "io.modelcontextprotocol/b");
  }

  @Test
  void extension_called_twice_with_the_same_id_overwrites_the_earlier_value() {
    ObjectNode original = mapper.createObjectNode().put("k", "original");
    ObjectNode replacement = mapper.createObjectNode().put("k", "replacement");

    ServerCapabilities caps =
        ServerCapabilities.builder()
            .extension("io.modelcontextprotocol/tasks", original)
            .extension("io.modelcontextprotocol/tasks", replacement)
            .build();

    assertThat(caps.extensions()).hasSize(1);
    assertThat(caps.extensions().get("io.modelcontextprotocol/tasks").path("k").asString())
        .isEqualTo("replacement");
  }

  @Test
  void build_returns_an_immutable_snapshot_that_later_builder_calls_cannot_reach() {
    ServerCapabilities.Builder builder =
        ServerCapabilities.builder()
            .extension("io.modelcontextprotocol/tasks", mapper.createObjectNode().put("k", "v"));
    ServerCapabilities caps = builder.build();

    assertThatThrownBy(() -> caps.extensions().put("new", mapper.createObjectNode()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> caps.extensions().remove("io.modelcontextprotocol/tasks"))
        .isInstanceOf(UnsupportedOperationException.class);

    builder.extension("io.modelcontextprotocol/other", mapper.createObjectNode());

    assertThat(caps.extensions()).containsOnlyKeys("io.modelcontextprotocol/tasks");
  }

  @Test
  void tools_setter_flows_through_to_the_built_instance() {
    ToolsCapability tools = new ToolsCapability(true);
    ServerCapabilities caps = ServerCapabilities.builder().tools(tools).build();
    assertThat(caps.tools()).isEqualTo(tools);
  }

  @Test
  void completions_setter_flows_through_to_the_built_instance() {
    CompletionsCapability completions = new CompletionsCapability();
    ServerCapabilities caps = ServerCapabilities.builder().completions(completions).build();
    assertThat(caps.completions()).isEqualTo(completions);
  }

  @Test
  void resources_setter_flows_through_to_the_built_instance() {
    ResourcesCapability resources = new ResourcesCapability(true, true);
    ServerCapabilities caps = ServerCapabilities.builder().resources(resources).build();
    assertThat(caps.resources()).isEqualTo(resources);
  }

  @Test
  void prompts_setter_flows_through_to_the_built_instance() {
    PromptsCapability prompts = new PromptsCapability(true);
    ServerCapabilities caps = ServerCapabilities.builder().prompts(prompts).build();
    assertThat(caps.prompts()).isEqualTo(prompts);
  }

  // SEP-2577 spec contract: LoggingCapability is deprecated but still part of the MCP schema
  // during the 12-month deprecation window, so exercising ServerCapabilities.Builder#logging
  // (also annotated in main code) legitimately touches the deprecated type.
  @SuppressWarnings("deprecation")
  @Test
  void logging_setter_flows_through_to_the_built_instance() {
    LoggingCapability logging = new LoggingCapability();
    ServerCapabilities caps = ServerCapabilities.builder().logging(logging).build();
    assertThat(caps.logging()).isEqualTo(logging);
  }

  @Test
  void experimental_setter_flows_through_to_the_built_instance() {
    Map<String, ObjectNode> experimental = new LinkedHashMap<>();
    experimental.put("io.modelcontextprotocol/experimental-thing", mapper.createObjectNode());
    ServerCapabilities caps = ServerCapabilities.builder().experimental(experimental).build();
    assertThat(caps.experimental()).isEqualTo(experimental);
  }
}
