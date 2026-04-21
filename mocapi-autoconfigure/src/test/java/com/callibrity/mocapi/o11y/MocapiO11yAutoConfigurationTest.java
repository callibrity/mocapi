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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerConfig;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerConfig;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiO11yAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MocapiO11yAutoConfiguration.class));

  @Test
  void registers_filter_and_four_handler_customizers_when_observation_registry_is_present() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpObservationFilter.class);
              assertThat(context).hasSingleBean(CallToolHandlerCustomizer.class);
              assertThat(context).hasSingleBean(GetPromptHandlerCustomizer.class);
              assertThat(context).hasSingleBean(ReadResourceHandlerCustomizer.class);
              assertThat(context).hasSingleBean(ReadResourceTemplateHandlerCustomizer.class);
            });
  }

  @Test
  void tool_customizer_attaches_handler_observation_interceptor() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              CallToolHandlerCustomizer customizer =
                  context.getBean(CallToolHandlerCustomizer.class);
              CallToolHandlerConfig config = mock(CallToolHandlerConfig.class);
              Tool tool = mock(Tool.class);
              when(tool.name()).thenReturn("my-tool");
              when(config.descriptor()).thenReturn(tool);

              customizer.customize(config);

              verify(config).interceptor(any(MethodInterceptor.class));
            });
  }

  @Test
  void prompt_customizer_attaches_handler_observation_interceptor() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              GetPromptHandlerCustomizer customizer =
                  context.getBean(GetPromptHandlerCustomizer.class);
              GetPromptHandlerConfig config = mock(GetPromptHandlerConfig.class);
              Prompt prompt = mock(Prompt.class);
              when(prompt.name()).thenReturn("my-prompt");
              when(config.descriptor()).thenReturn(prompt);

              customizer.customize(config);

              verify(config).interceptor(any(MethodInterceptor.class));
            });
  }

  @Test
  void resource_customizer_attaches_handler_observation_interceptor() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              ReadResourceHandlerCustomizer customizer =
                  context.getBean(ReadResourceHandlerCustomizer.class);
              ReadResourceHandlerConfig config = mock(ReadResourceHandlerConfig.class);
              Resource resource = new Resource("mem://hello", "hello", null, null);
              when(config.descriptor()).thenReturn(resource);

              customizer.customize(config);

              verify(config).interceptor(any(MethodInterceptor.class));
            });
  }

  @Test
  void resource_template_customizer_attaches_handler_observation_interceptor() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              ReadResourceTemplateHandlerCustomizer customizer =
                  context.getBean(ReadResourceTemplateHandlerCustomizer.class);
              ReadResourceTemplateHandlerConfig config =
                  mock(ReadResourceTemplateHandlerConfig.class);
              ResourceTemplate tmpl = new ResourceTemplate("mem://item/{id}", "item", null, null);
              when(config.descriptor()).thenReturn(tmpl);

              customizer.customize(config);

              verify(config).interceptor(any(MethodInterceptor.class));
            });
  }

  @Test
  void inactive_when_no_observation_registry_bean_present() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(McpObservationFilter.class);
          assertThat(context).doesNotHaveBean(CallToolHandlerCustomizer.class);
        });
  }

  @Configuration
  static class ObservationRegistryConfig {
    @Bean
    ObservationRegistry observationRegistry() {
      return ObservationRegistry.create();
    }
  }
}
