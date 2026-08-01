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
package com.callibrity.mocapi.examples;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the real context to prove the flat "app in the root package, handlers in sub-packages"
 * layout works with plain {@code @Component} beans and no example auto-configuration: the tool,
 * prompt, and resource handlers across the {@code tools}, {@code validation}, {@code prompts}, and
 * {@code resources} sub-packages are all discovered by component scan.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest
class HttpExampleContextTest {

  @Autowired private McpToolsService tools;
  @Autowired private McpPromptsService prompts;
  @Autowired private McpResourcesService resources;

  @Test
  void tools_from_the_tools_and_validation_packages_are_discovered() {
    assertThat(tools.listTools(null).tools())
        .extracting(Tool::name)
        .contains("hello", "countdown", "greet");
  }

  @Test
  void the_prompt_bean_with_an_injected_factory_is_discovered() {
    assertThat(prompts.listPrompts(null).prompts()).isNotEmpty();
  }

  @Test
  void fixed_and_templated_resources_are_discovered() {
    assertThat(resources.listResources(null).resources()).isNotEmpty();
    assertThat(resources.listResourceTemplates(null).resourceTemplates()).isNotEmpty();
  }
}
