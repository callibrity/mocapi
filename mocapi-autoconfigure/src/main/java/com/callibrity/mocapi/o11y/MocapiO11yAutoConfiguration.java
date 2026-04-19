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
package com.callibrity.mocapi.o11y;

import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Wires {@link McpObservationInterceptor} onto every MCP handler via the per-handler customizer
 * SPI. One customizer bean per handler kind closes over the descriptor's name / uri / uriTemplate
 * at build time so the hot path does no reflection. Activates only when an {@link
 * ObservationRegistry} bean is present — Spring Boot auto-creates one whenever Actuator or any
 * Micrometer Observation autoconfiguration is on the classpath, so this starter lights up
 * automatically when paired with a metrics or tracing stack.
 */
@AutoConfiguration(
    afterName =
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration")
@ConditionalOnClass({McpObservationInterceptor.class, ObservationRegistry.class})
@ConditionalOnBean(ObservationRegistry.class)
public class MocapiO11yAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MocapiO11yAutoConfiguration.class);

  @Bean
  public CallToolHandlerCustomizer mcpObservationToolCustomizer(ObservationRegistry registry) {
    return config -> {
      var name = config.descriptor().name();
      config.interceptor(new McpObservationInterceptor(registry, "tool", name));
      log.info(
          "Attached {} interceptor to tool \"{}\"",
          McpObservationInterceptor.class.getSimpleName(),
          name);
    };
  }

  @Bean
  public GetPromptHandlerCustomizer mcpObservationPromptCustomizer(ObservationRegistry registry) {
    return config -> {
      var name = config.descriptor().name();
      config.interceptor(new McpObservationInterceptor(registry, "prompt", name));
      log.info(
          "Attached {} interceptor to prompt \"{}\"",
          McpObservationInterceptor.class.getSimpleName(),
          name);
    };
  }

  @Bean
  public ReadResourceHandlerCustomizer mcpObservationResourceCustomizer(
      ObservationRegistry registry) {
    return config -> {
      var uri = config.descriptor().uri();
      config.interceptor(new McpObservationInterceptor(registry, "resource", uri));
      log.info(
          "Attached {} interceptor to resource \"{}\"",
          McpObservationInterceptor.class.getSimpleName(),
          uri);
    };
  }

  @Bean
  public ReadResourceTemplateHandlerCustomizer mcpObservationResourceTemplateCustomizer(
      ObservationRegistry registry) {
    return config -> {
      var uriTemplate = config.descriptor().uriTemplate();
      config.interceptor(new McpObservationInterceptor(registry, "resource_template", uriTemplate));
      log.info(
          "Attached {} interceptor to resource_template \"{}\"",
          McpObservationInterceptor.class.getSimpleName(),
          uriTemplate);
    };
  }
}
