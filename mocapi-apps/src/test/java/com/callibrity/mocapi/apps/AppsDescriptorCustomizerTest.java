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

import com.callibrity.mocapi.model.Resource;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.resources.ReadResourceHandlerConfig;
import com.callibrity.mocapi.server.tools.CallToolHandlerConfig;
import java.lang.reflect.Method;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AppsDescriptorCustomizerTest {

  private final ObjectMapper mapper = new ObjectMapper();

  static class Fixture {
    @McpUi(
        value = "ui://dash",
        visibility = {"app"})
    public void tool() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpAppResource(uri = "ui://dash", csp = @Csp(connect = "https://api.example.com"))
    public void ui() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpAppResource(uri = "ui://plain")
    public void resNoCsp() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpAppResource(uri = "ui://res", csp = @Csp(resource = "https://cdn.example.com"))
    public void resResourceCsp() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpAppResource(uri = "ui://res", csp = @Csp(frame = "https://frame.example.com"))
    public void resFrameCsp() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpAppResource(uri = "ui://res", csp = @Csp(baseUri = "https://base.example.com"))
    public void resBaseUriCsp() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    @McpAppResource(
        uri = "ui://res",
        sandbox = {"allow-scripts", "allow-forms"})
    public void resSandbox() {
      // Reflection fixture: only the annotations are read; the body is intentionally empty.
    }

    public void plain() {
      // Reflection fixture: an un-annotated method to exercise the no-op path.
    }
  }

  private Method method(String name) throws Exception {
    return Fixture.class.getMethod(name);
  }

  /** Minimal {@link CallToolHandlerConfig} double: only descriptor/method are exercised. */
  private static final class FakeToolConfig implements CallToolHandlerConfig {
    private Tool descriptor;
    private final Method method;

    FakeToolConfig(Method method, Tool descriptor) {
      this.method = method;
      this.descriptor = descriptor;
    }

    @Override
    public Tool descriptor() {
      return descriptor;
    }

    @Override
    public void descriptor(Tool descriptor) {
      this.descriptor = Objects.requireNonNull(descriptor);
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public CallToolHandlerConfig correlationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return this;
    }

    @Override
    public CallToolHandlerConfig observationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return this;
    }

    @Override
    public CallToolHandlerConfig auditInterceptor(MethodInterceptor<? super JsonNode> interceptor) {
      return this;
    }

    @Override
    public CallToolHandlerConfig validationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return this;
    }

    @Override
    public CallToolHandlerConfig invocationInterceptor(
        MethodInterceptor<? super JsonNode> interceptor) {
      return this;
    }

    @Override
    public CallToolHandlerConfig guard(Guard guard) {
      return this;
    }

    @Override
    public CallToolHandlerConfig resolver(ParameterResolver<? super JsonNode> resolver) {
      return this;
    }
  }

  /** Minimal {@link ReadResourceHandlerConfig} double: only descriptor/method are exercised. */
  private static final class FakeResourceConfig implements ReadResourceHandlerConfig {
    private Resource descriptor;
    private final Method method;

    FakeResourceConfig(Method method, Resource descriptor) {
      this.method = method;
      this.descriptor = descriptor;
    }

    @Override
    public Resource descriptor() {
      return descriptor;
    }

    @Override
    public void descriptor(Resource descriptor) {
      this.descriptor = Objects.requireNonNull(descriptor);
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object bean() {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig correlationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig observationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig auditInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig validationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig invocationInterceptor(
        MethodInterceptor<? super Object> interceptor) {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig guard(Guard guard) {
      return this;
    }

    @Override
    public ReadResourceHandlerConfig resolver(ParameterResolver<? super Object> resolver) {
      return this;
    }
  }

  @Test
  void tool_customizer_stamps_ui_meta_when_McpUi_present() throws Exception {
    var customizer = new AppsToolDescriptorCustomizer(mapper);
    var config =
        new FakeToolConfig(
            method("tool"), new Tool("t", "T", "d", mapper.createObjectNode(), null));
    customizer.customize(config);
    Tool out = config.descriptor();
    assertThat(out.meta().path("ui").path("resourceUri").asString()).isEqualTo("ui://dash");
    assertThat(out.meta().path("ui").path("visibility").get(0).asString()).isEqualTo("app");
  }

  @Test
  void tool_customizer_is_a_noop_without_McpUi() throws Exception {
    var customizer = new AppsToolDescriptorCustomizer(mapper);
    Tool in = new Tool("t", "T", "d", mapper.createObjectNode(), null);
    var config = new FakeToolConfig(method("plain"), in);
    customizer.customize(config);
    assertThat(config.descriptor()).isSameAs(in);
  }

  @Test
  void resource_customizer_stamps_ui_csp_when_McpAppResource_present() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    var config =
        new FakeResourceConfig(
            method("ui"), new Resource("ui://dash", "Dash", "d", "text/html;profile=mcp-app"));
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("ui").path("csp").path("connectDomains").get(0).asString())
        .isEqualTo("https://api.example.com");
  }

  @Test
  void resource_customizer_is_a_noop_without_McpAppResource() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    Resource in = new Resource("res://x", "X", "d", "text/plain");
    var config = new FakeResourceConfig(method("plain"), in);
    customizer.customize(config);
    assertThat(config.descriptor()).isSameAs(in);
  }

  @Test
  void resource_customizer_omits_csp_when_no_domains_are_declared() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    var config =
        new FakeResourceConfig(
            method("resNoCsp"), new Resource("ui://plain", "Plain", "d", "text/html"));
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("ui").isObject()).isTrue();
    assertThat(out.meta().path("ui").path("csp").isMissingNode()).isTrue();
  }

  @Test
  void resource_customizer_stamps_resource_domains() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    var config =
        new FakeResourceConfig(method("resResourceCsp"), new Resource("ui://res", "R", "d", "t"));
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("ui").path("csp").path("resourceDomains").get(0).asString())
        .isEqualTo("https://cdn.example.com");
  }

  @Test
  void resource_customizer_stamps_frame_domains() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    var config =
        new FakeResourceConfig(method("resFrameCsp"), new Resource("ui://res", "R", "d", "t"));
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("ui").path("csp").path("frameDomains").get(0).asString())
        .isEqualTo("https://frame.example.com");
  }

  @Test
  void resource_customizer_stamps_base_uri_domains() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    var config =
        new FakeResourceConfig(method("resBaseUriCsp"), new Resource("ui://res", "R", "d", "t"));
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("ui").path("csp").path("baseUriDomains").get(0).asString())
        .isEqualTo("https://base.example.com");
  }

  @Test
  void resource_customizer_stamps_sandbox_tokens() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    var config =
        new FakeResourceConfig(method("resSandbox"), new Resource("ui://res", "R", "d", "t"));
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("ui").path("sandbox").get(0).asString()).isEqualTo("allow-scripts");
    assertThat(out.meta().path("ui").path("sandbox").get(1).asString()).isEqualTo("allow-forms");
  }

  @Test
  void resource_customizer_preserves_existing_meta() throws Exception {
    var customizer = new AppsResourceDescriptorCustomizer(mapper);
    Resource withMeta =
        new Resource("ui://dash", "Dash", "d", "text/html")
            .withMeta(mapper.createObjectNode().put("existing", "kept"));
    var config = new FakeResourceConfig(method("ui"), withMeta);
    customizer.customize(config);
    Resource out = config.descriptor();
    assertThat(out.meta().path("existing").asString()).isEqualTo("kept");
    assertThat(out.meta().path("ui").path("csp").path("connectDomains").get(0).asString())
        .isEqualTo("https://api.example.com");
  }
}
