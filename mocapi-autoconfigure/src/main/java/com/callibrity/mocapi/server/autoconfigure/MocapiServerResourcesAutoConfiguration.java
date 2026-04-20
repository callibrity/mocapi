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

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlers;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandler;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.StringValueResolver;

@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@EnableConfigurationProperties(MocapiServerProperties.class)
@RequiredArgsConstructor
@Slf4j
public class MocapiServerResourcesAutoConfiguration {

  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean(McpResourcesService.class)
  public McpResourcesService mcpProtocolResourcesService(
      HandlerMethodsCache cache,
      ObjectProvider<ConversionService> conversionService,
      StringValueResolver mcpAnnotationValueResolver,
      McpCompletionsService completions,
      @Autowired(required = false) List<ReadResourceHandlerCustomizer> resourceCustomizers,
      @Autowired(required = false)
          List<ReadResourceTemplateHandlerCustomizer> resourceTemplateCustomizers) {
    ConversionService cs =
        conversionService.getIfAvailable(DefaultConversionService::getSharedInstance);
    List<ReadResourceHandlerCustomizer> resourceCustoms =
        resourceCustomizers == null ? List.of() : resourceCustomizers;
    List<ReadResourceTemplateHandlerCustomizer> templateCustoms =
        resourceTemplateCustomizers == null ? List.of() : resourceTemplateCustomizers;
    List<ReadResourceHandler> handlers =
        cache.forAnnotation(McpResource.class).stream()
            .map(
                bm -> {
                  ReadResourceHandler handler =
                      ReadResourceHandlers.build(
                          bm.bean(),
                          bm.method(),
                          resourceCustoms,
                          mcpAnnotationValueResolver::resolveStringValue);
                  log.info(
                      "Registered MCP resource: \"{}\" (bean \"{}\")",
                      handler.descriptor().uri(),
                      bm.beanName());
                  return handler;
                })
            .toList();
    List<ReadResourceTemplateHandler> templateHandlers =
        cache.forAnnotation(McpResourceTemplate.class).stream()
            .map(
                bm -> {
                  ReadResourceTemplateHandler handler =
                      ReadResourceTemplateHandlers.build(
                          bm.bean(),
                          bm.method(),
                          cs,
                          templateCustoms,
                          mcpAnnotationValueResolver::resolveStringValue);
                  log.info(
                      "Registered MCP resource template: \"{}\" (bean \"{}\")",
                      handler.descriptor().uriTemplate(),
                      bm.beanName());
                  return handler;
                })
            .toList();
    templateHandlers.forEach(
        h ->
            h.completionCandidates()
                .forEach(
                    c -> {
                      completions.registerResourceTemplateVariable(
                          h.descriptor().uriTemplate(), c.argumentName(), c.values());
                      log.info(
                          "\tRegistered completions for variable \"{}\": {}",
                          c.argumentName(),
                          c.values());
                    }));
    return new McpResourcesService(handlers, templateHandlers, props.pagination().pageSize());
  }
}
