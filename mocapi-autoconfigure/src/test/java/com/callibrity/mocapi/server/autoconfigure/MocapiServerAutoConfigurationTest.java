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

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.DefaultMcpServer;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransportResolver;
import com.callibrity.mocapi.server.lifecycle.McpLifecycleService;
import com.callibrity.mocapi.server.logging.McpLoggingService;
import com.callibrity.mocapi.server.ping.McpPingService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.session.McpSessionResolver;
import com.callibrity.mocapi.server.session.McpSessionService;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.mocapi.server.substrate.SubstrateTestSupport;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.substrate.atom.AtomFactory;
import org.jwcarman.substrate.mailbox.MailboxFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
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
    AtomFactory atomFactory() {
      return SubstrateTestSupport.atomFactory();
    }

    @Bean
    MailboxFactory mailboxFactory() {
      return SubstrateTestSupport.mailboxFactory();
    }

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
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
          assertThat(context).hasSingleBean(Implementation.class);
          assertThat(context).hasSingleBean(McpSessionStore.class);
          assertThat(context).hasSingleBean(McpSessionResolver.class);
          assertThat(context).hasSingleBean(McpTransportResolver.class);
          assertThat(context).hasSingleBean(ServerCapabilities.class);
          assertThat(context).hasSingleBean(McpSessionService.class);
          assertThat(context).hasSingleBean(McpResponseCorrelationService.class);
          assertThat(context).hasSingleBean(McpServer.class);
          assertThat(context).hasSingleBean(McpResourcesService.class);
          assertThat(context).hasSingleBean(McpPingService.class);
          assertThat(context).hasSingleBean(McpLifecycleService.class);
          assertThat(context).hasSingleBean(McpLoggingService.class);
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
          assertThat(props.sessionTimeout()).hasHours(1);
          assertThat(props.allowedOrigins()).containsExactly("localhost", "127.0.0.1", "[::1]");
          assertThat(props.elicitation().timeout()).hasMinutes(5);
          assertThat(props.sampling().timeout()).hasSeconds(30);
          assertThat(props.pagination().pageSize()).isEqualTo(50);
        });
  }

  @Test
  void properties_can_be_overridden() {
    contextRunner
        .withPropertyValues(
            "mocapi.server-name=custom-server",
            "mocapi.server-title=Custom Title",
            "mocapi.session-timeout=PT30M",
            "mocapi.pagination.page-size=25")
        .run(
            context -> {
              MocapiServerProperties props = context.getBean(MocapiServerProperties.class);
              assertThat(props.serverName()).isEqualTo("custom-server");
              assertThat(props.serverTitle()).isEqualTo("Custom Title");
              assertThat(props.sessionTimeout()).hasMinutes(30);
              assertThat(props.pagination().pageSize()).isEqualTo(25);
            });
  }

  @Test
  void custom_implementation_overrides_default() {
    Implementation custom = new Implementation("custom", "Custom Server", "9.9.9");
    contextRunner
        .withBean(Implementation.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Implementation.class);
              assertThat(context.getBean(Implementation.class)).isSameAs(custom);
            });
  }

  @Test
  void custom_session_store_overrides_default() {
    McpSessionStore custom = SessionStoreTestSupport.create();
    contextRunner
        .withBean(McpSessionStore.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpSessionStore.class);
              assertThat(context.getBean(McpSessionStore.class)).isSameAs(custom);
            });
  }

  @Test
  void custom_server_capabilities_overrides_default() {
    ServerCapabilities custom = new ServerCapabilities(null, null, null, null, null);
    contextRunner
        .withBean(ServerCapabilities.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(ServerCapabilities.class);
              assertThat(context.getBean(ServerCapabilities.class)).isSameAs(custom);
            });
  }

  @Test
  void custom_ping_service_overrides_default() {
    McpPingService custom = new McpPingService();
    contextRunner
        .withBean(McpPingService.class, () -> custom)
        .run(
            context -> {
              assertThat(context).hasSingleBean(McpPingService.class);
              assertThat(context.getBean(McpPingService.class)).isSameAs(custom);
            });
  }
}
