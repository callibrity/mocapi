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

import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
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
  void customizers_register_when_observation_registry_bean_is_present() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(CallToolHandlerCustomizer.class);
              assertThat(context).hasSingleBean(GetPromptHandlerCustomizer.class);
              assertThat(context).hasSingleBean(ReadResourceHandlerCustomizer.class);
              assertThat(context).hasSingleBean(ReadResourceTemplateHandlerCustomizer.class);
            });
  }

  @Test
  void customizers_absent_when_no_observation_registry_bean() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(CallToolHandlerCustomizer.class);
          assertThat(context).doesNotHaveBean(GetPromptHandlerCustomizer.class);
          assertThat(context).doesNotHaveBean(ReadResourceHandlerCustomizer.class);
          assertThat(context).doesNotHaveBean(ReadResourceTemplateHandlerCustomizer.class);
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
