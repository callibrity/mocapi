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

import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.tools.McpToolContextScopedValueResolver;
import com.callibrity.mocapi.server.tools.McpToolProvider;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.server.tools.annotation.DefaultAnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(before = MocapiServerAutoConfiguration.class)
@EnableConfigurationProperties({MocapiServerToolsProperties.class, MocapiServerProperties.class})
@PropertySource("classpath:mocapi-server-tools-defaults.properties")
@RequiredArgsConstructor
public class MocapiServerToolsAutoConfiguration {

  private final MocapiServerToolsProperties props;
  private final MocapiServerProperties mocapiProperties;

  @Bean
  @ConditionalOnMissingBean(McpToolContextScopedValueResolver.class)
  public McpToolContextScopedValueResolver mcpProtocolToolContextScopedValueResolver() {
    return new McpToolContextScopedValueResolver();
  }

  @Bean
  @ConditionalOnMissingBean(McpToolParamsResolver.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public McpToolParamsResolver mcpProtocolToolParamsResolver(ObjectMapper objectMapper) {
    return new McpToolParamsResolver(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean(McpToolsService.class)
  public McpToolsService mcpProtocolToolsService(
      List<McpToolProvider> toolProviders,
      ObjectMapper objectMapper,
      McpResponseCorrelationService correlationService) {
    return new McpToolsService(
        toolProviders,
        objectMapper,
        correlationService,
        mocapiProperties.getPagination().getPageSize());
  }

  @Bean
  @ConditionalOnMissingBean(MethodSchemaGenerator.class)
  public MethodSchemaGenerator mcpProtocolMethodSchemaGenerator(ObjectMapper mapper) {
    return new DefaultMethodSchemaGenerator(mapper, props.getSchemaVersion());
  }

  @Bean
  @ConditionalOnMissingBean(AnnotationMcpToolProviderFactory.class)
  public AnnotationMcpToolProviderFactory mcpProtocolAnnotationMcpToolProviderFactory(
      MethodSchemaGenerator generator, MethodInvokerFactory invokerFactory) {
    return new DefaultAnnotationMcpToolProviderFactory(generator, invokerFactory);
  }

  @Bean
  @ConditionalOnMissingBean(ToolServiceMcpToolProvider.class)
  public ToolServiceMcpToolProvider mcpProtocolToolServiceMcpToolProvider(
      ApplicationContext context,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory) {
    return new ToolServiceMcpToolProvider(context, generator, invokerFactory);
  }
}
