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
package com.callibrity.mocapi.security.spring.autoconfigure;

import com.callibrity.mocapi.security.spring.ScopeGuard;
import com.callibrity.mocapi.security.spring.SpringSecurityGuards;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerCustomizer;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerCustomizer;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Wires {@link SpringSecurityGuards}-driven guards onto every MCP handler via the per-handler
 * customizer SPI. Each customizer reads the user method's {@code @RequiresScope} /
 * {@code @RequiresRole} annotations once at handler-build time and attaches the matching {@link
 * com.callibrity.mocapi.server.guards.Guard}s; the Guard SPI's AND-with-short-circuit evaluation
 * then enforces the combined rule at list and call time.
 *
 * <p>Activates only when both {@code mocapi-spring-security-guards} and Spring Security's core
 * module are on the classpath — the first gate keeps this autoconfig dormant when the feature
 * module is absent, the second keeps the import lines from exploding when Spring Security isn't
 * there.
 */
@AutoConfiguration
@ConditionalOnClass({ScopeGuard.class, Authentication.class})
public class MocapiSpringSecurityGuardsAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(MocapiSpringSecurityGuardsAutoConfiguration.class);

  @Bean
  public CallToolHandlerCustomizer springSecurityToolGuardCustomizer() {
    return config ->
        SpringSecurityGuards.attach(
            config,
            config.method(),
            (c, g) -> {
              c.guard(g);
              log.info(
                  "Attached {} guard to tool \"{}\"",
                  g.getClass().getSimpleName(),
                  config.descriptor().name());
            });
  }

  @Bean
  public GetPromptHandlerCustomizer springSecurityPromptGuardCustomizer() {
    return config ->
        SpringSecurityGuards.attach(
            config,
            config.method(),
            (c, g) -> {
              c.guard(g);
              log.info(
                  "Attached {} guard to prompt \"{}\"",
                  g.getClass().getSimpleName(),
                  config.descriptor().name());
            });
  }

  @Bean
  public ReadResourceHandlerCustomizer springSecurityResourceGuardCustomizer() {
    return config ->
        SpringSecurityGuards.attach(
            config,
            config.method(),
            (c, g) -> {
              c.guard(g);
              log.info(
                  "Attached {} guard to resource \"{}\"",
                  g.getClass().getSimpleName(),
                  config.descriptor().uri());
            });
  }

  @Bean
  public ReadResourceTemplateHandlerCustomizer springSecurityResourceTemplateGuardCustomizer() {
    return config ->
        SpringSecurityGuards.attach(
            config,
            config.method(),
            (c, g) -> {
              c.guard(g);
              log.info(
                  "Attached {} guard to resource_template \"{}\"",
                  g.getClass().getSimpleName(),
                  config.descriptor().uriTemplate());
            });
  }
}
