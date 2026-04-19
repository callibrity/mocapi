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
package com.callibrity.mocapi.security.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.oauth2.MocapiOAuth2AutoConfiguration;
import com.callibrity.mocapi.security.spring.RequiresScope;
import com.callibrity.mocapi.server.JsonRpcErrorCodes;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcError;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.JsonRpcResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Wires the full autoconfig — the Spring-Security-backed guard customizers plus the rest of mocapi
 * — and drives requests through {@link JsonRpcDispatcher} with {@link SecurityContextHolder}
 * populated or empty, to verify list-time filtering and call-time {@code -32003 Forbidden}
 * end-to-end.
 */
@SpringBootTest(
    classes = SpringSecurityGuardsIntegrationTest.TestApp.class,
    webEnvironment = WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SpringSecurityGuardsIntegrationTest {

  @Autowired JsonRpcDispatcher dispatcher;
  @Autowired ObjectMapper objectMapper;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void guarded_tool_hidden_when_no_authentication() {
    var response = dispatcher.dispatch(listToolsCall());
    assertThat(response).isInstanceOf(JsonRpcResult.class);
    var names = ((JsonRpcResult) response).result().path("tools").toString();
    assertThat(names).doesNotContain("\"admin-only\"");
    assertThat(names).contains("\"public\"");
  }

  @Test
  void guarded_tool_visible_when_authentication_has_required_scope() {
    authenticateWith("SCOPE_admin:write");
    var response = dispatcher.dispatch(listToolsCall());
    assertThat(response).isInstanceOf(JsonRpcResult.class);
    var names = ((JsonRpcResult) response).result().path("tools").toString();
    assertThat(names).contains("\"admin-only\"");
  }

  @Test
  void calling_guarded_tool_without_authentication_returns_forbidden() {
    var response = dispatcher.dispatch(callAdminOnly());
    assertThat(response).isInstanceOf(JsonRpcError.class);
    var error = (JsonRpcError) response;
    assertThat(error.error().code()).isEqualTo(JsonRpcErrorCodes.FORBIDDEN);
    assertThat(error.error().message()).isEqualTo("Forbidden: unauthenticated");
  }

  @Test
  void calling_guarded_tool_with_wrong_scope_returns_forbidden_with_missing_scope_reason() {
    authenticateWith("SCOPE_read");
    var response = dispatcher.dispatch(callAdminOnly());
    assertThat(response).isInstanceOf(JsonRpcError.class);
    var error = (JsonRpcError) response;
    assertThat(error.error().code()).isEqualTo(JsonRpcErrorCodes.FORBIDDEN);
    assertThat(error.error().message()).isEqualTo("Forbidden: missing scope(s): admin:write");
  }

  @Test
  void calling_guarded_tool_with_required_scope_succeeds() {
    authenticateWith("SCOPE_admin:write");
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

  private static void authenticateWith(String... authorities) {
    List<SimpleGrantedAuthority> granted =
        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.setContext(
        new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated("user", "n/a", granted)));
  }

  static class AdminTools {
    @McpTool(name = "admin-only", description = "Requires admin:write scope")
    @RequiresScope("admin:write")
    public String wipeEverything() {
      return "done";
    }

    @McpTool(name = "public", description = "Open to everyone")
    public String hello() {
      return "hi";
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = MocapiOAuth2AutoConfiguration.class)
  static class TestApp {
    @Bean
    AdminTools adminTools() {
      return new AdminTools();
    }
  }
}
