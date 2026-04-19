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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.Implementation;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiActuatorAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EndpointAutoConfiguration.class,
                  WebEndpointAutoConfiguration.class,
                  MocapiActuatorAutoConfiguration.class))
          .withUserConfiguration(StubBeans.class);

  @Test
  void endpoint_bean_registered_when_exposed_via_actuator() {
    runner
        .withPropertyValues("management.endpoints.web.exposure.include=mcp")
        .run(context -> assertThat(context).hasSingleBean(McpActuatorEndpoint.class));
  }

  @Test
  void endpoint_bean_absent_when_exposure_does_not_include_mcp() {
    runner.run(context -> assertThat(context).doesNotHaveBean(McpActuatorEndpoint.class));
  }

  @Configuration
  static class StubBeans {
    @Bean
    Implementation serverInfo() {
      return new Implementation("mocapi", "Mocapi", "1.0.0");
    }

    @Bean
    McpToolsService toolsService() {
      return mock(McpToolsService.class);
    }

    @Bean
    McpPromptsService promptsService() {
      return mock(McpPromptsService.class);
    }

    @Bean
    McpResourcesService resourcesService() {
      return mock(McpResourcesService.class);
    }
  }
}
