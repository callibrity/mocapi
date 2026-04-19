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
package com.callibrity.mocapi.o11y;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.support.LogCaptor;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubPromptConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubResourceConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubResourceTemplateConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubToolConfig;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiO11yAttachmentLogTest {

  private final MocapiO11yAutoConfiguration autoConfig = new MocapiO11yAutoConfiguration();
  private final ObservationRegistry registry = ObservationRegistry.create();

  @Test
  void tool_customizer_logs_attachment() {
    var config = new StubToolConfig(new Tool("get-weather", null, null, null, null));

    try (var captor = LogCaptor.forClass(MocapiO11yAutoConfiguration.class)) {
      autoConfig.mcpObservationToolCustomizer(registry).customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly(
              "Attached McpObservationInterceptor interceptor to tool \"get-weather\"");
    }
  }

  @Test
  void prompt_customizer_logs_attachment() {
    var config = new StubPromptConfig(new Prompt("summarize", null, null, null, null));

    try (var captor = LogCaptor.forClass(MocapiO11yAutoConfiguration.class)) {
      autoConfig.mcpObservationPromptCustomizer(registry).customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly(
              "Attached McpObservationInterceptor interceptor to prompt \"summarize\"");
    }
  }

  @Test
  void resource_customizer_logs_attachment() {
    var config = new StubResourceConfig(new Resource("mem://hello", "hello", null, null));

    try (var captor = LogCaptor.forClass(MocapiO11yAutoConfiguration.class)) {
      autoConfig.mcpObservationResourceCustomizer(registry).customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly(
              "Attached McpObservationInterceptor interceptor to resource \"mem://hello\"");
    }
  }

  @Test
  void resource_template_customizer_logs_attachment() {
    var config =
        new StubResourceTemplateConfig(new ResourceTemplate("mem://item/{id}", "item", null, null));

    try (var captor = LogCaptor.forClass(MocapiO11yAutoConfiguration.class)) {
      autoConfig.mcpObservationResourceTemplateCustomizer(registry).customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly(
              "Attached McpObservationInterceptor interceptor to resource_template \"mem://item/{id}\"");
    }
  }
}
