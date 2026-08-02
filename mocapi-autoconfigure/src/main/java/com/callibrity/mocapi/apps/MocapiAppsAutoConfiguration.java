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
package com.callibrity.mocapi.apps;

import com.callibrity.mocapi.server.autoconfigure.HandlerMethodsCache;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the MCP Apps handler customizers (ADR-0039) and the {@code ui} capability when
 * mocapi-apps is present.
 */
@AutoConfiguration
@ConditionalOnClass(UiCapabilityCustomizer.class)
public class MocapiAppsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AppsToolDescriptorCustomizer appsToolDescriptorCustomizer(ObjectMapper objectMapper) {
    return new AppsToolDescriptorCustomizer(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AppsResourceDescriptorCustomizer appsResourceDescriptorCustomizer(
      ObjectMapper objectMapper) {
    return new AppsResourceDescriptorCustomizer(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public UiCapabilityCustomizer uiCapabilityCustomizer(ObjectMapper objectMapper) {
    return new UiCapabilityCustomizer(objectMapper);
  }

  /**
   * Serve-mode contributor (ADR-0036): contributes a {@code ui://} resource for every
   * {@code @McpUi(resource=…)} tool. Collected as a {@link ResourceContributor} into the one
   * resources service alongside the annotation scan.
   */
  @Bean
  public ResourceContributor appUiResourceContributor(
      ObjectProvider<HandlerMethodsCache> handlerMethodsCache,
      ResourceLoader resourceLoader,
      ObjectMapper objectMapper) {
    return new AppUiResourceContributor(
        handlerMethodsCache.getIfAvailable(), resourceLoader, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public McpUiReferenceValidator mcpUiReferenceValidator(
      ObjectProvider<McpToolsService> toolsService,
      ObjectProvider<McpResourcesService> resourcesService) {
    return new McpUiReferenceValidator(toolsService, resourcesService);
  }
}
