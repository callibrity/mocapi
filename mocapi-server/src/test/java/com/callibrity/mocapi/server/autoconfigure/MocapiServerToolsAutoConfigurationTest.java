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
import com.callibrity.mocapi.server.tools.McpToolContextResolver;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
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
      return new DefaultMethodInvokerFactory(java.util.List.of());
    }
  }

  @Test
  void defaultBeansAreAutoConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(McpToolContextResolver.class);
          assertThat(context).hasSingleBean(McpToolParamsResolver.class);
          assertThat(context).hasSingleBean(MethodSchemaGenerator.class);
          assertThat(context).hasSingleBean(ToolServiceMcpToolProvider.class);
        });
  }

  @Test
  void mcpToolsServiceIsAutoConfigured() {
    contextRunner.run(context -> assertThat(context).hasSingleBean(McpToolsService.class));
  }

  @Test
  void methodSchemaGeneratorIsDefaultImplementation() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(MethodSchemaGenerator.class))
                .isInstanceOf(DefaultMethodSchemaGenerator.class));
  }

  @Test
  void defaultSchemaVersionPropertyIsBound() {
    contextRunner.run(
        context -> {
          MocapiServerToolsProperties props = context.getBean(MocapiServerToolsProperties.class);
          assertThat(props.getSchemaVersion())
              .isEqualTo(com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12);
        });
  }

  @Test
  void schemaVersionCanBeOverridden() {
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
  void customToolContextResolverOverridesDefault() {
    McpToolContextResolver custom = new McpToolContextResolver();
    contextRunner
        .withBean(McpToolContextResolver.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpToolContextResolver.class);
              assertThat(context.getBean(McpToolContextResolver.class)).isSameAs(custom);
            });
  }

  @Test
  void customMethodSchemaGeneratorOverridesDefault() {
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
