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
import com.callibrity.mocapi.model.ResourceRequestParams;
import com.callibrity.mocapi.model.TextResourceContents;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.ObjectMapper;

/**
 * Serve-mode (ADR-0036): a tool declaring {@code @McpUi(value=…, resource=classpath:…)} boots with
 * mocapi contributing the {@code ui://} resource — no {@code @McpAppResource} method — and the
 * bundle is served from the classpath with the app MIME and a default {@code _meta.ui}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AppUiServeModeTest {

  static class ServeModeApp {
    @McpTool(name = "get_time", description = "Get time")
    @McpUi(value = "ui://demo/app.html", resource = "classpath:/ui/serve-mode-app.html")
    public String getTime() {
      return "now";
    }
  }

  static class ConflictApp {
    @McpTool(name = "a")
    @McpUi(value = "ui://demo/app.html", resource = "classpath:/ui/serve-mode-app.html")
    public String a() {
      return "a";
    }

    @McpTool(name = "b")
    @McpUi(value = "ui://demo/app.html", resource = "classpath:/ui/other.html")
    public String b() {
      return "b";
    }
  }

  static class SameUriSameLocationApp {
    @McpTool(name = "a")
    @McpUi(value = "ui://demo/app.html", resource = "classpath:/ui/serve-mode-app.html")
    public String a() {
      return "a";
    }

    @McpTool(name = "b")
    @McpUi(value = "ui://demo/app.html", resource = "classpath:/ui/serve-mode-app.html")
    public String b() {
      return "b";
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
  }

  private ApplicationContextRunner runnerWith(Class<?> app) {
    return new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                PropertyPlaceholderAutoConfiguration.class,
                com.callibrity.mocapi.server.autoconfigure.MocapiServerResourcesAutoConfiguration
                    .class,
                com.callibrity.mocapi.server.autoconfigure.MocapiServerToolsAutoConfiguration.class,
                com.callibrity.mocapi.server.autoconfigure.MocapiServerAutoConfiguration.class,
                MocapiAppsAutoConfiguration.class))
        .withUserConfiguration(Infra.class)
        .withBean(app);
  }

  @Test
  void serve_mode_contributes_and_serves_the_bundle() {
    runnerWith(ServeModeApp.class)
        .run(
            context -> {
              var resources = context.getBean(McpResourcesService.class);
              var descriptor = resources.listResources(null).resources().getFirst();
              assertThat(descriptor.uri()).isEqualTo("ui://demo/app.html");
              assertThat(descriptor.mimeType()).isEqualTo("text/html;profile=mcp-app");
              assertThat(descriptor.meta().path("ui").isObject()).isTrue();
              // Friendly name derived from the URI, not the raw URI (ui://demo/app.html -> "Demo").
              assertThat(descriptor.name()).isEqualTo("Demo");

              var read =
                  (ReadResourceResult)
                      resources.readResource(
                          new ResourceRequestParams("ui://demo/app.html", null, null, null));
              var content = (TextResourceContents) read.contents().getFirst();
              assertThat(content.text()).contains("served from the classpath");
              assertThat(content.mimeType()).isEqualTo("text/html;profile=mcp-app");
            });
  }

  @Test
  void friendly_name_humanizes_the_uri_leaf() {
    assertThat(AppUiResourceContributor.friendlyName("ui://get-time/mcp-app.html"))
        .isEqualTo("Get Time");
    assertThat(AppUiResourceContributor.friendlyName("ui://weather/dashboard"))
        .isEqualTo("Dashboard");
    assertThat(AppUiResourceContributor.friendlyName("ui://foo_bar")).isEqualTo("Foo Bar");
  }

  @Test
  void friendly_name_keeps_a_dotted_leaf_when_it_is_the_only_segment() {
    // leaf == 0, so the trailing-filename trim does not apply even though the segment has a dot.
    assertThat(AppUiResourceContributor.friendlyName("ui://app.html")).isEqualTo("App.html");
  }

  @Test
  void friendly_name_skips_empty_words_from_leading_separators() {
    assertThat(AppUiResourceContributor.friendlyName("ui://-foo")).isEqualTo("Foo");
  }

  @Test
  void friendly_name_falls_back_to_the_uri_when_nothing_usable_remains() {
    assertThat(AppUiResourceContributor.friendlyName("ui://")).isEqualTo("ui://");
  }

  @Test
  void a_null_handler_cache_contributes_no_resources() {
    var contributor =
        new AppUiResourceContributor(null, new DefaultResourceLoader(), new ObjectMapper(), v -> v);
    assertThat(contributor.resources()).isEmpty();
  }

  @Test
  void two_tools_sharing_the_same_uri_and_location_register_a_single_resource() {
    runnerWith(SameUriSameLocationApp.class)
        .run(
            context -> {
              var resources = context.getBean(McpResourcesService.class);
              assertThat(resources.listResources(null).resources()).hasSize(1);
            });
  }

  @Test
  void same_uri_from_two_locations_fails_fast() {
    runnerWith(ConflictApp.class)
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("two different locations"));
  }
}
