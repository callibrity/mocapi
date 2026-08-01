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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.tools.CallToolHandler;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlers;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.mocapi.server.tools.ToolDescriptorCustomizer;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.schema.MethodSchemaGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.StringValueResolver;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@EnableConfigurationProperties({MocapiServerToolsProperties.class, MocapiServerProperties.class})
@PropertySource("classpath:mocapi-server-tools-defaults.properties")
@RequiredArgsConstructor
public class MocapiServerToolsAutoConfiguration {

  private final Logger log = LoggerFactory.getLogger(MocapiServerToolsAutoConfiguration.class);
  private final MocapiServerToolsProperties props;
  private final MocapiServerProperties mocapiProperties;

  @Bean
  @ConditionalOnMissingBean(McpToolsService.class)
  public McpToolsService mcpProtocolToolsService(
      HandlerMethodsCache cache,
      MethodSchemaGenerator generator,
      ObjectMapper objectMapper,
      MrtrElicitationEngine elicitationEngine,
      CacheSettings cacheSettings,
      @Autowired(required = false) List<CallToolHandlerCustomizer> toolCustomizers,
      @Autowired(required = false) List<ToolDescriptorCustomizer> toolDescriptorCustomizers,
      StringValueResolver mcpAnnotationValueResolver) {
    List<CallToolHandlerCustomizer> customizers =
        toolCustomizers == null ? List.of() : toolCustomizers;
    List<ToolDescriptorCustomizer> descriptorCustomizers =
        toolDescriptorCustomizers == null ? List.of() : toolDescriptorCustomizers;
    CallToolHandlers.BuildParams buildParams =
        new CallToolHandlers.BuildParams(
            generator,
            objectMapper,
            customizers,
            descriptorCustomizers,
            mcpAnnotationValueResolver::resolveStringValue,
            props.isValidateOutput());
    List<CallToolHandler> handlers =
        cache.forAnnotation(McpTool.class).stream()
            .map(
                bm -> {
                  CallToolHandler handler =
                      CallToolHandlers.build(bm.bean(), bm.method(), buildParams);
                  log.info(
                      "Registered MCP tool: \"{}\" (bean \"{}\")",
                      handler.descriptor().name(),
                      bm.beanName());
                  return handler;
                })
            .toList();
    return new McpToolsService(
        handlers,
        objectMapper,
        elicitationEngine,
        mocapiProperties.pagination().pageSize(),
        cacheSettings);
  }

  @Bean
  @ConditionalOnMissingBean(MethodSchemaGenerator.class)
  public MethodSchemaGenerator mcpProtocolMethodSchemaGenerator(ObjectMapper mapper) {
    return new DefaultMethodSchemaGenerator(mapper, props.getSchemaVersion());
  }
}
