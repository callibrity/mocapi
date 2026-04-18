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

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unit-level coverage of {@link MocapiOAuth2AutoConfiguration}. Exercises the wiring without
 * standing up a real IdP or issuing real tokens — the stub {@link JwtDecoder} never gets called on
 * these paths because every assertion concerns the anonymous metadata endpoint or the {@code
 * WWW-Authenticate} challenge on an unauthenticated request.
 *
 * <p>End-to-end flows that require a real authorization server (token grant, signature validation,
 * audience enforcement) live in {@code MocapiOAuth2IntegrationTest}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@ActiveProfiles("test")
class MocapiOAuth2AutoConfigurationTest {

  @Nested
  @SpringBootTest(classes = MocapiOAuth2AutoConfigurationTest.TestApp.class)
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        "mocapi.oauth2.resource=https://mcp.example.com",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.com",
        "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      })
  class With_implicit_authorization_server {

    @Autowired MockMvc mockMvc;

    @Test
    void metadata_endpoint_is_anonymous_and_returns_200() throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void metadata_document_advertises_configured_resource() throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.resource").value("https://mcp.example.com"));
    }

    @Test
    void metadata_document_falls_back_to_spring_issuer_uri_when_explicit_list_is_empty()
        throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.authorization_servers[0]").value("https://idp.example.com"));
    }

    @Test
    void unauthenticated_mcp_request_returns_401_with_bearer_challenge() throws Exception {
      mockMvc
          .perform(post("/mcp"))
          .andExpect(status().isUnauthorized())
          .andExpect(header().exists("WWW-Authenticate"))
          .andExpect(
              header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")));
    }

    @Test
    void unauthenticated_mcp_request_includes_resource_metadata_parameter() throws Exception {
      mockMvc
          .perform(post("/mcp"))
          .andExpect(
              header()
                  .string(
                      "WWW-Authenticate",
                      org.hamcrest.Matchers.containsString("resource_metadata=")));
    }
  }

  @Nested
  @SpringBootTest(classes = MocapiOAuth2AutoConfigurationTest.TestApp.class)
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        "mocapi.oauth2.resource=https://mcp.example.com",
        "mocapi.oauth2.authorization-servers[0]=https://idp-a.example.com",
        "mocapi.oauth2.authorization-servers[1]=https://idp-b.example.com",
        "mocapi.oauth2.scopes[0]=mcp.read",
        "mocapi.oauth2.scopes[1]=mcp.write",
        "mocapi.server-title=Test MCP",
        "mocapi.oauth2.resource-documentation=https://docs.example.com/mcp",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://ignored.example.com",
        "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      })
  class With_explicit_metadata_configuration {

    @Autowired MockMvc mockMvc;

    @Test
    void explicit_authorization_servers_override_spring_issuer_fallback() throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.authorization_servers[0]").value("https://idp-a.example.com"))
          .andExpect(jsonPath("$.authorization_servers[1]").value("https://idp-b.example.com"));
    }

    @Test
    void scopes_are_advertised_in_metadata() throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.scopes_supported[0]").value("mcp.read"))
          .andExpect(jsonPath("$.scopes_supported[1]").value("mcp.write"));
    }

    @Test
    void resource_name_comes_from_mcp_server_title() throws Exception {
      // mocapi.oauth2 has no resource-name property; resource_name is derived from the MCP
      // Implementation bean (mocapi.server-title in the @TestPropertySource above) so the OAuth2
      // metadata stays aligned with what clients see in the MCP initialize response.
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.resource_name").value("Test MCP"));
    }

    @Test
    void resource_documentation_is_advertised_in_metadata() throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.resource_documentation").value("https://docs.example.com/mcp"));
    }
  }

  @Nested
  @SpringBootTest(
      classes = {
        MocapiOAuth2AutoConfigurationTest.TestApp.class,
        MocapiOAuth2AutoConfigurationTest.CustomizerConfig.class
      })
  @AutoConfigureMockMvc
  @TestPropertySource(
      properties = {
        "mocapi.oauth2.resource=https://mcp.example.com",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.com",
        "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      })
  class Customizers {

    @Autowired MockMvc mockMvc;

    @Test
    void metadata_customizer_bean_is_applied_after_mocapi_defaults() throws Exception {
      mockMvc
          .perform(get("/.well-known/oauth-protected-resource"))
          .andExpect(jsonPath("$.custom_claim").value("custom_value"));
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApp {

    /**
     * Mock decoder that satisfies the {@code @ConditionalOnBean(JwtDecoder.class)} gate on the
     * oauth2 auto-configuration without contacting a real IdP. No test in this class exercises a
     * path that actually invokes the decoder; wrong-token paths live in the integration test.
     */
    @Bean
    JwtDecoder jwtDecoder() {
      return mock(JwtDecoder.class);
    }

    @RestController
    static class McpStub {
      @PostMapping("/mcp")
      String post() {
        return "{\"ok\":true}";
      }
    }
  }

  static class CustomizerConfig {
    @Bean
    OAuth2ProtectedResourceMetadataCustomizer customClaimCustomizer() {
      return builder -> builder.claim("custom_claim", "custom_value");
    }
  }
}
