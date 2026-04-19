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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Prompt;
import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.ResourceTemplate;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.support.LogCaptor;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubPromptConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubResourceConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubResourceTemplateConfig;
import com.callibrity.mocapi.support.StubHandlerConfigs.StubToolConfig;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.intercept.MethodInvocation;
import org.slf4j.MDC;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiLoggingAutoConfigurationTest {

  private final MocapiLoggingAutoConfiguration autoConfig = new MocapiLoggingAutoConfiguration();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void tool_customizer_bakes_tool_name_into_mdc() {
    var config = new StubToolConfig(new Tool("my-tool", null, null, null, null));

    autoConfig.mcpMdcToolCustomizer().customize(config);

    var captured = runFirstInterceptor(config.interceptors);
    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("tool");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("my-tool");
  }

  @Test
  void prompt_customizer_bakes_prompt_name_into_mdc() {
    var config = new StubPromptConfig(new Prompt("my-prompt", null, null, null, null));

    autoConfig.mcpMdcPromptCustomizer().customize(config);

    var captured = runFirstInterceptor(config.interceptors);
    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("prompt");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("my-prompt");
  }

  @Test
  void resource_customizer_bakes_resource_uri_into_mdc() {
    var config = new StubResourceConfig(new Resource("mem://hello", "hello", null, null));

    autoConfig.mcpMdcResourceCustomizer().customize(config);

    var captured = runFirstInterceptor(config.interceptors);
    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("resource");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("mem://hello");
  }

  @Test
  void resource_template_customizer_bakes_uri_template_into_mdc() {
    var config =
        new StubResourceTemplateConfig(new ResourceTemplate("mem://item/{id}", "item", null, null));

    autoConfig.mcpMdcResourceTemplateCustomizer().customize(config);

    var captured = runFirstInterceptor(config.interceptors);
    assertThat(captured.get().get(McpMdcKeys.HANDLER_KIND)).isEqualTo("resource_template");
    assertThat(captured.get().get(McpMdcKeys.HANDLER_NAME)).isEqualTo("mem://item/{id}");
  }

  @Test
  void tool_customizer_logs_attachment() {
    var config = new StubToolConfig(new Tool("get-weather", null, null, null, null));

    try (var captor = LogCaptor.forClass(MocapiLoggingAutoConfiguration.class)) {
      autoConfig.mcpMdcToolCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached McpMdcInterceptor interceptor to tool \"get-weather\"");
    }
  }

  @Test
  void prompt_customizer_logs_attachment() {
    var config = new StubPromptConfig(new Prompt("summarize", null, null, null, null));

    try (var captor = LogCaptor.forClass(MocapiLoggingAutoConfiguration.class)) {
      autoConfig.mcpMdcPromptCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached McpMdcInterceptor interceptor to prompt \"summarize\"");
    }
  }

  @Test
  void resource_customizer_logs_attachment() {
    var config = new StubResourceConfig(new Resource("mem://hello", "hello", null, null));

    try (var captor = LogCaptor.forClass(MocapiLoggingAutoConfiguration.class)) {
      autoConfig.mcpMdcResourceCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached McpMdcInterceptor interceptor to resource \"mem://hello\"");
    }
  }

  @Test
  void resource_template_customizer_logs_attachment() {
    var config =
        new StubResourceTemplateConfig(new ResourceTemplate("mem://item/{id}", "item", null, null));

    try (var captor = LogCaptor.forClass(MocapiLoggingAutoConfiguration.class)) {
      autoConfig.mcpMdcResourceTemplateCustomizer().customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly(
              "Attached McpMdcInterceptor interceptor to resource_template \"mem://item/{id}\"");
    }
  }

  private static AtomicReference<Map<String, String>> runFirstInterceptor(
      List<? extends MethodInterceptor<?>> interceptors) {
    assertThat(interceptors).hasSize(1);
    var interceptor = (McpMdcInterceptor) interceptors.getFirst();
    AtomicReference<Map<String, String>> captured = new AtomicReference<>();
    interceptor.intercept(
        MethodInvocation.of(
            dummyMethod(),
            new Object(),
            null,
            new Object[0],
            () -> {
              var snapshot = MDC.getCopyOfContextMap();
              captured.set(snapshot == null ? Map.of() : snapshot);
              return null;
            }));
    assertThat(MDC.get(McpMdcKeys.HANDLER_KIND)).isNull();
    assertThat(MDC.get(McpMdcKeys.HANDLER_NAME)).isNull();
    return captured;
  }

  private static Method dummyMethod() {
    try {
      return Object.class.getDeclaredMethod("toString");
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }
}
