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

import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration;
import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.ToolCallReplayInvoker;
import com.callibrity.mocapi.tasks.engine.TaskExecutionEngine;
import com.callibrity.mocapi.tasks.store.InMemoryTaskStore;
import com.callibrity.mocapi.tasks.store.TaskStore;
import io.micrometer.context.ContextSnapshotFactory;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringValueResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the {@code io.modelcontextprotocol/tasks} extension beans when mocapi-tasks is present:
 * the {@link TaskStore}, {@link TaskExecutionEngine}, {@link McpTasksService}, the {@code
 * tools/call} dispatch customizer, the capability and routed-param contributors, and the {@code
 * -32021} exception translator.
 *
 * <p>Runs {@code after} {@link MocapiServerToolsAutoConfiguration} because {@link
 * TaskExecutionEngine} is wired with the {@link McpToolsService} bean as its {@code
 * ToolCallReplayInvoker}.
 */
@AutoConfiguration(after = MocapiServerToolsAutoConfiguration.class)
@ConditionalOnClass(TaskExecutionEngine.class)
@EnableConfigurationProperties(MocapiTasksProperties.class)
public class MocapiTasksAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MocapiTasksAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  public Clock mcpTasksClock() {
    return Clock.systemUTC();
  }

  /**
   * Default {@link TaskStore}: in-memory, single-node. A clustered or durable deployment supplies
   * its own {@link TaskStore} bean to back off this default.
   */
  @Bean
  @ConditionalOnMissingBean(TaskStore.class)
  public InMemoryTaskStore mcpTaskStore(Clock clock, MocapiTasksProperties properties) {
    log.warn(
        "Using the in-memory TaskStore: task state is process-local — NOT multi-node safe, and "
            + "in-flight tasks are lost on restart. Provide a shared TaskStore bean for clustered "
            + "or durable deployments.");
    return new InMemoryTaskStore(clock, properties.sweepInterval());
  }

  @Bean
  @ConditionalOnMissingBean(ContextSnapshotFactory.class)
  public ContextSnapshotFactory mcpTasksContextSnapshotFactory() {
    return ContextSnapshotFactory.builder().build();
  }

  /**
   * {@link McpToolsService} is itself the {@link ToolCallReplayInvoker}, but it also collects the
   * {@link TaskToolCallDispatcher} bean (below) into its {@link
   * com.callibrity.mocapi.server.dispatch.McpDispatchInterceptor} list — a genuine bean-graph cycle
   * if this engine resolved {@link McpToolsService} eagerly. Deferring resolution through {@link
   * ObjectProvider} breaks the cycle: by the time a task actually runs, {@link McpToolsService} has
   * long since finished construction.
   */
  @Bean
  @ConditionalOnMissingBean(TaskExecutionEngine.class)
  public TaskExecutionEngine mcpTaskExecutionEngine(
      TaskStore store,
      ObjectProvider<McpToolsService> toolsService,
      ContextSnapshotFactory contextSnapshotFactory,
      Clock clock) {
    ToolCallReplayInvoker invoker =
        (toolName, arguments, ledger, progressOverride, exchange) ->
            toolsService
                .getObject()
                .invoke(toolName, arguments, ledger, progressOverride, exchange);
    return new TaskExecutionEngine(store, invoker, contextSnapshotFactory, clock);
  }

  @Bean
  @ConditionalOnMissingBean(McpTasksService.class)
  public McpTasksService mcpTasksService(
      TaskStore store,
      TaskExecutionEngine engine,
      McpPrincipalSource principalSource,
      Clock clock,
      ObjectMapper objectMapper) {
    return new McpTasksService(store, engine, principalSource, clock, objectMapper);
  }

  /**
   * The {@code mcpAnnotationValueResolver} bean lives in {@link MocapiServerAutoConfiguration},
   * which some minimal test/embedding apps deliberately exclude while still pulling in mocapi-tasks
   * via classpath auto-configuration. {@link ObjectProvider} keeps that combination working: absent
   * the bean, {@code ${...}} placeholders in {@code @McpTask} attributes simply pass through
   * unresolved instead of failing context startup.
   */
  @Bean
  public TaskToolCallDispatcher mcpTaskToolCallDispatcher(
      TaskExecutionEngine engine,
      McpPrincipalSource principalSource,
      ObjectMapper objectMapper,
      MocapiTasksProperties properties,
      Clock clock,
      ObjectProvider<StringValueResolver> mcpAnnotationValueResolver) {
    var defaults =
        new TaskToolCallDispatcher.Defaults(
            properties.defaultTtl(), properties.defaultPollInterval());
    StringValueResolver resolver = mcpAnnotationValueResolver.getIfAvailable(() -> value -> value);
    return new TaskToolCallDispatcher(
        engine, principalSource, objectMapper, defaults, clock, resolver::resolveStringValue);
  }

  @Bean
  public TaskRequiredExceptionTranslator mcpTaskRequiredTranslator(ObjectMapper objectMapper) {
    return new TaskRequiredExceptionTranslator(objectMapper);
  }

  @Bean
  public TasksCapabilityCustomizer mcpTasksCapabilityCustomizer(ObjectMapper objectMapper) {
    return new TasksCapabilityCustomizer(objectMapper);
  }

  @Bean
  public TasksRoutedParamContributor mcpTasksRoutedParamContributor() {
    return new TasksRoutedParamContributor();
  }
}
