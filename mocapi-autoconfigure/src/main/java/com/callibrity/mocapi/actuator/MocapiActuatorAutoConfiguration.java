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
package com.callibrity.mocapi.actuator;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the {@link McpActuatorEndpoint} bean when Spring Boot Actuator is on the classpath and
 * the {@code mcp} endpoint is enabled/exposed via standard actuator configuration (default enabled;
 * expose via {@code management.endpoints.web.exposure.include}).
 */
@AutoConfiguration
@ConditionalOnClass(Endpoint.class)
@ConditionalOnAvailableEndpoint(endpoint = McpActuatorEndpoint.class)
public class MocapiActuatorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public McpActuatorEndpoint mcpActuatorEndpoint(
      Implementation serverInfo,
      McpToolsService tools,
      McpPromptsService prompts,
      McpResourcesService resources) {
    return new McpActuatorEndpoint(serverInfo, tools, prompts, resources);
  }
}
