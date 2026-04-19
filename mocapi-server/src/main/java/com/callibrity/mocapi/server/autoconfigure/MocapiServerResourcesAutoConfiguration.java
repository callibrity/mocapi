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

import com.callibrity.mocapi.api.resources.McpResourceTemplateProvider;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceHandlers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.StringValueResolver;

@AutoConfiguration(before = MocapiServerAutoConfiguration.class)
@EnableConfigurationProperties(MocapiServerProperties.class)
@RequiredArgsConstructor
@Slf4j
public class MocapiServerResourcesAutoConfiguration {

  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean(ResourceServiceMcpResourceTemplateProvider.class)
  public ResourceServiceMcpResourceTemplateProvider
      mcpProtocolResourceServiceMcpResourceTemplateProvider(
          ApplicationContext context,
          MethodInvokerFactory invokerFactory,
          ObjectProvider<ConversionService> conversionService,
          StringValueResolver mcpAnnotationValueResolver,
          McpCompletionsService completions) {
    return new ResourceServiceMcpResourceTemplateProvider(
        context,
        invokerFactory,
        conversionService.getIfAvailable(DefaultConversionService::getSharedInstance),
        mcpAnnotationValueResolver,
        completions);
  }

  @Bean
  @ConditionalOnMissingBean(McpResourcesService.class)
  public McpResourcesService mcpProtocolResourcesService(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      StringValueResolver mcpAnnotationValueResolver,
      List<McpResourceTemplateProvider> templateProviders) {
    List<ReadResourceHandler> handlers =
        context.getBeansWithAnnotation(ResourceService.class).entrySet().stream()
            .flatMap(
                entry -> {
                  String beanName = entry.getKey();
                  Object bean = entry.getValue();
                  log.info(
                      "Registering MCP resources for @{} bean \"{}\"...",
                      ResourceService.class.getSimpleName(),
                      beanName);
                  List<ReadResourceHandler> perBean =
                      ReadResourceHandlers.discover(
                          bean, invokerFactory, mcpAnnotationValueResolver::resolveStringValue);
                  perBean.forEach(
                      h -> log.info("\tRegistered MCP resource: \"{}\"", h.descriptor().uri()));
                  return perBean.stream();
                })
            .toList();
    return new McpResourcesService(handlers, templateProviders, props.pagination().pageSize());
  }
}
