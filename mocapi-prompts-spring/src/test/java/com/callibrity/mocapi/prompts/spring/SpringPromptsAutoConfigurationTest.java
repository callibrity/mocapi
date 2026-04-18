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
package com.callibrity.mocapi.prompts.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.prompts.template.PromptTemplate;
import com.callibrity.mocapi.api.prompts.template.PromptTemplateFactory;
import com.callibrity.mocapi.model.Role;
import com.callibrity.mocapi.model.TextContent;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SpringPromptsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SpringPromptsAutoConfiguration.class));

  @Test
  void registers_spring_factory_by_default() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PromptTemplateFactory.class);
          assertThat(context.getBean(PromptTemplateFactory.class))
              .isInstanceOf(SpringPromptTemplateFactory.class);
        });
  }

  @Test
  void factory_produces_working_templates() {
    contextRunner.run(
        context -> {
          var factory = context.getBean(PromptTemplateFactory.class);
          PromptTemplate template = factory.create(Role.USER, "Hi ${name}");
          var result = template.render(Map.of("name", "Mocapi"));
          assertThat(((TextContent) result.messages().getFirst().content()).text())
              .isEqualTo("Hi Mocapi");
        });
  }

  @Test
  void user_supplied_bean_wins() {
    contextRunner
        .withUserConfiguration(CustomFactoryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PromptTemplateFactory.class);
              assertThat(context.getBean(PromptTemplateFactory.class))
                  .isSameAs(CustomFactoryConfig.CUSTOM);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomFactoryConfig {
    static final PromptTemplateFactory CUSTOM = new SpringPromptTemplateFactory();

    @Bean
    PromptTemplateFactory customPromptTemplateFactory() {
      return CUSTOM;
    }
  }
}
