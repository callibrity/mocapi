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
package com.callibrity.mocapi.server.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.server.substrate.SubstrateTestSupport;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiServerToolsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MocapiServerToolsAutoConfiguration.class, MocapiServerAutoConfiguration.class))
          .withUserConfiguration(InfrastructureConfig.class);

  @Configuration(proxyBeanMethods = false)
  static class InfrastructureConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AtomFactory atomFactory() {
      return SubstrateTestSupport.atomFactory();
    }

    @Bean
    MailboxFactory mailboxFactory() {
      return SubstrateTestSupport.mailboxFactory();
    }

    @Bean
    JsonRpcDispatcher jsonRpcDispatcher() {
      return mock(JsonRpcDispatcher.class);
    }

    @Bean
    MethodInvokerFactory methodInvokerFactory() {
      return new DefaultMethodInvokerFactory();
    }
  }

  @Test
  void default_beans_are_auto_configured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(MethodSchemaGenerator.class);
          assertThat(context).hasSingleBean(McpToolsService.class);
        });
  }

  @Test
  void mcp_tools_service_is_auto_configured() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(McpToolsService.class));
  }

  @Test
  void method_schema_generator_is_default_implementation() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(MethodSchemaGenerator.class))
                .isInstanceOf(DefaultMethodSchemaGenerator.class));
  }

  @Test
  void default_schema_version_property_is_bound() {
    contextRunner.run(
        context -> {
          MocapiServerToolsProperties props = context.getBean(MocapiServerToolsProperties.class);
          assertThat(props.getSchemaVersion())
              .isEqualTo(com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12);
        });
  }

  @Test
  void schema_version_can_be_overridden() {
    contextRunner
        .withPropertyValues("mocapi.tools.schema-version=draft_7")
        .run(
            context -> {
              MocapiServerToolsProperties props =
                  context.getBean(MocapiServerToolsProperties.class);
              assertThat(props.getSchemaVersion())
                  .isEqualTo(com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_7);
            });
  }

  @Test
  void custom_method_schema_generator_overrides_default() {
    contextRunner
        .withUserConfiguration(CustomSchemaGeneratorConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(MethodSchemaGenerator.class);
              assertThat(context.getBean(MethodSchemaGenerator.class))
                  .isNotInstanceOf(DefaultMethodSchemaGenerator.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomSchemaGeneratorConfig {

    @Bean
    MethodSchemaGenerator customSchemaGenerator() {
      return mock(MethodSchemaGenerator.class);
    }
  }
}
