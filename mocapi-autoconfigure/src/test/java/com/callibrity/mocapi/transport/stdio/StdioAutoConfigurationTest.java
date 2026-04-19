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
package com.callibrity.mocapi.transport.stdio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.server.McpServer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StdioAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(StdioAutoConfiguration.class))
          .withUserConfiguration(InfrastructureConfig.class);

  @Configuration(proxyBeanMethods = false)
  static class InfrastructureConfig {

    @Bean
    McpServer mcpServer() {
      return mock(McpServer.class);
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }

  @Test
  void beans_are_not_registered_when_stdio_enabled_is_false() {
    contextRunner
        .withPropertyValues("mocapi.stdio.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(StdioTransport.class);
              assertThat(context).doesNotHaveBean(StdioServer.class);
            });
  }

  @Test
  void beans_are_not_registered_when_stdio_enabled_is_missing() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(StdioTransport.class);
          assertThat(context).doesNotHaveBean(StdioServer.class);
        });
  }

  @Test
  void beans_are_registered_when_stdio_enabled_is_true() {
    contextRunner
        .withPropertyValues("mocapi.stdio.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(StdioTransport.class);
              assertThat(context).hasSingleBean(StdioServer.class);
              assertThat(context).hasSingleBean(AtomicReference.class);
              assertThat(context).hasSingleBean(ApplicationRunner.class);
            });
  }

  @Test
  void beans_are_not_registered_without_mcp_server() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(StdioAutoConfiguration.class))
        .withPropertyValues("mocapi.stdio.enabled=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(StdioTransport.class);
              assertThat(context).doesNotHaveBean(StdioServer.class);
            });
  }
}
