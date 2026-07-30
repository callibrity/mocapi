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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coverage for {@code mocapi.oauth2.required-scopes} — resource-level (coarse) scope enforcement on
 * the MCP filter chain, and the {@code 403 insufficient_scope} challenge Spring Security emits for
 * it (RFC 6750 §3.1). See ADR-0029.
 *
 * <p>These tests drive a real {@code Authorization: Bearer} header rather than the {@code jwt()}
 * request post-processor on purpose. Spring registers {@code BearerTokenAccessDeniedHandler} via
 * {@code defaultAccessDeniedHandlerFor(handler, BearerTokenRequestMatcher)}, so the spec-shaped
 * challenge is only produced for requests that actually carry a bearer token. A post-processor that
 * injects the {@code SecurityContext} without the header would fall through to the plain {@code
 * AccessDeniedHandlerImpl} and yield a bare 403 — the assertions would pass on the status code
 * while silently not testing the challenge at all.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ActiveProfiles("test")
class McpRequiredScopesTest {

  private static final String RESOURCE_PROPS_RESOURCE =
      "mocapi.oauth2.resource=https://mcp.example.com";
  private static final String RESOURCE_PROPS_ISSUER =
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.com";
  private static final String RESOURCE_PROPS_AUDIENCE =
      "spring.security.oauth2.resourceserver.jwt.audiences=https://mcp.example.com";

  @Nested
  @SpringBootTest(classes = McpRequiredScopesTest.TestApp.class)
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        RESOURCE_PROPS_RESOURCE,
        RESOURCE_PROPS_ISSUER,
        RESOURCE_PROPS_AUDIENCE,
        "mocapi.oauth2.scopes[0]=mcp.use",
        "mocapi.oauth2.required-scopes[0]=mcp.use"
      })
  class With_one_required_scope {

    @Autowired MockMvc mockMvc;

    @Test
    void token_carrying_the_required_scope_reaches_the_endpoint() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("mcp.use")))
          .andExpect(status().isOk());
    }

    @Test
    void token_missing_the_required_scope_is_rejected_with_403() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("some.other.scope")))
          .andExpect(status().isForbidden());
    }

    @Test
    void rejection_carries_the_insufficient_scope_bearer_challenge() throws Exception {
      // RFC 6750 §3.1 — this is the step-up breadcrumb the MCP authorization spec expects, and the
      // whole reason resource-level scopes are worth wiring at the filter layer.
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("some.other.scope")))
          .andExpect(
              header()
                  .string(
                      HttpHeaders.WWW_AUTHENTICATE,
                      Matchers.containsString("error=\"insufficient_scope\"")));
    }

    @Test
    void unauthenticated_request_is_still_401_not_403() throws Exception {
      // Missing credentials is an authentication failure; it must not be reported as a scope
      // problem, or a client would try to step up when it has no token at all.
      mockMvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @SpringBootTest(classes = McpRequiredScopesTest.TestApp.class)
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        RESOURCE_PROPS_RESOURCE,
        RESOURCE_PROPS_ISSUER,
        RESOURCE_PROPS_AUDIENCE,
        "mocapi.oauth2.scopes[0]=mcp.read",
        "mocapi.oauth2.scopes[1]=mcp.write",
        "mocapi.oauth2.required-scopes[0]=mcp.read",
        "mocapi.oauth2.required-scopes[1]=mcp.write"
      })
  class With_multiple_required_scopes {

    @Autowired MockMvc mockMvc;

    @Test
    void all_required_scopes_present_reaches_the_endpoint() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("mcp.read", "mcp.write")))
          .andExpect(status().isOk());
    }

    @Test
    void semantics_are_AND_not_OR_so_a_single_scope_is_insufficient() throws Exception {
      // Matches @RequiresScope's AND semantics at the handler layer. If this ever regresses to OR,
      // a token holding only the weaker scope would silently gain write access.
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("mcp.read")))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @SpringBootTest(classes = McpRequiredScopesTest.TestApp.class)
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {RESOURCE_PROPS_RESOURCE, RESOURCE_PROPS_ISSUER, RESOURCE_PROPS_AUDIENCE})
  class With_no_required_scopes_configured {

    @Autowired MockMvc mockMvc;

    @Test
    void any_authenticated_token_reaches_the_endpoint() throws Exception {
      // The property is optional: absent means authentication-only, exactly as it behaved before
      // required-scopes existed. This is the backward-compatibility guard.
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("nothing.relevant")))
          .andExpect(status().isOk());
    }

    @Test
    void unauthenticated_request_is_still_rejected() throws Exception {
      // An empty scope list must mean "authentication only", never "permit everyone". The AND is
      // Spring's hasAllAuthorities, which asserts a non-empty list, so empty is branched to
      // authenticated() — the shape deliberately avoids AuthorizationManagers.allOf, which grants
      // when handed zero managers and would have dropped authentication for an empty list.
      mockMvc.perform(post("/mcp")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @SpringBootTest(classes = McpRequiredScopesTest.TestApp.class)
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        RESOURCE_PROPS_RESOURCE,
        RESOURCE_PROPS_ISSUER,
        RESOURCE_PROPS_AUDIENCE,
        // Deliberately NOT listed in mocapi.oauth2.scopes — exercises the startup warning for a
        // scope that is enforced but undiscoverable through the RFC 9728 metadata document.
        "mocapi.oauth2.required-scopes[0]=admin.unadvertised"
      })
  class With_a_required_scope_that_is_not_advertised {

    @Autowired MockMvc mockMvc;

    @Test
    void context_still_starts_and_the_scope_is_enforced() throws Exception {
      // The mismatch is a warning, not a failure: the advertised set may legitimately come from a
      // replacement ScopesSupportedMetadataCustomizer, so mocapi can't prove it's undiscoverable.
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("admin.unadvertised")))
          .andExpect(status().isOk());
    }

    @Test
    void token_without_the_unadvertised_scope_is_still_denied() throws Exception {
      mockMvc
          .perform(post("/mcp").header(HttpHeaders.AUTHORIZATION, bearer("some.other.scope")))
          .andExpect(status().isForbidden());
    }
  }

  /**
   * Builds an {@code Authorization} header whose token value encodes the scopes the stub decoder
   * should grant. {@code DefaultBearerTokenResolver} rejects spaces in the token, so scopes are
   * joined with {@code +} here and split back apart in {@link TestApp#jwtDecoder()}.
   */
  private static String bearer(String... scopes) {
    return "Bearer " + String.join("+", scopes);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApp {

    /**
     * Stub decoder that trusts the token value: it treats the token as a {@code +}-joined scope
     * list and returns a {@code Jwt} carrying them in the {@code scope} claim, which Spring's
     * {@code JwtGrantedAuthoritiesConverter} turns into {@code SCOPE_*} authorities. This keeps the
     * real {@code BearerTokenAuthenticationFilter} and the real authorization path in play — only
     * signature and issuer validation are stubbed out, and neither is what these tests are about.
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
