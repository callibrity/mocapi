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

import com.callibrity.mocapi.server.completions.McpCompletionsService;
import com.callibrity.mocapi.server.prompts.GetPromptHandlers;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.util.StringMapArgResolver;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterResolver;
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
public class MocapiServerPromptsAutoConfiguration {

  private final MocapiServerProperties props;

  @Bean
  @ConditionalOnMissingBean(McpPromptsService.class)
  public McpPromptsService mcpProtocolPromptsService(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      ObjectProvider<ConversionService> conversionService,
      StringValueResolver mcpAnnotationValueResolver,
      McpCompletionsService completions) {
    List<ParameterResolver<? super Map<String, String>>> resolvers =
        List.of(
            new StringMapArgResolver(
                conversionService.getIfAvailable(DefaultConversionService::getSharedInstance)));
    var handlers =
        GetPromptHandlers.discover(context, invokerFactory, resolvers, mcpAnnotationValueResolver);
    handlers.forEach(
        h ->
            h.completionCandidates()
                .forEach(
                    c -> {
                      completions.registerPromptArgument(
                          h.descriptor().name(), c.argumentName(), c.values());
                      log.info(
                          "\t\tRegistered completions for argument \"{}\": {}",
                          c.argumentName(),
                          c.values());
                    }));
    return new McpPromptsService(handlers, props.pagination().pageSize());
  }
}
