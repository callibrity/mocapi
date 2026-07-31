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
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.server.discover.DiscoverHandler;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end proof that the MCP Apps extension works through the real autoconfiguration: a bean
 * declaring a {@code @McpAppResource} UI resource and an {@code @McpUi}-linked {@code @McpTool}
 * boots through {@code MocapiServerToolsAutoConfiguration}, {@code
 * MocapiServerResourcesAutoConfiguration}, {@code MocapiServerAutoConfiguration}, and {@code
 * MocapiAppsAutoConfiguration}, and the wire output carries the expected {@code _meta.ui} stamps
 * and the {@code io.modelcontextprotocol/ui} capability.
 */
class AppsEndToEndTest {

  static class WeatherApp {
    @McpAppResource(
        uri = "ui://weather/dashboard",
        name = "Weather",
        csp = @Csp(connect = "https://api.weather.com"))
    public ReadResourceResult dashboard() {
      return ReadResourceResult.ofText(
          "ui://weather/dashboard", "text/html;profile=mcp-app", "<html></html>");
    }

    @McpTool(name = "get_weather", description = "Get weather")
    @McpUi("ui://weather/dashboard")
    public String getWeather() {
      return "sunny";
    }
  }

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

    @Bean
    WeatherApp weatherApp() {
      return new WeatherApp();
    }
  }

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PropertyPlaceholderAutoConfiguration.class,
                  com.callibrity.mocapi.server.autoconfigure.MocapiServerResourcesAutoConfiguration
                      .class,
                  com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration
                      .class,
                  com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration.class,
                  MocapiAppsAutoConfiguration.class))
          .withUserConfiguration(Infra.class);

  @Test
  void tool_list_carries_ui_resource_uri() {
    runner.run(
        context -> {
          var tools = context.getBean(McpToolsService.class).listTools(null).tools();
          var tool =
              tools.stream().filter(t -> t.name().equals("get_weather")).findFirst().orElseThrow();
          assertThat(tool.meta().path("ui").path("resourceUri").asString())
              .isEqualTo("ui://weather/dashboard");
        });
  }

  @Test
  void resource_list_carries_ui_csp_and_html_mime() {
    runner.run(
        context -> {
          var resource =
              context.getBean(McpResourcesService.class).listResources(null).resources().getFirst();
          assertThat(resource.uri()).isEqualTo("ui://weather/dashboard");
          assertThat(resource.mimeType()).isEqualTo("text/html;profile=mcp-app");
          assertThat(
                  resource.meta().path("ui").path("csp").path("connectDomains").get(0).asString())
              .isEqualTo("https://api.weather.com");
        });
  }

  @Test
  void discover_advertises_ui_capability() {
    runner.run(
        context -> {
          ServerCapabilities caps =
              context.getBean(DiscoverHandler.class).discover().capabilities();
          assertThat(caps.extensions()).containsKey("io.modelcontextprotocol/ui");
        });
  }
}
