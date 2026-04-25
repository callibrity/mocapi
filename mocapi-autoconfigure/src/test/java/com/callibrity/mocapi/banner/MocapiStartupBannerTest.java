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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.prompts.McpPromptsService;
import com.callibrity.mocapi.server.resources.McpResourcesService;
import com.callibrity.mocapi.server.resources.ReadResourceHandler;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandler;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.session.McpSessionStore;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiStartupBannerTest {

  @Nested
  class Headline_format {

    @Test
    void leads_with_ascii_art() {
      String banner = banner().build().render();
      // The ASCII art is multi-line; spot-check the easy-to-recognize bottom row.
      assertThat(banner).contains("|_|  |_|\\___/ \\___\\__,_| .__/|_|");
    }

    @Test
    void includes_a_recognizable_grep_anchor_below_the_art() {
      String banner = banner().build().render();
      // ":: @mocapi :: ready" is the stable string ops grep for in mixed log streams.
      assertThat(banner).contains(":: @mocapi :: ready");
    }
  }

  @Nested
  class Counts_line {

    @Test
    void shows_zero_for_each_axis_when_no_handler_services_are_registered() {
      String banner = banner().build().render();
      assertThat(banner).contains("Tools: 0 | Prompts: 0 | Resources: 0");
    }

    @Test
    void totals_fixed_uri_and_templated_resources_into_a_single_resources_count() {
      String banner =
          banner()
              .withTools(2)
              .withPrompts(1)
              .withFixedResources(3)
              .withTemplatedResources(4)
              .build()
              .render();
      assertThat(banner).contains("Tools: 2 | Prompts: 1 | Resources: 7");
    }
  }

  @Nested
  class Transport_line {

    @Test
    void
        reports_streamable_http_at_the_default_endpoint_when_only_the_http_controller_bean_is_present() {
      String banner = banner().withHttpTransport().build().render();
      assertThat(banner).contains("Transport: Streamable HTTP at /mcp");
    }

    @Test
    void honors_the_configured_endpoint_property() {
      String banner =
          banner().withHttpTransport().withProperty("mocapi.endpoint", "/api/mcp").build().render();
      assertThat(banner).contains("Transport: Streamable HTTP at /api/mcp");
    }

    @Test
    void reports_stdio_when_only_the_stdio_server_bean_is_present() {
      String banner = banner().withStdioTransport().build().render();
      assertThat(banner).contains("Transport: stdio");
    }

    @Test
    void prefers_http_over_stdio_when_both_are_present() {
      String banner = banner().withHttpTransport().withStdioTransport().build().render();
      assertThat(banner).contains("Transport: Streamable HTTP at /mcp");
    }

    @Test
    void reports_none_detected_when_neither_transport_bean_is_present() {
      String banner = banner().build().render();
      assertThat(banner).contains("Transport: (none detected)");
    }
  }

  @Nested
  class Session_store_line {

    @Test
    void reports_simple_class_name_when_a_store_bean_is_present() {
      String banner = banner().withSessionStore().build().render();
      assertThat(banner).contains("Session store: TestSessionStore");
    }

    @Test
    void reports_none_when_no_store_bean_is_registered() {
      String banner = banner().build().render();
      assertThat(banner).contains("Session store: (none)");
    }
  }

  @Nested
  class OAuth2_line {

    @Test
    void reports_disabled_when_no_jwt_decoder_bean_is_registered() {
      String banner = banner().build().render();
      assertThat(banner).contains("OAuth2: disabled");
    }

    @Test
    void reports_enabled_when_a_jwt_decoder_bean_is_present() {
      String banner =
          banner()
              .withBeanOfType("org.springframework.security.oauth2.jwt.JwtDecoder")
              .build()
              .render();
      assertThat(banner).contains("OAuth2: enabled (JWT)");
    }
  }

  @Nested
  class Observability_line {

    @Test
    void omits_the_observability_line_entirely_when_no_modules_are_present() {
      String banner = banner().build().render();
      assertThat(banner).doesNotContain("Observability:");
    }

    @Test
    void lists_each_active_module_in_a_stable_order() {
      String banner =
          banner()
              .withBeanOfType("com.callibrity.mocapi.audit.AuditLoggingInterceptor")
              .withBeanOfType("com.callibrity.mocapi.logging.McpMdcInterceptor")
              .withBeanOfType("com.callibrity.mocapi.o11y.McpObservationFilter")
              .build()
              .render();
      assertThat(banner).contains("Observability: audit, mdc, o11y");
    }

    @Test
    void omits_modules_whose_beans_are_not_present() {
      String banner =
          banner()
              .withBeanOfType("com.callibrity.mocapi.audit.AuditLoggingInterceptor")
              .build()
              .render();
      assertThat(banner)
          .contains("Observability: audit")
          .doesNotContain("mdc")
          .doesNotContain("o11y");
    }
  }

  // --- builder ----------------------------------------------------------

  private static Builder banner() {
    return new Builder();
  }

  private static final class Builder {
    private final MockEnvironment env = new MockEnvironment();
    private final ApplicationContext ctx = mock(ApplicationContext.class);
    private McpToolsService toolsService;
    private McpPromptsService promptsService;
    private McpResourcesService resourcesService;
    private McpSessionStore store;

    Builder() {
      // Mockito's default return for an unstubbed `String[]` method is null, which would NPE
      // inside the banner's `getBeanNamesForType(...).length` check. Default every type to "no
      // bean of this kind"; specific tests override via `withBeanOfType(...)`.
      when(ctx.getBeanNamesForType(any(Class.class))).thenReturn(new String[0]);
    }

    Builder withTools(int n) {
      this.toolsService = mock(McpToolsService.class);
      List<Tool> descriptors =
          IntStream.range(0, n).mapToObj(i -> new Tool("t" + i, null, null, null, null)).toList();
      when(toolsService.allToolDescriptors()).thenReturn(descriptors);
      return this;
    }

    Builder withPrompts(int n) {
      this.promptsService = mock(McpPromptsService.class);
      List<Prompt> descriptors =
          IntStream.range(0, n).mapToObj(i -> new Prompt("p" + i, null, null, null, null)).toList();
      when(promptsService.allDescriptors()).thenReturn(descriptors);
      return this;
    }

    Builder withFixedResources(int n) {
      ensureResourcesService();
      when(resourcesService.allResourceHandlers())
          .thenReturn(Collections.nCopies(n, mock(ReadResourceHandler.class)));
      return this;
    }

    Builder withTemplatedResources(int n) {
      ensureResourcesService();
      when(resourcesService.allResourceTemplateHandlers())
          .thenReturn(Collections.nCopies(n, mock(ReadResourceTemplateHandler.class)));
      return this;
    }

    private void ensureResourcesService() {
      if (resourcesService == null) {
        resourcesService = mock(McpResourcesService.class);
        when(resourcesService.allResourceHandlers()).thenReturn(List.of());
        when(resourcesService.allResourceTemplateHandlers()).thenReturn(List.of());
      }
    }

    Builder withHttpTransport() {
      return withBeanOfType("com.callibrity.mocapi.transport.http.StreamableHttpController");
    }

    Builder withStdioTransport() {
      return withBeanOfType("com.callibrity.mocapi.transport.stdio.StdioServer");
    }

    Builder withSessionStore() {
      this.store = new TestSessionStore();
      return this;
    }

    Builder withProperty(String key, String value) {
      env.setProperty(key, value);
      return this;
    }

    Builder withBeanOfType(String fqcn) {
      try {
        Class<?> type = Class.forName(fqcn);
        when(ctx.getBeanNamesForType(type)).thenReturn(new String[] {"bean"});
      } catch (ClassNotFoundException e) {
        // Class not on test classpath — banner's own try/catch handles this case; nothing to stub.
      }
      return this;
    }

    MocapiStartupBanner build() {
      return new MocapiStartupBanner(
          provider(toolsService),
          provider(promptsService),
          provider(resourcesService),
          provider(store),
          env,
          ctx);
    }

    /**
     * Minimal {@link ObjectProvider} test stub — avoids mocking the generic interface (which would
     * force an unchecked-cast suppression). Only implements the two abstract methods plus {@code
     * getIfAvailable()}, which is the only one the banner actually calls.
     */
    private static <T> ObjectProvider<T> provider(T value) {
      return new ObjectProvider<T>() {
        @Override
        public T getObject() {
          if (value == null) {
            throw new NoSuchBeanDefinitionException("test-stub: none");
          }
          return value;
        }

        @Override
        public T getObject(Object... args) {
          return getObject();
        }

        @Override
        public T getIfAvailable() {
          return value;
        }
      };
    }
  }

  /** Concrete McpSessionStore so the banner reports its simple name as "TestSessionStore". */
  static class TestSessionStore implements McpSessionStore {
    @Override
    public void save(McpSession session, Duration ttl) {}

    @Override
    public void update(String sessionId, McpSession session) {}

    @Override
    public Optional<McpSession> find(String sessionId) {
      return Optional.empty();
    }

    @Override
    public void touch(String sessionId, Duration ttl) {}

    @Override
    public void delete(String sessionId) {}
  }
}
