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
package com.callibrity.mocapi.guards;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.tools.CallToolHandlerCustomizer;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * End-to-end verification of the Guard SPI wired through mocapi-autoconfigure. A single {@link
 * CallToolHandlerCustomizer} attaches a {@link RequiresScope} annotation-driven guard to every
 * tool; the guard reads a per-test {@link ThreadLocal} to simulate a caller's auth context.
 *
 * <p>Covers: list hides denied tools, call returns the forbidden JSON-RPC code, and an allowed
 * scope makes the same tool visible and callable.
 */
@SpringBootTest(classes = GuardIntegrationTest.TestApp.class, webEnvironment = WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GuardIntegrationTest {

  static final ThreadLocal<String> CURRENT_SCOPE = new ThreadLocal<>();

  @Autowired JsonRpcDispatcher dispatcher;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void clearScope() {
    CURRENT_SCOPE.remove();
  }

  @AfterEach
  void cleanup() {
    CURRENT_SCOPE.remove();
  }

  @Test
  void unauthorized_caller_does_not_see_guarded_tool_in_list() {
    CURRENT_SCOPE.set("read");
    var response = dispatcher.dispatch(listToolsCall());
    assertThat(response).isInstanceOf(JsonRpcResult.class);
    var names = ((JsonRpcResult) response).result().path("tools").toString();
    assertThat(names).doesNotContain("\"admin-only\"");
  }

  @Test
  void authorized_caller_sees_guarded_tool_in_list() {
    CURRENT_SCOPE.set("admin");
    var response = dispatcher.dispatch(listToolsCall());
    assertThat(response).isInstanceOf(JsonRpcResult.class);
    var names = ((JsonRpcResult) response).result().path("tools").toString();
    assertThat(names).contains("\"admin-only\"");
  }

  @Test
  void unauthorized_call_returns_forbidden() {
    CURRENT_SCOPE.set("read");
    var response = dispatcher.dispatch(callAdminOnly());
    assertThat(response).isInstanceOf(JsonRpcError.class);
    var error = (JsonRpcError) response;
    assertThat(error.error().code()).isEqualTo(JsonRpcErrorCodes.FORBIDDEN);
    assertThat(error.error().message()).startsWith("Forbidden").contains("admin");
  }

  @Test
  void authorized_call_reaches_the_tool() {
    CURRENT_SCOPE.set("admin");
    var response = dispatcher.dispatch(callAdminOnly());
    assertThat(response).isInstanceOf(JsonRpcResult.class);
  }

  private JsonRpcCall listToolsCall() {
    ObjectNode params = objectMapper.createObjectNode();
    return new JsonRpcCall(
        JsonRpcProtocol.VERSION, McpMethods.TOOLS_LIST, params, IntNode.valueOf(1));
  }

  private JsonRpcCall callAdminOnly() {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", "admin-only");
    params.putObject("arguments");
    return new JsonRpcCall(
        JsonRpcProtocol.VERSION, McpMethods.TOOLS_CALL, params, IntNode.valueOf(2));
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  @interface RequiresScope {
    String value();
  }

  static class AdminTools {
    @McpTool(name = "admin-only", description = "Requires admin scope")
    @RequiresScope("admin")
    public String wipeEverything() {
      return "done";
    }

    @McpTool(name = "public", description = "Open to everyone")
    public String hello() {
      return "hi";
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = com.callibrity.mocapi.oauth2.MocapiOAuth2AutoConfiguration.class)
  static class TestApp {
    @Bean
    AdminTools adminTools() {
      return new AdminTools();
    }

    @Bean
    CallToolHandlerCustomizer scopeGuardCustomizer() {
      return config -> {
        RequiresScope annotation = config.method().getAnnotation(RequiresScope.class);
        if (annotation != null) {
          String required = annotation.value();
          config.guard(
              () -> {
                String current = CURRENT_SCOPE.get();
                if (required.equals(current)) {
                  return new GuardDecision.Allow();
                }
                return new GuardDecision.Deny("requires scope " + required);
              });
        }
      };
    }
  }
}
