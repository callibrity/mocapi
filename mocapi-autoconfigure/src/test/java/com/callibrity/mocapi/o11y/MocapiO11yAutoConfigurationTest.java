/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import com.callibrity.ripcurl.autoconfigure.RipCurlObservationAutoConfiguration;
import com.callibrity.ripcurl.core.annotation.JsonRpcMethodHandlerConfig;
import com.callibrity.ripcurl.core.spi.JsonRpcExceptionTranslatorRegistry;
import com.callibrity.ripcurl.o11y.JsonRpcObservationCustomizer;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiO11yAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(MocapiO11yAutoConfiguration.class));

  @Test
  void registers_operation_customizer_and_four_handler_customizers() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpServerOperationCustomizer.class);
              assertThat(context).hasSingleBean(CallToolHandlerCustomizer.class);
              assertThat(context).hasSingleBean(GetPromptHandlerCustomizer.class);
              assertThat(context).hasSingleBean(ReadResourceHandlerCustomizer.class);
              assertThat(context).hasSingleBean(ReadResourceTemplateHandlerCustomizer.class);
            });
  }

  @Test
  void server_operation_customizer_attaches_the_interceptor_with_the_handler_method_name() {
    runner
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              McpServerOperationCustomizer customizer =
                  context.getBean(McpServerOperationCustomizer.class);
              JsonRpcMethodHandlerConfig config = mock(JsonRpcMethodHandlerConfig.class);
              when(config.name()).thenReturn("tools/call");

              customizer.customize(config);

              verify(config).interceptor(any(McpServerOperationInterceptor.class));
            });
  }

  @Test
  void server_operation_customizer_backs_off_without_a_translator_registry() {
    runner
        .withUserConfiguration(RegistryOnlyConfig.class)
        .run(context -> assertThat(context).doesNotHaveBean(McpServerOperationCustomizer.class));
  }

  @Test
  void ripcurl_default_observation_customizer_backs_off_to_mocapi_s() {
    // Pins the ordering contract (ADR-0030): MocapiO11yAutoConfiguration is declared
    // beforeName=RipCurlObservationAutoConfiguration so ripcurl's @ConditionalOnMissingBean
    // sees mocapi's bean. If the ordering regresses, BOTH customizers register and every
    // request produces two observations over the same interval — silently.
    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                MocapiO11yAutoConfiguration.class, RipCurlObservationAutoConfiguration.class))
        .withUserConfiguration(ObservationRegistryConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(JsonRpcObservationCustomizer.class);
              assertThat(context.getBean(JsonRpcObservationCustomizer.class))
                  .isInstanceOf(McpServerOperationCustomizer.class);
              assertThat(context).doesNotHaveBean("jsonRpcObservationCustomizer");
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

              verify(config).observationInterceptor(any(MethodInterceptor.class));
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

              verify(config).observationInterceptor(any(MethodInterceptor.class));
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

              verify(config).observationInterceptor(any(MethodInterceptor.class));
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

              verify(config).observationInterceptor(any(MethodInterceptor.class));
            });
  }

  @Test
  void server_operation_customizer_resolves_tcp_transport_from_the_module_s_own_classpath() {
    // This module has both HTTP and stdio transports on its classpath, so bean creation always
    // resolves the "HTTP present" branch of networkTransport(); the other branches are covered by
    // MocapiO11yAutoConfigurationNetworkTransportTest, which isolates that private helper directly.
    runner
        .withUserConfiguration(TestObservationRegistryConfig.class)
        .run(
            context -> {
              McpServerOperationCustomizer customizer =
                  context.getBean(McpServerOperationCustomizer.class);
              TestObservationRegistry probe = context.getBean(TestObservationRegistry.class);

              JsonRpcMethodHandlerConfig config = mock(JsonRpcMethodHandlerConfig.class);
              when(config.name()).thenReturn("tools/list");
              AtomicReference<MethodInterceptor<JsonNode>> captured = new AtomicReference<>();
              when(config.interceptor(any()))
                  .thenAnswer(
                      invocation -> {
                        captured.set(invocation.getArgument(0));
                        return config;
                      });

              customizer.customize(config);
              captured.get().intercept(successfulInvocation(JsonNodeFactory.instance.objectNode()));

              TestObservationRegistryAssert.assertThat(probe)
                  .hasObservationWithNameEqualTo(McpServerOperationInterceptor.OBSERVATION_NAME)
                  .that()
                  .hasLowCardinalityKeyValue("network.transport", "tcp");
            });
  }

  private static MethodInvocation<JsonNode> successfulInvocation(JsonNode result) {
    return MethodInvocation.of(null, null, null, new Object[0], () -> result);
  }

  @Test
  void inactive_when_no_observation_registry_bean_present() {
    runner.run(
        context -> {
          assertThat(context).doesNotHaveBean(McpServerOperationCustomizer.class);
          assertThat(context).doesNotHaveBean(CallToolHandlerCustomizer.class);
        });
  }

  @Configuration
  static class ObservationRegistryConfig {
    @Bean
    ObservationRegistry observationRegistry() {
      return ObservationRegistry.create();
    }

    @Bean
    JsonRpcExceptionTranslatorRegistry translators() {
      return mock(JsonRpcExceptionTranslatorRegistry.class);
    }
  }

  @Configuration
  static class RegistryOnlyConfig {
    @Bean
    ObservationRegistry observationRegistry() {
      return ObservationRegistry.create();
    }
  }

  @Configuration
  static class TestObservationRegistryConfig {
    // A single bean: TestObservationRegistry already implements ObservationRegistry, so it
    // satisfies both the @ConditionalOnBean(ObservationRegistry.class) gate and the
    // TestObservationRegistry lookup used to make assertions below.
    @Bean
    TestObservationRegistry observationRegistry() {
      return TestObservationRegistry.create();
    }

    @Bean
    JsonRpcExceptionTranslatorRegistry translators() {
      return mock(JsonRpcExceptionTranslatorRegistry.class);
    }
  }
}
