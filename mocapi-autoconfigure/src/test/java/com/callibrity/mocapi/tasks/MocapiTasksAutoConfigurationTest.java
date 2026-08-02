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
package com.callibrity.mocapi.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.ClientCapabilities;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.tasks.model.CreateTaskResult;
import com.callibrity.mocapi.tasks.store.InMemoryTaskStore;
import com.callibrity.mocapi.tasks.store.TaskStore;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(OutputCaptureExtension.class)
class MocapiTasksAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MocapiServerAutoConfiguration.class,
                  MocapiServerToolsAutoConfiguration.class,
                  MocapiTasksAutoConfiguration.class))
          .withUserConfiguration(Infra.class);

  @Configuration(proxyBeanMethods = false)
  static class Infra {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    JsonRpcDispatcher jsonRpcDispatcher() {
      return mock(JsonRpcDispatcher.class);
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class TaskToolFixtureConfig {

    @Bean
    TaskToolFixture taskToolFixture() {
      return new TaskToolFixture();
    }
  }

  static class TaskToolFixture {
    @McpTask(ttl = "${demo.ttl}")
    @McpTool(description = "placeholder ttl tool")
    public String placeholderTtlTool() {
      return "ok";
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class UserSuppliedTaskStoreConfig {

    static InMemoryTaskStore instance;

    @Bean
    TaskStore taskStore() {
      instance = new InMemoryTaskStore(Clock.systemUTC());
      return instance;
    }
  }

  @Test
  void registers_all_tasks_beans(CapturedOutput output) {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(TaskStore.class);
          assertThat(context.getBean(TaskStore.class)).isInstanceOf(InMemoryTaskStore.class);
          assertThat(context)
              .hasSingleBean(com.callibrity.mocapi.tasks.engine.TaskExecutionEngine.class);
          assertThat(context).hasSingleBean(McpTasksService.class);
          assertThat(context).hasSingleBean(TaskToolCallDispatcher.class);
          assertThat(context).hasSingleBean(TasksCapabilityCustomizer.class);
          assertThat(context).hasSingleBean(TasksRoutedParamContributor.class);
          assertThat(context).hasSingleBean(TaskRequiredExceptionTranslator.class);

          ServerCapabilities capabilities = context.getBean(ServerCapabilities.class);
          assertThat(capabilities.extensions()).containsKey(TasksExtension.EXTENSION_ID);
        });
  }

  @Test
  void property_override_reaches_dispatcher_defaults(CapturedOutput output) {
    runner
        .withPropertyValues("mocapi.tasks.default-ttl=PT5M")
        .run(
            context -> {
              TaskToolCallDispatcher dispatcher = context.getBean(TaskToolCallDispatcher.class);
              assertThat(dispatcher.defaults().ttl()).isEqualTo(java.time.Duration.ofMinutes(5));
              assertThat(dispatcher.defaults().pollInterval())
                  .isEqualTo(java.time.Duration.ofSeconds(2));
            });
  }

  @Test
  void mctask_ttl_resolves_property_placeholder_end_to_end(CapturedOutput output) {
    runner
        .withPropertyValues("demo.ttl=PT7M")
        .withUserConfiguration(TaskToolFixtureConfig.class)
        .run(
            context -> {
              McpToolsService toolsService = context.getBean(McpToolsService.class);
              String toolName =
                  toolsService.allToolDescriptors().stream()
                      .filter(tool -> "placeholder ttl tool".equals(tool.description()))
                      .findFirst()
                      .orElseThrow()
                      .name();
              RequestMeta meta =
                  new RequestMeta(
                      null,
                      "2026-07-28",
                      null,
                      new ClientCapabilities(
                          null,
                          null,
                          null,
                          null,
                          Map.of(
                              TasksExtension.EXTENSION_ID, JsonNodeFactory.instance.objectNode())));
              CallToolRequestParams params =
                  new CallToolRequestParams(
                      toolName, JsonNodeFactory.instance.objectNode(), null, null, meta);

              Object result = toolsService.callTool(params);

              assertThat(result).isInstanceOf(CreateTaskResult.class);
              assertThat(((CreateTaskResult) result).ttlMs())
                  .isEqualTo(Duration.ofMinutes(7).toMillis());
            });
  }

  @Test
  void user_supplied_task_store_backs_off_default(CapturedOutput output) {
    runner
        .withUserConfiguration(UserSuppliedTaskStoreConfig.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(TaskStore.class);
              assertThat(context.getBean(TaskStore.class))
                  .isSameAs(UserSuppliedTaskStoreConfig.instance);
            });
  }

  @Test
  void default_store_logs_multi_node_warning(CapturedOutput output) {
    runner.run(context -> assertThat(context).hasSingleBean(TaskStore.class));
    assertThat(output.getOut() + output.getErr()).contains("NOT multi-node safe");
  }

  @Test
  void user_supplied_store_does_not_log_warning(CapturedOutput output) {
    runner
        .withUserConfiguration(UserSuppliedTaskStoreConfig.class)
        .run(context -> assertThat(context).hasSingleBean(TaskStore.class));
    assertThat(output.getOut() + output.getErr()).doesNotContain("NOT multi-node safe");
  }
}
