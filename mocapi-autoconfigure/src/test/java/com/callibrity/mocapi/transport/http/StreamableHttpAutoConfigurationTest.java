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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerProperties;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StreamableHttpAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(StreamableHttpAutoConfiguration.class))
          .withUserConfiguration(InfrastructureConfig.class)
          .withPropertyValues("mocapi.pagination.page-size=50", "mocapi.allowed-origins=localhost");

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
  void default_beans_are_auto_configured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(McpRequestValidator.class);
          assertThat(context).hasSingleBean(McpHeaderValidator.class);
          assertThat(context).hasSingleBean(StreamableHttpController.class);
          assertThat(context).hasSingleBean(ContextSnapshotFactory.class);
        });
  }

  @Test
  void custom_context_snapshot_factory_overrides_default() {
    ContextSnapshotFactory custom = ContextSnapshotFactory.builder().build();
    contextRunner
        .withBean(ContextSnapshotFactory.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ContextSnapshotFactory.class);
              assertThat(context.getBean(ContextSnapshotFactory.class)).isSameAs(custom);
            });
  }

  @Test
  void custom_request_validator_overrides_default() {
    McpRequestValidator custom = new McpRequestValidator(List.of("example.com"));
    contextRunner
        .withBean(McpRequestValidator.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpRequestValidator.class);
              assertThat(context.getBean(McpRequestValidator.class)).isSameAs(custom);
            });
  }

  @Test
  void custom_streamable_http_controller_overrides_default() {
    contextRunner
        .withUserConfiguration(CustomControllerConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(StreamableHttpController.class);
              assertThat(context.getBean("customController")).isNotNull();
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class CustomControllerConfig {

    @Bean
    StreamableHttpController customController(McpServer protocol, ObjectMapper objectMapper) {
      return new StreamableHttpController(
          protocol,
          new McpRequestValidator(List.of("localhost")),
          new McpHeaderValidator(),
          objectMapper,
          ContextSnapshotFactory.builder().build());
    }
  }

  @Test
  void beans_are_not_created_when_mcp_server_bean_is_missing() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(StreamableHttpAutoConfiguration.class))
        .withPropertyValues("mocapi.pagination.page-size=50", "mocapi.allowed-origins=localhost")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpRequestValidator.class);
              assertThat(context).doesNotHaveBean(StreamableHttpController.class);
            });
  }

  @Test
  void allowed_origins_property_is_used_by_validator() {
    contextRunner
        .withPropertyValues("mocapi.allowed-origins=example.com,other.com")
        .run(
            context -> {
              MocapiServerProperties props = context.getBean(MocapiServerProperties.class);
              assertThat(props.allowedOrigins()).containsExactly("example.com", "other.com");
            });
  }
}
