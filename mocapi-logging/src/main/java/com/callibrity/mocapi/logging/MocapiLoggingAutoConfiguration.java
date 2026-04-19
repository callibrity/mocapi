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

import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration that attaches an {@link McpMdcInterceptor} to every MCP handler via the
 * per-handler customizer SPI. One customizer bean per handler kind bakes the descriptor's
 * name/uri/uriTemplate into the interceptor at build time, so the hot path does no reflection.
 */
@AutoConfiguration
@ConditionalOnClass({MDC.class, MethodInterceptor.class, McpSession.class})
public class MocapiLoggingAutoConfiguration {

  @Bean
  public CallToolHandlerCustomizer mcpMdcToolCustomizer() {
    return config -> config.interceptor(new McpMdcInterceptor("tool", config.descriptor().name()));
  }

  @Bean
  public GetPromptHandlerCustomizer mcpMdcPromptCustomizer() {
    return config ->
        config.interceptor(new McpMdcInterceptor("prompt", config.descriptor().name()));
  }

  @Bean
  public ReadResourceHandlerCustomizer mcpMdcResourceCustomizer() {
    return config ->
        config.interceptor(new McpMdcInterceptor("resource", config.descriptor().uri()));
  }

  @Bean
  public ReadResourceTemplateHandlerCustomizer mcpMdcResourceTemplateCustomizer() {
    return config ->
        config.interceptor(
            new McpMdcInterceptor("resource_template", config.descriptor().uriTemplate()));
  }
}
