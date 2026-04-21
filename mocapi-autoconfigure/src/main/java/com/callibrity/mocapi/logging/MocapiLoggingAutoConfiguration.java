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
package com.callibrity.mocapi.logging;

import com.callibrity.mocapi.server.handler.HandlerKind;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import org.jwcarman.methodical.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

/**
 * Autoconfiguration that attaches an {@link McpMdcInterceptor} to every MCP handler via the
 * per-handler customizer SPI. One customizer bean per handler kind bakes the descriptor's
 * name/uri/uriTemplate plus the bean's simple class name into the interceptor at build time, so the
 * hot path does no reflection.
 */
@AutoConfiguration
@ConditionalOnClass({McpMdcInterceptor.class, MDC.class, MethodInterceptor.class, McpSession.class})
public class MocapiLoggingAutoConfiguration {

  private final Logger log = LoggerFactory.getLogger(MocapiLoggingAutoConfiguration.class);

  @Bean
  @Order(100)
  public CallToolHandlerCustomizer mcpMdcToolCustomizer() {
    return config -> {
      var name = config.descriptor().name();
      var className = targetClassSimpleName(config.bean());
      config.correlationInterceptor(new McpMdcInterceptor(HandlerKind.TOOL, name, className));
      log.info(
          "Attached {} interceptor to tool \"{}\"", McpMdcInterceptor.class.getSimpleName(), name);
    };
  }

  @Bean
  @Order(100)
  public GetPromptHandlerCustomizer mcpMdcPromptCustomizer() {
    return config -> {
      var name = config.descriptor().name();
      var className = targetClassSimpleName(config.bean());
      config.correlationInterceptor(new McpMdcInterceptor(HandlerKind.PROMPT, name, className));
      log.info(
          "Attached {} interceptor to prompt \"{}\"",
          McpMdcInterceptor.class.getSimpleName(),
          name);
    };
  }

  @Bean
  @Order(100)
  public ReadResourceHandlerCustomizer mcpMdcResourceCustomizer() {
    return config -> {
      var uri = config.descriptor().uri();
      var className = targetClassSimpleName(config.bean());
      config.correlationInterceptor(new McpMdcInterceptor(HandlerKind.RESOURCE, uri, className));
      log.info(
          "Attached {} interceptor to resource \"{}\"",
          McpMdcInterceptor.class.getSimpleName(),
          uri);
    };
  }

  @Bean
  @Order(100)
  public ReadResourceTemplateHandlerCustomizer mcpMdcResourceTemplateCustomizer() {
    return config -> {
      var uriTemplate = config.descriptor().uriTemplate();
      var className = targetClassSimpleName(config.bean());
      config.correlationInterceptor(
          new McpMdcInterceptor(HandlerKind.RESOURCE_TEMPLATE, uriTemplate, className));
      log.info(
          "Attached {} interceptor to resource_template \"{}\"",
          McpMdcInterceptor.class.getSimpleName(),
          uriTemplate);
    };
  }

  private static String targetClassSimpleName(Object bean) {
    return AopUtils.getTargetClass(bean).getSimpleName();
  }
}
