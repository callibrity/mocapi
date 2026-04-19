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
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.prompts.GetPromptHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerConfig;
import com.callibrity.mocapi.server.resources.ReadResourceTemplateHandlerConfig;
import com.callibrity.mocapi.server.tools.CallToolHandlerConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
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
import tools.jackson.databind.JsonNode;

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

  private static final class StubToolConfig implements CallToolHandlerConfig {
    private final Tool descriptor;
    private final List<MethodInterceptor<? super JsonNode>> interceptors = new ArrayList<>();

    StubToolConfig(Tool descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public Tool descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return dummyMethod();
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public CallToolHandlerConfig interceptor(MethodInterceptor<? super JsonNode> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public CallToolHandlerConfig guard(Guard guard) {
      return this;
    }
  }

  private static final class StubPromptConfig implements GetPromptHandlerConfig {
    private final Prompt descriptor;
    private final List<MethodInterceptor<? super Map<String, String>>> interceptors =
        new ArrayList<>();

    StubPromptConfig(Prompt descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public Prompt descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return dummyMethod();
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public GetPromptHandlerConfig interceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public GetPromptHandlerConfig guard(Guard guard) {
      return this;
    }
  }

  private static final class StubResourceConfig implements ReadResourceHandlerConfig {
    private final Resource descriptor;
    private final List<MethodInterceptor<? super Object>> interceptors = new ArrayList<>();

    StubResourceConfig(Resource descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public Resource descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return dummyMethod();
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig interceptor(MethodInterceptor<? super Object> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceHandlerConfig guard(Guard guard) {
      return this;
    }
  }

  private static final class StubResourceTemplateConfig
      implements ReadResourceTemplateHandlerConfig {
    private final ResourceTemplate descriptor;
    private final List<MethodInterceptor<? super Map<String, String>>> interceptors =
        new ArrayList<>();

    StubResourceTemplateConfig(ResourceTemplate descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public ResourceTemplate descriptor() {
      return descriptor;
    }

    @Override
    public Method method() {
      return dummyMethod();
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public ReadResourceTemplateHandlerConfig interceptor(
        MethodInterceptor<? super Map<String, String>> interceptor) {
      interceptors.add(interceptor);
      return this;
    }

    @Override
    public ReadResourceTemplateHandlerConfig guard(Guard guard) {
      return this;
    }
  }
}
