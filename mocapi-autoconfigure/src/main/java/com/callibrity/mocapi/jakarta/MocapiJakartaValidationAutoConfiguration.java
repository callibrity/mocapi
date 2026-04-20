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
package com.callibrity.mocapi.jakarta;

import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import jakarta.validation.Validator;
import org.jwcarman.methodical.jakarta.JakartaValidationInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Attaches a methodical {@link JakartaValidationInterceptor} to every MCP handler via the
 * per-handler customizer SPI. Activates when a {@link Validator} bean is present — typically
 * supplied by Spring Boot's {@code spring-boot-starter-validation}.
 */
@AutoConfiguration
@ConditionalOnClass(JakartaValidationInterceptor.class)
@ConditionalOnBean(Validator.class)
public class MocapiJakartaValidationAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(MocapiJakartaValidationAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public JakartaValidationInterceptor jakartaValidationInterceptor(Validator validator) {
    return new JakartaValidationInterceptor(validator);
  }

  @Bean
  public CallToolHandlerCustomizer jakartaValidationToolCustomizer(
      JakartaValidationInterceptor interceptor) {
    return config -> {
      config.interceptor(interceptor);
      log.info(
          "Attached {} interceptor to tool \"{}\"",
          JakartaValidationInterceptor.class.getSimpleName(),
          config.descriptor().name());
    };
  }

  @Bean
  public GetPromptHandlerCustomizer jakartaValidationPromptCustomizer(
      JakartaValidationInterceptor interceptor) {
    return config -> {
      config.interceptor(interceptor);
      log.info(
          "Attached {} interceptor to prompt \"{}\"",
          JakartaValidationInterceptor.class.getSimpleName(),
          config.descriptor().name());
    };
  }

  @Bean
  public ReadResourceHandlerCustomizer jakartaValidationResourceCustomizer(
      JakartaValidationInterceptor interceptor) {
    return config -> {
      config.interceptor(interceptor);
      log.info(
          "Attached {} interceptor to resource \"{}\"",
          JakartaValidationInterceptor.class.getSimpleName(),
          config.descriptor().uri());
    };
  }

  @Bean
  public ReadResourceTemplateHandlerCustomizer jakartaValidationResourceTemplateCustomizer(
      JakartaValidationInterceptor interceptor) {
    return config -> {
      config.interceptor(interceptor);
      log.info(
          "Attached {} interceptor to resource_template \"{}\"",
          JakartaValidationInterceptor.class.getSimpleName(),
          config.descriptor().uriTemplate());
    };
  }
}
