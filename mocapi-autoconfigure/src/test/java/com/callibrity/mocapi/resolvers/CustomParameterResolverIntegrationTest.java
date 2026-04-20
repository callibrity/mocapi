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
package com.callibrity.mocapi.resolvers;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import jakarta.annotation.Nullable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * End-to-end verification that a user-supplied {@link ParameterResolver} registered via a {@link
 * CallToolHandlerCustomizer} is layered into the tool's resolver chain by mocapi-autoconfigure and
 * resolves a bespoke parameter annotation at call time.
 */
@SpringBootTest(
    classes = CustomParameterResolverIntegrationTest.TestApp.class,
    webEnvironment = WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CustomParameterResolverIntegrationTest {

  static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

  @Autowired JsonRpcDispatcher dispatcher;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void setTenant() {
    CURRENT_TENANT.set("acme");
  }

  @AfterEach
  void clearTenant() {
    CURRENT_TENANT.remove();
  }

  @Test
  void custom_resolver_supplies_bound_parameter_at_call_time() {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", "tenant-echo");
    params.putObject("arguments");
    var call =
        new JsonRpcCall(JsonRpcProtocol.VERSION, McpMethods.TOOLS_CALL, params, IntNode.valueOf(1));

    var response = dispatcher.dispatch(call);

    assertThat(response).isInstanceOf(JsonRpcResult.class);
    var result = ((JsonRpcResult) response).result();
    assertThat(result.path("structuredContent").path("tenant").asString()).isEqualTo("acme");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface CurrentTenant {}

  record TenantEcho(String tenant) {}

  static class TenantEchoTool {
    @McpTool(name = "tenant-echo")
    public TenantEcho echo(@CurrentTenant @Nullable String tenant) {
      return new TenantEcho(tenant);
    }
  }

  static final class CurrentTenantResolver implements ParameterResolver<JsonNode> {
    @Override
    public Optional<ParameterResolver.Binding<JsonNode>> bind(ParameterInfo info) {
      if (!info.parameter().isAnnotationPresent(CurrentTenant.class)
          || info.resolvedType() != String.class) {
        return Optional.empty();
      }
      return Optional.of(arguments -> CURRENT_TENANT.get());
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = com.callibrity.mocapi.oauth2.MocapiOAuth2AutoConfiguration.class)
  static class TestApp {
    @Bean
    TenantEchoTool tenantEchoTool() {
      return new TenantEchoTool();
    }

    @Bean
    CallToolHandlerCustomizer currentTenantResolverCustomizer() {
      CurrentTenantResolver resolver = new CurrentTenantResolver();
      return config -> config.resolver(resolver);
    }
  }
}
