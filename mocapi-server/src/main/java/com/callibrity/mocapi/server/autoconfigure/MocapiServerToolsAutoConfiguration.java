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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.CallToolHandlers;
import com.callibrity.mocapi.server.tools.McpToolContextResolver;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.StringValueResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@EnableConfigurationProperties({MocapiServerToolsProperties.class, MocapiServerProperties.class})
@PropertySource("classpath:mocapi-server-tools-defaults.properties")
@RequiredArgsConstructor
@Slf4j
public class MocapiServerToolsAutoConfiguration {

  private final MocapiServerToolsProperties props;
  private final MocapiServerProperties mocapiProperties;

  @Bean
  @ConditionalOnMissingBean(McpToolsService.class)
  public McpToolsService mcpProtocolToolsService(
      HandlerMethodsCache cache,
      MethodSchemaGenerator generator,
      MethodInvokerFactory invokerFactory,
      ObjectMapper objectMapper,
      McpResponseCorrelationService correlationService,
      @Autowired(required = false) List<MethodInterceptor<? super JsonNode>> toolInterceptors,
      StringValueResolver mcpAnnotationValueResolver) {
    List<ParameterResolver<? super JsonNode>> resolvers =
        List.of(
            new McpToolContextResolver(),
            new McpToolParamsResolver(objectMapper),
            new Jackson3ParameterResolver(objectMapper));
    List<MethodInterceptor<? super JsonNode>> interceptors =
        toolInterceptors == null ? List.of() : toolInterceptors;
    List<CallToolHandler> handlers =
        cache.forAnnotation(McpTool.class).stream()
            .map(
                bm -> {
                  CallToolHandler handler =
                      CallToolHandlers.build(
                          bm.bean(),
                          bm.method(),
                          generator,
                          invokerFactory,
                          resolvers,
                          interceptors,
                          mcpAnnotationValueResolver::resolveStringValue);
                  log.info(
                      "Registered MCP tool: \"{}\" (bean \"{}\")",
                      handler.descriptor().name(),
                      bm.beanName());
                  return handler;
                })
            .toList();
    return new McpToolsService(
        handlers, objectMapper, correlationService, mocapiProperties.pagination().pageSize());
  }

  @Bean
  @ConditionalOnMissingBean(MethodSchemaGenerator.class)
  public MethodSchemaGenerator mcpProtocolMethodSchemaGenerator(ObjectMapper mapper) {
    return new DefaultMethodSchemaGenerator(mapper, props.getSchemaVersion());
  }
}
