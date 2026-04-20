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
package com.callibrity.mocapi.audit;

import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import org.jwcarman.methodical.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires {@link AuditLoggingInterceptor} onto every MCP handler via the per-handler customizer SPI.
 * One customizer bean per handler kind bakes the descriptor's name / uri / uriTemplate into the
 * interceptor at build time so the hot path does no reflection.
 *
 * <p>The default {@link AuditCallerIdentityProvider} activates based on classpath: {@link
 * DefaultAuditCallerIdentityProvider} (reading Spring Security's {@code SecurityContextHolder})
 * when Spring Security is present, a trivial {@code "anonymous"}-returning provider otherwise.
 * Either default is replaceable by a user {@code @Bean}.
 */
@AutoConfiguration
@ConditionalOnClass({AuditLoggingInterceptor.class, MethodInterceptor.class})
@EnableConfigurationProperties(MocapiAuditProperties.class)
public class MocapiAuditAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MocapiAuditAutoConfiguration.class);
  private static final int ORDER = 200;

  @Bean
  @Order(ORDER)
  public CallToolHandlerCustomizer mcpAuditToolCustomizer(
      AuditCallerIdentityProvider callerProvider,
      MocapiAuditProperties properties,
      ObjectMapper objectMapper) {
    return config -> {
      var name = config.descriptor().name();
      config.interceptor(
          new AuditLoggingInterceptor(
              "tool", name, callerProvider, properties.isHashArguments(), objectMapper));
      log.info(
          "Attached {} interceptor to tool \"{}\"",
          AuditLoggingInterceptor.class.getSimpleName(),
          name);
    };
  }

  @Bean
  @Order(ORDER)
  public GetPromptHandlerCustomizer mcpAuditPromptCustomizer(
      AuditCallerIdentityProvider callerProvider,
      MocapiAuditProperties properties,
      ObjectMapper objectMapper) {
    return config -> {
      var name = config.descriptor().name();
      config.interceptor(
          new AuditLoggingInterceptor(
              "prompt", name, callerProvider, properties.isHashArguments(), objectMapper));
      log.info(
          "Attached {} interceptor to prompt \"{}\"",
          AuditLoggingInterceptor.class.getSimpleName(),
          name);
    };
  }

  @Bean
  @Order(ORDER)
  public ReadResourceHandlerCustomizer mcpAuditResourceCustomizer(
      AuditCallerIdentityProvider callerProvider,
      MocapiAuditProperties properties,
      ObjectMapper objectMapper) {
    return config -> {
      var uri = config.descriptor().uri();
      config.interceptor(
          new AuditLoggingInterceptor(
              "resource", uri, callerProvider, properties.isHashArguments(), objectMapper));
      log.info(
          "Attached {} interceptor to resource \"{}\"",
          AuditLoggingInterceptor.class.getSimpleName(),
          uri);
    };
  }

  @Bean
  @Order(ORDER)
  public ReadResourceTemplateHandlerCustomizer mcpAuditResourceTemplateCustomizer(
      AuditCallerIdentityProvider callerProvider,
      MocapiAuditProperties properties,
      ObjectMapper objectMapper) {
    return config -> {
      var uriTemplate = config.descriptor().uriTemplate();
      config.interceptor(
          new AuditLoggingInterceptor(
              "resource_template",
              uriTemplate,
              callerProvider,
              properties.isHashArguments(),
              objectMapper));
      log.info(
          "Attached {} interceptor to resource_template \"{}\"",
          AuditLoggingInterceptor.class.getSimpleName(),
          uriTemplate);
    };
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
  static class SpringSecurityCallerIdentityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditCallerIdentityProvider auditCallerIdentityProvider() {
      return new DefaultAuditCallerIdentityProvider();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnMissingClass("org.springframework.security.core.context.SecurityContextHolder")
  static class AnonymousCallerIdentityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditCallerIdentityProvider auditCallerIdentityProvider() {
      return () -> AuditCallerIdentityProvider.ANONYMOUS;
    }
  }
}
