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
package com.callibrity.mocapi.prompts.mustache;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.prompts.template.PromptTemplate;
import com.callibrity.mocapi.api.prompts.template.PromptTemplateFactory;
import com.callibrity.mocapi.model.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class MustachePromptsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MustachePromptsAutoConfiguration.class));

  @Test
  void registersMustacheFactoryByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PromptTemplateFactory.class);
          assertThat(context.getBean(PromptTemplateFactory.class))
              .isInstanceOf(MustachePromptTemplateFactory.class);
        });
  }

  @Test
  void factoryProducesWorkingTemplates() {
    contextRunner.run(
        context -> {
          var factory = context.getBean(PromptTemplateFactory.class);
          PromptTemplate template = factory.create(Role.USER, "Hi {{name}}");
          var result = template.render(Map.of("name", "Mocapi"));
          assertThat(
                  ((com.callibrity.mocapi.model.TextContent) result.messages().getFirst().content())
                      .text())
              .isEqualTo("Hi Mocapi");
        });
  }

  @Test
  void userSuppliedBeanWins() {
    contextRunner
        .withUserConfiguration(CustomFactoryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PromptTemplateFactory.class);
              assertThat(context.getBean(PromptTemplateFactory.class))
                  .isSameAs(CustomFactoryConfig.CUSTOM);
            });
  }

  @Test
  void backsOffWhenMustacheIsNotOnClasspath() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(com.samskivert.mustache.Mustache.class))
        .run(context -> assertThat(context).doesNotHaveBean(PromptTemplateFactory.class));
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomFactoryConfig {
    static final PromptTemplateFactory CUSTOM = new MustachePromptTemplateFactory();

    @Bean
    PromptTemplateFactory customPromptTemplateFactory() {
      return CUSTOM;
    }
  }
}
