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

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.McpMethods;
import com.callibrity.mocapi.oauth2.MocapiOAuth2AutoConfiguration;
import com.callibrity.mocapi.security.spring.RequiresScope;
import com.callibrity.mocapi.support.LogCaptor;
import com.callibrity.ripcurl.core.JsonRpcCall;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
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
 * Drives tool invocations through the full autoconfig + JSON-RPC dispatcher and asserts that the
 * {@code mocapi.audit} logger sees one event per call with correctly classified outcome, caller
 * identity, and arguments-hash behavior.
 */
@SpringBootTest(classes = AuditIntegrationTest.TestApp.class, webEnvironment = WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "mocapi.audit.hash-arguments=true"
    })
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditIntegrationTest {

  @Autowired JsonRpcDispatcher dispatcher;
  @Autowired ObjectMapper objectMapper;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void success_invocation_emits_audit_event_with_success_outcome() {
    authenticateAs("alice");
    try (LogCaptor captor = LogCaptor.forName("mocapi.audit")) {
      var response = dispatcher.dispatch(callTool("greet", Map.of("name", "world")));
      assertThat(response).isNotNull();

      ILoggingEvent event = captor.events().getLast();
      Map<String, Object> kv = keyValues(event);
      assertThat(kv).containsEntry(AuditFieldKeys.CALLER, "alice");
      assertThat(kv).containsEntry(AuditFieldKeys.HANDLER_KIND, "tool");
      assertThat(kv).containsEntry(AuditFieldKeys.HANDLER_NAME, "greet");
      assertThat(kv).containsEntry(AuditFieldKeys.OUTCOME, "success");
      assertThat((Long) kv.get(AuditFieldKeys.DURATION_MS)).isGreaterThanOrEqualTo(0L);
      assertThat((String) kv.get(AuditFieldKeys.ARGUMENTS_HASH)).startsWith("sha256:");
    }
  }

  @Test
  void unauthenticated_caller_is_recorded_as_anonymous() {
    try (LogCaptor captor = LogCaptor.forName("mocapi.audit")) {
      dispatcher.dispatch(callTool("greet", Map.of("name", "world")));

      Map<String, Object> kv = keyValues(captor.events().getLast());
      assertThat(kv).containsEntry(AuditFieldKeys.CALLER, AuditCallerIdentityProvider.ANONYMOUS);
    }
  }

  @Test
  void throwing_tool_classifies_as_error_with_error_class_field() {
    try (LogCaptor captor = LogCaptor.forName("mocapi.audit")) {
      dispatcher.dispatch(callTool("boom", Map.of()));

      Map<String, Object> kv = keyValues(captor.events().getLast());
      assertThat(kv).containsEntry(AuditFieldKeys.OUTCOME, "error");
      assertThat(kv).containsEntry(AuditFieldKeys.ERROR_CLASS, "IllegalStateException");
    }
  }

  @Test
  void guard_denial_classifies_as_forbidden() {
    try (LogCaptor captor = LogCaptor.forName("mocapi.audit")) {
      dispatcher.dispatch(callTool("admin-only", Map.of()));

      Map<String, Object> kv = keyValues(captor.events().getLast());
      assertThat(kv).containsEntry(AuditFieldKeys.OUTCOME, "forbidden");
      assertThat(kv).containsEntry(AuditFieldKeys.ERROR_CLASS, "JsonRpcException");
    }
  }

  private JsonRpcCall callTool(String name, Map<String, ?> args) {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("name", name);
    ObjectNode arguments = params.putObject("arguments");
    args.forEach((k, v) -> arguments.putPOJO(k, v));
    return new JsonRpcCall(
        JsonRpcProtocol.VERSION, McpMethods.TOOLS_CALL, params, IntNode.valueOf(1));
  }

  private static void authenticateAs(String name, String... authorities) {
    List<SimpleGrantedAuthority> granted =
        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
    SecurityContextHolder.setContext(
        new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(name, "n/a", granted)));
  }

  private static Map<String, Object> keyValues(ILoggingEvent event) {
    Map<String, Object> out = new LinkedHashMap<>();
    List<KeyValuePair> pairs = event.getKeyValuePairs();
    if (pairs != null) {
      for (KeyValuePair pair : pairs) {
        out.put(pair.key, pair.value);
      }
    }
    return out;
  }

  static class AuditTools {
    @McpTool(name = "greet", description = "Greets someone")
    public String greet(String name) {
      return "hello " + name;
    }

    @McpTool(name = "boom", description = "Always throws")
    public String boom() {
      throw new IllegalStateException("boom");
    }

    @McpTool(name = "admin-only", description = "Requires admin scope")
    @RequiresScope("admin:write")
    public String adminOnly() {
      return "admin";
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(exclude = MocapiOAuth2AutoConfiguration.class)
  static class TestApp {
    @Bean
    AuditTools auditTools() {
      return new AuditTools();
    }
  }
}
