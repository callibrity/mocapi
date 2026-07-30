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
package com.callibrity.mocapi.oauth2;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.oauth2.token.JwtMcpTokenStrategy;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Behavioral coverage of {@link McpFilterChains#createMcpFilterChain} resource-level scope
 * enforcement, in the module that owns the class.
 *
 * <p>Each nested class builds the chain by calling {@code createMcpFilterChain} directly with a
 * hand-built {@link McpFilterChainConfig} — no mocapi auto-configuration involved. That keeps the
 * unit under test the filter-chain factory itself, and means JaCoCo attributes the coverage to
 * {@code mocapi-oauth2} where {@code McpFilterChains} lives. The equivalent end-to-end path through
 * property binding is covered separately by {@code McpRequiredScopesTest} in {@code
 * mocapi-autoconfigure}; coverage does not cross module boundaries in this build, so both exist
 * deliberately.
 *
 * <p>Requests carry a real {@code Authorization: Bearer} header rather than the {@code jwt()}
 * post-processor: Spring scopes {@code BearerTokenAccessDeniedHandler} to a {@code
 * BearerTokenRequestMatcher}, so without the header a denial falls through to the plain {@code
 * AccessDeniedHandlerImpl} and returns a bare 403 — passing a status assertion while testing none
 * of the challenge.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpFilterChainsScopeTest {

  @Nested
  @SpringBootTest(classes = {TestApp.class, OneRequiredScopeConfig.class})
  @AutoConfigureMockMvc
  class One_required_scope {

    @Autowired MockMvc mockMvc;

    @Test
    void token_with_the_required_scope_is_allowed() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("mcp.use")))
          .andExpect(status().isOk());
    }

    @Test
    void token_without_the_required_scope_gets_403_insufficient_scope() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("unrelated.scope")))
          .andExpect(status().isForbidden())
          .andExpect(
              header()
                  .string(
                      HttpHeaders.WWW_AUTHENTICATE,
                      Matchers.containsString("error=\"insufficient_scope\"")));
    }

    @Test
    void anonymous_request_is_401_not_403() throws Exception {
      mockMvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @SpringBootTest(classes = {TestApp.class, TwoRequiredScopesConfig.class})
  @AutoConfigureMockMvc
  class Multiple_required_scopes {

    @Autowired MockMvc mockMvc;

    @Test
    void all_required_scopes_present_is_allowed() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("mcp.read", "mcp.write")))
          .andExpect(status().isOk());
    }

    @Test
    void one_of_two_required_scopes_is_denied_AND_not_OR() throws Exception {
      // hasAllAuthorities semantics, matching @RequiresScope at the handler layer. A regression to
      // OR here would silently grant write access to a read-only token.
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("mcp.read")))
          .andExpect(status().isForbidden());
    }

    @Test
    void neither_required_scope_is_denied() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("something.else")))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @SpringBootTest(classes = {TestApp.class, NoRequiredScopesConfig.class})
  @AutoConfigureMockMvc
  class No_required_scopes {

    @Autowired MockMvc mockMvc;

    @Test
    void any_authenticated_token_is_allowed() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("irrelevant")))
          .andExpect(status().isOk());
    }

    @Test
    void anonymous_request_is_still_rejected() throws Exception {
      // An empty scope list must mean "authentication only", never "permit everyone".
      mockMvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @SpringBootTest(classes = {TestApp.class, NullRequiredScopesConfig.class})
  @AutoConfigureMockMvc
  class Null_required_scopes {

    @Autowired MockMvc mockMvc;

    @Test
    void null_list_is_treated_as_no_required_scopes() throws Exception {
      // Defensive: the property is @DefaultValue-bound so Spring supplies an empty list, but the
      // record is public and can be constructed directly with null by anyone replacing the bean.
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("irrelevant")))
          .andExpect(status().isOk());
    }

    @Test
    void anonymous_request_is_still_rejected_with_a_null_list() throws Exception {
      mockMvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }
  }

  private static String bearer(String... scopes) {
    return "Bearer " + String.join("+", scopes);
  }

  private static SecurityFilterChain chainRequiring(HttpSecurity http, List<String> requiredScopes)
      throws Exception {
    return McpFilterChains.createMcpFilterChain(
        http,
        new McpFilterChainConfig(new JwtMcpTokenStrategy(), "/mcp", List.of(), requiredScopes));
  }

  static class OneRequiredScopeConfig {
    @Bean
    SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
      return chainRequiring(http, List.of("mcp.use"));
    }
  }

  static class TwoRequiredScopesConfig {
    @Bean
    SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
      return chainRequiring(http, List.of("mcp.read", "mcp.write"));
    }
  }

  static class NoRequiredScopesConfig {
    @Bean
    SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
      return chainRequiring(http, List.of());
    }
  }

  static class NullRequiredScopesConfig {
    @Bean
    SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
      return chainRequiring(http, null);
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApp {

    /**
     * Treats the bearer token value as a {@code +}-joined scope list and returns a {@code Jwt}
     * carrying them in the {@code scope} claim, which Spring's {@code
     * JwtGrantedAuthoritiesConverter} turns into {@code SCOPE_*} authorities. Only signature and
     * issuer validation are stubbed; the real {@code BearerTokenAuthenticationFilter} and
     * authorization path still run.
     */
    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .claim("sub", "test-user")
              .claim("scope", token.replace('+', ' '))
              .build();
    }

    @RestController
    static class McpStub {
      @PostMapping("/mcp")
      String post() {
        return "{\"ok\":true}";
      }
    }
  }
}
