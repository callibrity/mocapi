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
package com.callibrity.mocapi.transport.http.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerProperties;
import com.callibrity.mocapi.transport.http.McpRequestValidator;
import com.callibrity.mocapi.transport.http.StreamableHttpController;
import com.callibrity.mocapi.transport.http.sse.SseStreamFactory;
import java.util.Base64;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.Odyssey;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StreamableHttpAutoConfigurationTest {

  private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(StreamableHttpAutoConfiguration.class))
          .withUserConfiguration(InfrastructureConfig.class)
          .withPropertyValues(
              "mocapi.session-encryption-master-key=" + MASTER_KEY,
              "mocapi.session-timeout=PT1H",
              "mocapi.elicitation.timeout=PT5M",
              "mocapi.sampling.timeout=PT30S",
              "mocapi.pagination.page-size=50",
              "mocapi.allowed-origins=localhost");

  @Configuration(proxyBeanMethods = false)
  static class InfrastructureConfig {

    @Bean
    McpServer mcpServer() {
      return mock(McpServer.class);
    }

    @Bean
    Odyssey odyssey() {
      return mock(Odyssey.class);
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
          assertThat(context).hasSingleBean(StreamableHttpController.class);
        });
  }

  @Test
  void custom_request_validator_overrides_default() {
    McpRequestValidator custom = new McpRequestValidator(java.util.List.of("example.com"));
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
    StreamableHttpController customController(
        McpServer protocol, SseStreamFactory sseStreamFactory, ObjectMapper objectMapper) {
      return new StreamableHttpController(
          protocol,
          new McpRequestValidator(java.util.List.of("localhost")),
          sseStreamFactory,
          objectMapper);
    }
  }

  @Test
  void beans_are_not_created_when_mcp_server_bean_is_missing() {
    new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(StreamableHttpAutoConfiguration.class))
        .withPropertyValues(
            "mocapi.session-encryption-master-key=" + MASTER_KEY,
            "mocapi.session-timeout=PT1H",
            "mocapi.elicitation.timeout=PT5M",
            "mocapi.sampling.timeout=PT30S",
            "mocapi.pagination.page-size=50",
            "mocapi.allowed-origins=localhost")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(McpRequestValidator.class);
              assertThat(context).doesNotHaveBean(StreamableHttpController.class);
            });
  }

  @Test
  void missing_master_key_fails_with_helpful_message() {
    contextRunner
        .withPropertyValues("mocapi.session-encryption-master-key=")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasStackTraceContaining("mocapi.session-encryption-master-key is required")
                  .hasStackTraceContaining("openssl rand -base64 32");
            });
  }

  @Test
  void invalid_base64_master_key_fails_with_helpful_message() {
    contextRunner
        .withPropertyValues("mocapi.session-encryption-master-key=not!!!valid~base64")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasStackTraceContaining("not valid base64")
                  .hasStackTraceContaining("openssl rand -base64 32");
            });
  }

  @Test
  void wrong_length_master_key_fails_with_helpful_message() {
    // 16 bytes encoded — base64-valid but AES-256 needs 32.
    String shortKey = java.util.Base64.getEncoder().encodeToString(new byte[16]);
    contextRunner
        .withPropertyValues("mocapi.session-encryption-master-key=" + shortKey)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context)
                  .getFailure()
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining("16 bytes")
                  .hasMessageContaining("AES-256 requires exactly 32");
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
