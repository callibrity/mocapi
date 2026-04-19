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
package com.callibrity.mocapi.audit;

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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiAuditAutoConfigurationTest {

  private static final ObjectMapper MAPPER = JsonMapper.builder().build();
  private static final AuditCallerIdentityProvider PROVIDER = () -> "alice";

  private final MocapiAuditAutoConfiguration autoConfig = new MocapiAuditAutoConfiguration();
  private final MocapiAuditProperties properties = new MocapiAuditProperties();

  @Test
  void tool_customizer_attaches_interceptor() {
    var config = new StubToolConfig(new Tool("my-tool", null, null, null, null));

    autoConfig.mcpAuditToolCustomizer(PROVIDER, properties, MAPPER).customize(config);

    assertThat(config.interceptors).hasSize(1);
    assertThat(config.interceptors.getFirst()).isInstanceOf(AuditLoggingInterceptor.class);
  }

  @Test
  void prompt_customizer_attaches_interceptor() {
    var config = new StubPromptConfig(new Prompt("my-prompt", null, null, null, null));

    autoConfig.mcpAuditPromptCustomizer(PROVIDER, properties, MAPPER).customize(config);

    assertThat(config.interceptors).hasSize(1);
    assertThat(config.interceptors.getFirst()).isInstanceOf(AuditLoggingInterceptor.class);
  }

  @Test
  void resource_customizer_attaches_interceptor() {
    var config = new StubResourceConfig(new Resource("mem://hello", "hello", null, null));

    autoConfig.mcpAuditResourceCustomizer(PROVIDER, properties, MAPPER).customize(config);

    assertThat(config.interceptors).hasSize(1);
    assertThat(config.interceptors.getFirst()).isInstanceOf(AuditLoggingInterceptor.class);
  }

  @Test
  void resource_template_customizer_attaches_interceptor() {
    var config =
        new StubResourceTemplateConfig(new ResourceTemplate("mem://item/{id}", "item", null, null));

    autoConfig.mcpAuditResourceTemplateCustomizer(PROVIDER, properties, MAPPER).customize(config);

    assertThat(config.interceptors).hasSize(1);
    assertThat(config.interceptors.getFirst()).isInstanceOf(AuditLoggingInterceptor.class);
  }

  @Test
  void tool_customizer_logs_attachment() {
    var config = new StubToolConfig(new Tool("get-weather", null, null, null, null));

    try (var captor = LogCaptor.forClass(MocapiAuditAutoConfiguration.class)) {
      autoConfig.mcpAuditToolCustomizer(PROVIDER, properties, MAPPER).customize(config);
      assertThat(captor.formattedMessages())
          .containsExactly("Attached AuditLoggingInterceptor interceptor to tool \"get-weather\"");
    }
  }

  @Test
  void default_provider_returns_anonymous_when_security_context_is_empty() {
    MocapiAuditAutoConfiguration.SpringSecurityCallerIdentityConfiguration cfg =
        new MocapiAuditAutoConfiguration.SpringSecurityCallerIdentityConfiguration();

    assertThat(cfg.auditCallerIdentityProvider().currentCaller())
        .isEqualTo(AuditCallerIdentityProvider.ANONYMOUS);
  }
}
