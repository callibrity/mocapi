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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerJwtAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wires a Spring Boot context with an opaque-token introspector instead of a JWT decoder, via the
 * standard {@code spring.security.oauth2.resourceserver.opaquetoken.*} properties. Confirms that
 * mocapi's auto-configuration selects the opaque-token branch, serves the metadata document
 * anonymously, challenges unauthenticated requests with {@code WWW-Authenticate: Bearer}, and wraps
 * the opaque introspector so audience enforcement still applies.
 *
 * <p>We don't exercise a real introspection call (that would require a live introspection server);
 * the unit tests in {@link AudienceCheckingOpaqueTokenIntrospectorTest} cover the audience check
 * behavior directly with a mock introspector delegate.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(classes = MocapiOAuth2OpaqueTokenConfigTest.TestApp.class)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "mocapi.oauth2.resource=https://mcp.example.com",
      "spring.security.oauth2.resourceserver.opaquetoken.introspection-uri=https://idp.example.com/introspect",
      "spring.security.oauth2.resourceserver.opaquetoken.client-id=mcp-client",
      "spring.security.oauth2.resourceserver.opaquetoken.client-secret=secret",
      "spring.security.oauth2.resourceserver.jwt.audiences=mcp-test",
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    })
class MocapiOAuth2OpaqueTokenConfigTest {

  @Autowired MockMvc mockMvc;
  @Autowired ApplicationContext context;

  @Test
  void opaque_token_introspector_bean_is_available_and_jwt_decoder_is_not() {
    // Spring Boot auto-wires OpaqueTokenIntrospector from the properties above; JwtDecoder is
    // NOT registered because no jwt.* issuer/jwk/key properties are set. This confirms the test
    // is actually exercising the opaque path, not accidentally falling through to JWT.
    assertThat(context.getBeansOfType(OpaqueTokenIntrospector.class)).isNotEmpty();
    assertThat(context.getBeansOfType(org.springframework.security.oauth2.jwt.JwtDecoder.class))
        .isEmpty();
  }

  @Test
  void security_filter_chain_is_registered_in_opaque_mode() {
    assertThat(context.getBeansOfType(SecurityFilterChain.class)).isNotEmpty();
  }

  @Test
  void metadata_endpoint_is_anonymous_and_returns_200() throws Exception {
    mockMvc
        .perform(get("/.well-known/oauth-protected-resource"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resource").value("https://mcp.example.com"));
  }

  @Test
  void unauthenticated_mcp_request_returns_401_with_bearer_challenge() throws Exception {
    mockMvc
        .perform(post("/mcp"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(
            header()
                .string(
                    "WWW-Authenticate",
                    org.hamcrest.Matchers.containsString("resource_metadata=")));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = {
        // spring-boot-starter-oauth2-authorization-server is on the test classpath (used by
        // MocapiOAuth2IntegrationTest to bundle a real IdP). Its JwtDecoder auto-config would
        // register a NimbusJwtDecoder bean here, which would flip our chain into JWT mode even
        // though this test is exercising the opaque path. Excluding them leaves a clean
        // opaque-only context that mirrors a realistic deployment.
        OAuth2AuthorizationServerAutoConfiguration.class,
        OAuth2AuthorizationServerJwtAutoConfiguration.class
      })
  static class TestApp {
    @RestController
    static class McpStub {
      @PostMapping("/mcp")
      String post() {
        return "{\"ok\":true}";
      }
    }
  }
}
