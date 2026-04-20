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
import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.prompts.GetPromptHandler;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.prompts.GetPromptHandlers;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
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
public class MocapiServerPromptsAutoConfiguration {

  private final Logger log = LoggerFactory.getLogger(MocapiServerPromptsAutoConfiguration.class);
  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean(McpPromptsService.class)
  public McpPromptsService mcpProtocolPromptsService(
      HandlerMethodsCache cache,
      ObjectProvider<ConversionService> conversionService,
      StringValueResolver mcpAnnotationValueResolver,
      McpCompletionsService completions,
      @Autowired(required = false) List<GetPromptHandlerCustomizer> promptCustomizers) {
    ConversionService cs =
        conversionService.getIfAvailable(DefaultConversionService::getSharedInstance);
    List<GetPromptHandlerCustomizer> customizers =
        promptCustomizers == null ? List.of() : promptCustomizers;
    List<GetPromptHandler> handlers =
        cache.forAnnotation(McpPrompt.class).stream()
            .map(
                bm -> {
                  GetPromptHandler handler =
                      GetPromptHandlers.build(
                          bm.bean(),
                          bm.method(),
                          cs,
                          customizers,
                          mcpAnnotationValueResolver::resolveStringValue);
                  log.info(
                      "Registered MCP prompt: \"{}\" (bean \"{}\")",
                      handler.descriptor().name(),
                      bm.beanName());
                  return handler;
                })
            .toList();
    handlers.forEach(
        h ->
            h.completionCandidates()
                .forEach(
                    c -> {
                      completions.registerPromptArgument(
                          h.descriptor().name(), c.argumentName(), c.values());
                      log.info(
                          "\tRegistered completions for argument \"{}\": {}",
                          c.argumentName(),
                          c.values());
                    }));
    return new McpPromptsService(handlers, props.pagination().pageSize());
  }
}
