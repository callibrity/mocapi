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

import com.callibrity.mocapi.api.resources.McpResource;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlers;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandler;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlers;
import com.callibrity.mocapi.server.resources.ResourceContributor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.StringValueResolver;

/**
 * The primary, built-in {@link ResourceContributor} (ADR-0035): it scans every {@code @McpResource}
 * / {@code @McpResourceTemplate} method discovered by {@link HandlerMethodsCache} and builds the
 * corresponding method-backed handlers, applying the configured customizer chains. It is one
 * contributor among peers — an extension (e.g. MCP Apps) adds another.
 */
public final class AnnotationScanResourceContributor implements ResourceContributor {

  private static final Logger log =
      LoggerFactory.getLogger(AnnotationScanResourceContributor.class);

  private final List<ReadResourceHandler> resources;
  private final List<ReadResourceTemplateHandler> resourceTemplates;

  public AnnotationScanResourceContributor(
      HandlerMethodsCache cache,
      ConversionService conversionService,
      StringValueResolver valueResolver,
      List<ReadResourceHandlerCustomizer> resourceCustomizers,
      List<ReadResourceTemplateHandlerCustomizer> resourceTemplateCustomizers) {
    this.resources =
        cache.forAnnotation(McpResource.class).stream()
            .map(
                bm -> {
                  ReadResourceHandler handler =
                      ReadResourceHandlers.build(
                          bm.bean(),
                          bm.method(),
                          resourceCustomizers,
                          valueResolver::resolveStringValue);
                  log.info(
                      "Registered MCP resource: \"{}\" (bean \"{}\")",
                      handler.descriptor().uri(),
                      bm.beanName());
                  return handler;
                })
            .toList();
    this.resourceTemplates =
        cache.forAnnotation(McpResourceTemplate.class).stream()
            .map(
                bm -> {
                  ReadResourceTemplateHandler handler =
                      ReadResourceTemplateHandlers.build(
                          bm.bean(),
                          bm.method(),
                          conversionService,
                          resourceTemplateCustomizers,
                          valueResolver::resolveStringValue);
                  log.info(
                      "Registered MCP resource template: \"{}\" (bean \"{}\")",
                      handler.descriptor().uriTemplate(),
                      bm.beanName());
                  return handler;
                })
            .toList();
  }

  @Override
  public List<ReadResourceHandler> resources() {
    return resources;
  }

  @Override
  public List<ReadResourceTemplateHandler> resourceTemplates() {
    return resourceTemplates;
  }
}
