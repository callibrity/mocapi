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
package com.callibrity.mocapi.autoconfigure;

import com.callibrity.mocapi.autoconfigure.sse.McpSessionManager;
import com.callibrity.mocapi.autoconfigure.sse.McpStreamingController;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpServerCapability;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
@EnableConfigurationProperties(MocapiProperties.class)
@PropertySource("classpath:mocapi-defaults.properties")
@RequiredArgsConstructor
public class MocapiAutoConfiguration {

  // ------------------------------ FIELDS ------------------------------

  private final MocapiProperties props;

  // -------------------------- OTHER METHODS --------------------------

  @Bean
  public McpServer mcpServer(List<McpServerCapability> serverCapabilities) {
    return new McpServer(serverCapabilities, props.getServerInfo(), props.getInstructions());
  }

  @Bean
  @ConditionalOnMissingBean(name = "mcpObjectMapper")
  public ObjectMapper mcpObjectMapper() {
    return JsonMapper.builder()
        .changeDefaultPropertyInclusion(v -> v.withValueInclusion(JsonInclude.Include.NON_NULL))
        .build();
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public McpSessionManager mcpSessionManager() {
    return new McpSessionManager();
  }

  @Bean
  @ConditionalOnMissingBean(name = "mcpTaskExecutor")
  public TaskExecutor mcpTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("mcp-stream-");
    executor.initialize();
    return executor;
  }

  @Bean
  @ConditionalOnMissingBean
  public McpStreamingController mcpStreamingController(
      McpServer mcpServer,
      McpSessionManager sessionManager,
      TaskExecutor mcpTaskExecutor,
      @Qualifier("mcpObjectMapper") ObjectMapper mcpObjectMapper) {
    return new McpStreamingController(
        mcpServer, sessionManager, mcpTaskExecutor, mcpObjectMapper, props.getAllowedOrigins());
  }
}
