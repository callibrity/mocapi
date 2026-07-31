/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.apps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.ReadResourceResult;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerResourcesAutoConfiguration;
import com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpUiReferenceValidatorTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PropertyPlaceholderAutoConfiguration.class,
                  MocapiServerResourcesAutoConfiguration.class,
                  MocapiServerToolsAutoConfiguration.class,
                  MocapiServerAutoConfiguration.class,
                  MocapiAppsAutoConfiguration.class))
          .withUserConfiguration(Infra.class);

  @Configuration(proxyBeanMethods = false)
  static class Infra {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    JsonRpcDispatcher jsonRpcDispatcher() {
      return mock(JsonRpcDispatcher.class);
    }
  }

  static class ValidApp {
    @McpAppResource(uri = "ui://dash", name = "Dash")
    public ReadResourceResult ui() {
      return ReadResourceResult.ofText("ui://dash", "text/html;profile=mcp-app", "<html></html>");
    }

    @McpTool(name = "ok", description = "ok")
    @McpUi("ui://dash")
    public String ok() {
      return "ok";
    }
  }

  static class DanglingApp {
    @McpTool(name = "bad", description = "bad")
    @McpUi("ui://nope")
    public String bad() {
      return "bad";
    }
  }

  @Test
  void starts_when_every_mcpui_link_resolves() {
    runner
        .withBean(ValidApp.class, ValidApp::new)
        .run(context -> assertThat(context).hasNotFailed());
  }

  @Test
  void fails_fast_when_an_mcpui_link_dangles() {
    runner
        .withBean(DanglingApp.class, DanglingApp::new)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ui://nope")
                    .hasMessageContaining("bad"));
  }
}
