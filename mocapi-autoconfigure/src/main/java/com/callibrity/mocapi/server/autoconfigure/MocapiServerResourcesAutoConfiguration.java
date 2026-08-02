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

import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.server.cache.CacheSettings;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.dispatch.McpDispatchInterceptor;
import com.callibrity.mocapi.server.mrtr.MrtrElicitationEngine;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class MocapiServerResourcesAutoConfiguration {

  private final Logger log = LoggerFactory.getLogger(MocapiServerResourcesAutoConfiguration.class);
  private final MocapiServerProperties props;

  /**
   * The built-in, primary {@link ResourceContributor} — the {@code @McpResource} /
   * {@code @McpResourceTemplate} annotation scan (ADR-0035). Extensions add further contributor
   * beans; all are merged at service construction.
   */
  @Bean
  public ResourceContributor annotationScanResourceContributor(
      HandlerMethodsCache cache,
      ObjectProvider<ConversionService> conversionService,
      StringValueResolver mcpAnnotationValueResolver,
      @Autowired(required = false) List<ReadResourceHandlerCustomizer> resourceCustomizers,
      @Autowired(required = false)
          List<ReadResourceTemplateHandlerCustomizer> resourceTemplateCustomizers) {
    ConversionService cs =
        conversionService.getIfAvailable(DefaultConversionService::getSharedInstance);
    return new AnnotationScanResourceContributor(
        cache,
        cs,
        mcpAnnotationValueResolver,
        resourceCustomizers == null ? List.of() : resourceCustomizers,
        resourceTemplateCustomizers == null ? List.of() : resourceTemplateCustomizers);
  }

  @Bean
  @ConditionalOnMissingBean(McpResourcesService.class)
  public McpResourcesService mcpProtocolResourcesService(
      List<ResourceContributor> contributors,
      McpCompletionsService completions,
      MrtrElicitationEngine elicitationEngine,
      CacheSettings cacheSettings,
      @Autowired(required = false)
          List<McpDispatchInterceptor<ReadResourceHandler, ResourceRequestParams>>
              dispatchInterceptors) {
    List<McpDispatchInterceptor<ReadResourceHandler, ResourceRequestParams>> interceptors =
        dispatchInterceptors == null ? List.of() : dispatchInterceptors;
    contributors.stream()
        .flatMap(c -> c.resourceTemplates().stream())
        .forEach(
            h ->
                h.completionCandidates()
                    .forEach(
                        c -> {
                          completions.registerResourceTemplateVariable(
                              h.uriTemplate(), c.argumentName(), c.values());
                          log.info(
                              "\tRegistered completions for variable \"{}\": {}",
                              c.argumentName(),
                              c.values());
                        }));
    return new McpResourcesService(
        contributors,
        elicitationEngine,
        props.pagination().pageSize(),
        cacheSettings,
        interceptors);
  }
}
