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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.api.tools.ToolService;
import com.callibrity.mocapi.server.substrate.SubstrateTestSupport;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.util.List;
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
class ToolServiceMcpToolProviderTest {

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

  @ToolService
  static class SampleToolService {

    @ToolMethod(description = "Says hello")
    public String hello() {
      return "Hello!";
    }
  }

  @Test
  void discovers_tool_service_beans() {
    contextRunner
        .withBean(SampleToolService.class, SampleToolService::new)
        .run(
            context -> {
              ToolServiceMcpToolProvider provider =
                  context.getBean(ToolServiceMcpToolProvider.class);
              List<McpTool> tools = provider.getMcpTools();
              assertThat(tools).hasSize(1);
              assertThat(tools.getFirst().descriptor().name()).endsWith("hello");
            });
  }

  @Test
  void returns_empty_list_when_no_tool_service_beans() {
    contextRunner.run(
        context -> {
          ToolServiceMcpToolProvider provider = context.getBean(ToolServiceMcpToolProvider.class);
          assertThat(provider.getMcpTools()).isEmpty();
        });
  }

  @Test
  void returns_unmodifiable_list() {
    contextRunner
        .withBean(SampleToolService.class, SampleToolService::new)
        .run(
            context -> {
              ToolServiceMcpToolProvider provider =
                  context.getBean(ToolServiceMcpToolProvider.class);
              List<McpTool> tools = provider.getMcpTools();
              assertThat(tools).isUnmodifiable();
            });
  }
}
