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
import com.callibrity.mocapi.api.resources.McpResourceProvider;
import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.resources.McpResourceTemplateProvider;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.server.resources.annotation.AnnotationMcpResource;
import com.callibrity.mocapi.server.resources.annotation.AnnotationMcpResourceTemplate;
import com.callibrity.mocapi.server.util.StringMapArgResolver;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.util.StringValueResolver;

@Slf4j
class ResourceServiceMcpResourceProvider
    implements McpResourceProvider, McpResourceTemplateProvider {

  private final ApplicationContext context;
  private final MethodInvokerFactory invokerFactory;
  private final List<ParameterResolver<? super Map<String, String>>> templateResolvers;
  private final StringValueResolver valueResolver;
  private List<AnnotationMcpResource> resources;
  private List<AnnotationMcpResourceTemplate> templates;

  ResourceServiceMcpResourceProvider(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      ConversionService conversionService,
      StringValueResolver valueResolver) {
    this.context = context;
    this.invokerFactory = invokerFactory;
    this.templateResolvers = List.of(new StringMapArgResolver(conversionService));
    this.valueResolver = valueResolver;
  }

  @Override
  public List<McpResource> getMcpResources() {
    return List.copyOf(resources);
  }

  @Override
  public List<McpResourceTemplate> getMcpResourceTemplates() {
    return List.copyOf(templates);
  }

  @PostConstruct
  public void initialize() {
    var beans = context.getBeansWithAnnotation(ResourceService.class);
    var resourceList = new ArrayList<AnnotationMcpResource>();
    var templateList = new ArrayList<AnnotationMcpResourceTemplate>();
    beans.forEach(
        (beanName, bean) -> {
          log.info(
              "Registering MCP resources for @{} bean \"{}\"...",
              ResourceService.class.getSimpleName(),
              beanName);
          var rs = AnnotationMcpResource.createResources(invokerFactory, bean, valueResolver);
          rs.forEach(r -> log.info("\tRegistered MCP resource: \"{}\"", r.descriptor().uri()));
          resourceList.addAll(rs);
          var ts =
              AnnotationMcpResourceTemplate.createTemplates(
                  invokerFactory, templateResolvers, bean, valueResolver);
          ts.forEach(
              t ->
                  log.info(
                      "\tRegistered MCP resource template: \"{}\"", t.descriptor().uriTemplate()));
          templateList.addAll(ts);
        });
    this.resources = List.copyOf(resourceList);
    this.templates = List.copyOf(templateList);
  }
}
