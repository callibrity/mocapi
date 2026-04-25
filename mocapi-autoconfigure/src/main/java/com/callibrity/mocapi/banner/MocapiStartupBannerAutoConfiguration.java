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
package com.callibrity.mocapi.banner;

import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Wires {@link MocapiStartupBanner} into the application. Runs after every other mocapi autoconfig
 * so by the time the banner fires (on {@code ApplicationReadyEvent}), all dependent beans — handler
 * services, session store, optional observability modules — are registered and discoverable.
 *
 * <p>The banner has no hard dependency on any optional starter (OAuth2, observability, etc.) — it
 * discovers them by class name at runtime. Gated by {@code mocapi.banner.enabled} (default {@code
 * true}); set to {@code false} to suppress without removing this autoconfig.
 */
@AutoConfiguration(after = MocapiServerAutoConfiguration.class)
@ConditionalOnProperty(name = "mocapi.banner.enabled", matchIfMissing = true)
public class MocapiStartupBannerAutoConfiguration {

  @Bean
  public MocapiStartupBanner mocapiStartupBanner(
      ObjectProvider<McpToolsService> tools,
      ObjectProvider<McpPromptsService> prompts,
      ObjectProvider<McpResourcesService> resources,
      ObjectProvider<McpSessionStore> sessionStore,
      Environment env,
      ApplicationContext ctx) {
    return new MocapiStartupBanner(tools, prompts, resources, sessionStore, env, ctx);
  }
}
