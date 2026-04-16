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

import com.callibrity.mocapi.api.prompts.McpPrompt;
import com.callibrity.mocapi.api.prompts.McpPromptProvider;
import com.callibrity.mocapi.api.prompts.PromptService;
import com.callibrity.mocapi.server.prompts.annotation.AnnotationMcpPrompt;
import com.callibrity.mocapi.server.util.StringMapArgResolver;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;

@Slf4j
class PromptServiceMcpPromptProvider implements McpPromptProvider {

  private final ApplicationContext context;
  private final MethodInvokerFactory invokerFactory;
  private final List<ParameterResolver<? super Map<String, String>>> resolvers;
  private List<AnnotationMcpPrompt> prompts;

  PromptServiceMcpPromptProvider(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      ConversionService conversionService) {
    this.context = context;
    this.invokerFactory = invokerFactory;
    this.resolvers = List.of(new StringMapArgResolver(conversionService));
  }

  @Override
  public List<McpPrompt> getMcpPrompts() {
    return List.copyOf(prompts);
  }

  @PostConstruct
  public void initialize() {
    var beans = context.getBeansWithAnnotation(PromptService.class);
    prompts =
        beans.entrySet().stream()
            .flatMap(
                entry -> {
                  var beanName = entry.getKey();
                  var bean = entry.getValue();
                  log.info(
                      "Registering MCP prompts for @{} bean \"{}\"...",
                      PromptService.class.getSimpleName(),
                      beanName);
                  var list = AnnotationMcpPrompt.createPrompts(invokerFactory, resolvers, bean);
                  list.forEach(
                      p -> log.info("\tRegistered MCP prompt: \"{}\"", p.descriptor().name()));
                  return list.stream();
                })
            .toList();
  }
}
