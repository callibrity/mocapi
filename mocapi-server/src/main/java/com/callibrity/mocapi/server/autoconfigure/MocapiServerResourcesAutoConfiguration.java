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

import org.jwcarman.methodical.MethodInvokerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.util.StringValueResolver;

@AutoConfiguration(before = MocapiServerAutoConfiguration.class)
public class MocapiServerResourcesAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ResourceServiceMcpResourceProvider.class)
  public ResourceServiceMcpResourceProvider mcpProtocolResourceServiceMcpResourceProvider(
      ApplicationContext context,
      MethodInvokerFactory invokerFactory,
      ObjectProvider<ConversionService> conversionService,
      StringValueResolver mcpAnnotationValueResolver) {
    return new ResourceServiceMcpResourceProvider(
        context,
        invokerFactory,
        conversionService.getIfAvailable(DefaultConversionService::getSharedInstance),
        mcpAnnotationValueResolver);
  }
}
