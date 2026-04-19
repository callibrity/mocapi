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

import com.callibrity.mocapi.api.resources.McpResourceTemplate;
import com.callibrity.mocapi.api.resources.McpResourceTemplateProvider;
import com.callibrity.mocapi.api.resources.ResourceService;
import com.callibrity.mocapi.server.completions.McpCompletionsService;
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
class ResourceServiceMcpResourceTemplateProvider implements McpResourceTemplateProvider {

  private final ApplicationContext context;
  private final MethodInvokerFactory invokerFactory;
  private final List<ParameterResolver<? super Map<String, String>>> templateResolvers;
  private final StringValueResolver valueResolver;
  private final McpCompletionsService completions;
  private List<AnnotationMcpResourceTemplate> templates;

  ResourceServiceMcpResourceTemplateProvider(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      ConversionService conversionService,
      StringValueResolver valueResolver,
      McpCompletionsService completions) {
    this.context = context;
    this.invokerFactory = invokerFactory;
    this.templateResolvers = List.of(new StringMapArgResolver(conversionService));
    this.valueResolver = valueResolver;
    this.completions = completions;
  }

  @Override
  public List<McpResourceTemplate> getMcpResourceTemplates() {
    return List.copyOf(templates);
  }

  @PostConstruct
  public void initialize() {
    var beans = context.getBeansWithAnnotation(ResourceService.class);
    var templateList = new ArrayList<AnnotationMcpResourceTemplate>();
    beans.forEach(
        (beanName, bean) -> {
          log.info(
              "Registering MCP resource templates for @{} bean \"{}\"...",
              ResourceService.class.getSimpleName(),
              beanName);
          var ts =
              AnnotationMcpResourceTemplate.createTemplates(
                  invokerFactory, templateResolvers, bean, valueResolver);
          ts.forEach(
              t -> {
                log.info(
                    "\tRegistered MCP resource template: \"{}\"", t.descriptor().uriTemplate());
                t.completionCandidates()
                    .forEach(
                        c -> {
                          completions.registerResourceTemplateVariable(
                              t.descriptor().uriTemplate(), c.argumentName(), c.values());
                          log.info(
                              "\t\tRegistered completions for variable \"{}\": {}",
                              c.argumentName(),
                              c.values());
                        });
              });
          templateList.addAll(ts);
        });
    this.templates = List.copyOf(templateList);
  }
}
