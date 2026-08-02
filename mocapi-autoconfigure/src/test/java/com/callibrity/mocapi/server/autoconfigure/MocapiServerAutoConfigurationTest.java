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
package com.callibrity.mocapi.server.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.CacheScope;
import com.callibrity.mocapi.model.CompletionsCapability;
import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.PromptsCapability;
import com.callibrity.mocapi.model.ResourcesCapability;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.DefaultMcpServer;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransportResolver;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.discover.DiscoverHandler;
import com.callibrity.mocapi.server.discover.ServerCapabilitiesCustomizer;
import com.callibrity.mocapi.server.elicitation.ElicitationNotSupportedExceptionTranslator;
import com.callibrity.mocapi.server.exchange.MetaEnvelopeParser;
import com.callibrity.mocapi.server.lifecycle.McpLifecycleService;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.mrtr.RequestStateCodec;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ExtendWith(OutputCaptureExtension.class)
class MocapiServerAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MocapiServerResourcesAutoConfiguration.class,
                  MocapiServerAutoConfiguration.class))
          .withUserConfiguration(InfrastructureConfig.class);

  @Configuration(proxyBeanMethods = false)
  static class InfrastructureConfig {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    JsonRpcDispatcher jsonRpcDispatcher() {
      return mock(JsonRpcDispatcher.class);
    }
  }

  @Test
  void default_beans_are_auto_configured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(Implementation.class);
          assertThat(context).hasSingleBean(McpTransportResolver.class);
          assertThat(context).hasSingleBean(ServerCapabilities.class);
          assertThat(context).hasSingleBean(MetaEnvelopeParser.class);
          assertThat(context).hasSingleBean(RequestStateCodec.class);
          assertThat(context).hasSingleBean(MrtrElicitationEngine.class);
          assertThat(context).hasSingleBean(ElicitationNotSupportedExceptionTranslator.class);
          assertThat(context).hasSingleBean(CacheSettings.class);
          assertThat(context).hasSingleBean(DiscoverHandler.class);
          assertThat(context).hasSingleBean(McpServer.class);
          assertThat(context).hasSingleBean(McpResourcesService.class);
          assertThat(context).hasSingleBean(McpLifecycleService.class);
        });
  }

  @Test
  void mcp_server_bean_is_default_mcp_server() {
    contextRunner.run(
        context ->
            assertThat(context.getBean(McpServer.class)).isInstanceOf(DefaultMcpServer.class));
  }

  @Test
  void implementation_uses_unknown_version_when_build_properties_absent() {
    contextRunner.run(
        context -> {
          Implementation impl = context.getBean(Implementation.class);
          assertThat(impl.version()).isEqualTo("unknown");
        });
  }

  @Test
  void implementation_uses_build_properties_version_when_present() {
    contextRunner
        .withUserConfiguration(BuildPropertiesConfig.class)
        .run(
            context -> {
              Implementation impl = context.getBean(Implementation.class);
              assertThat(impl.version()).isEqualTo("1.2.3");
            });
  }

  @Configuration(proxyBeanMethods = false)
  static class BuildPropertiesConfig {

    @Bean
    BuildProperties buildProperties() {
      java.util.Properties props = new java.util.Properties();
      props.setProperty("version", "1.2.3");
      return new BuildProperties(props);
    }
  }

  @Test
  void default_properties_are_bound() {
    contextRunner.run(
        context -> {
          MocapiServerProperties props = context.getBean(MocapiServerProperties.class);
          assertThat(props.serverName()).isEqualTo("mocapi");
          assertThat(props.serverTitle()).isEqualTo("Callibrity Mocapi MCP Server");
          assertThat(props.allowedOrigins()).containsExactly("localhost", "127.0.0.1", "[::1]");
          assertThat(props.mrtr().secret()).isEmpty();
          assertThat(props.mrtr().ttl()).hasMinutes(5);
          assertThat(props.cache().listTtl()).isZero();
          assertThat(props.cache().readTtl()).isZero();
          assertThat(props.cache().scope()).isEqualTo(CacheScope.PRIVATE);
          assertThat(props.pagination().pageSize()).isEqualTo(50);
        });
  }

  @Test
  void properties_can_be_overridden() {
    contextRunner
        .withPropertyValues(
            "mocapi.server-name=custom-server",
            "mocapi.server-title=Custom Title",
            "mocapi.mrtr.ttl=PT2M",
            "mocapi.cache.list-ttl=PT1M",
            "mocapi.cache.scope=public",
            "mocapi.pagination.page-size=25")
        .run(
            context -> {
              MocapiServerProperties props = context.getBean(MocapiServerProperties.class);
              assertThat(props.serverName()).isEqualTo("custom-server");
              assertThat(props.serverTitle()).isEqualTo("Custom Title");
              assertThat(props.mrtr().ttl()).hasMinutes(2);
              assertThat(props.cache().listTtl()).hasMinutes(1);
              assertThat(props.cache().scope()).isEqualTo(CacheScope.PUBLIC);
              assertThat(props.pagination().pageSize()).isEqualTo(25);
            });
  }

  @Test
  void custom_implementation_overrides_default() {
    Implementation custom = new Implementation("custom", "Custom Server", "9.9.9", null);
    contextRunner
        .withBean(Implementation.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Implementation.class);
              assertThat(context.getBean(Implementation.class)).isSameAs(custom);
            });
  }

  @Test
  void custom_server_capabilities_overrides_default() {
    ServerCapabilities custom =
        new ServerCapabilities(null, null, null, null, null, null, Map.of());
    contextRunner
        .withBean(ServerCapabilities.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ServerCapabilities.class);
              assertThat(context.getBean(ServerCapabilities.class)).isSameAs(custom);
            });
  }

  @Test
  void default_server_capabilities_preserve_historical_defaults() {
    contextRunner.run(
        context -> {
          ServerCapabilities caps = context.getBean(ServerCapabilities.class);
          assertThat(caps.experimental()).isNull();
          assertThat(caps.logging()).isNull();
          assertThat(caps.tools()).isEqualTo(new ToolsCapability(false));
          assertThat(caps.completions()).isEqualTo(new CompletionsCapability());
          assertThat(caps.resources()).isEqualTo(new ResourcesCapability(false, false));
          assertThat(caps.prompts()).isEqualTo(new PromptsCapability(false));
          assertThat(caps.extensions()).isEmpty();
        });
  }

  @Test
  void server_capabilities_customizers_contribute_extensions() {
    ServerCapabilitiesCustomizer tasks =
        caps ->
            caps.extension("io.modelcontextprotocol/tasks", JsonNodeFactory.instance.objectNode());
    contextRunner
        .withBean("tasksCapability", ServerCapabilitiesCustomizer.class, () -> tasks)
        .run(
            context -> {
              ServerCapabilities caps = context.getBean(ServerCapabilities.class);
              assertThat(caps.extensions()).containsKey("io.modelcontextprotocol/tasks");
              assertThat(caps.tools()).isEqualTo(new ToolsCapability(false));
            });
  }

  @Test
  void warns_when_customizers_are_discarded_by_a_user_supplied_server_capabilities_bean(
      CapturedOutput output) {
    ServerCapabilities custom =
        new ServerCapabilities(null, null, null, null, null, null, Map.of());
    ServerCapabilitiesCustomizer discarded =
        caps ->
            caps.extension("io.modelcontextprotocol/tasks", JsonNodeFactory.instance.objectNode());
    contextRunner
        .withBean(ServerCapabilities.class, () -> custom)
        .withBean("discardedCustomizer", ServerCapabilitiesCustomizer.class, () -> discarded)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ServerCapabilitiesOverrideAuditor.class);
              assertThat(output).contains("discardedCustomizer");
            });
  }

  @Test
  void no_warning_when_customizers_are_applied_to_the_default_server_capabilities(
      CapturedOutput output) {
    ServerCapabilitiesCustomizer applied =
        caps ->
            caps.extension("io.modelcontextprotocol/tasks", JsonNodeFactory.instance.objectNode());
    contextRunner
        .withBean("appliedCustomizer", ServerCapabilitiesCustomizer.class, () -> applied)
        .run(context -> assertThat(output).doesNotContain("were never applied"));
  }

  @Test
  void auditor_bean_is_always_registered_even_with_no_customizers() {
    // Unconditional registration is the point of the fix: gating this bean on
    // ServerCapabilitiesCustomizer beans existing would evaluate at auto-configuration
    // processing time, before deferred producer auto-configurations (tasks, apps) run.
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(ServerCapabilitiesOverrideAuditor.class));
  }

  @Test
  void custom_request_state_codec_overrides_default() {
    RequestStateCodec custom =
        RequestStateCodec.withEphemeralKey(RequestStateCodec.DEFAULT_TTL, new ObjectMapper());
    contextRunner
        .withBean(RequestStateCodec.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(RequestStateCodec.class);
              assertThat(context.getBean(RequestStateCodec.class)).isSameAs(custom);
            });
  }
}
