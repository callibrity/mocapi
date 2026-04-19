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
import static org.springframework.security.config.Customizer.withDefaults;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * End-to-end integration test bundling Spring Authorization Server inside the same Spring context
 * as the mocapi OAuth2 module. Exercises the complete dance: metadata discovery, unauthenticated
 * challenge, {@code client_credentials} token grant, and bearer-token call against the MCP stub.
 *
 * <p>Uses a fixed port ({@value TEST_PORT}) rather than {@code RANDOM_PORT} so the Spring-managed
 * {@code jwt.issuer-uri} property can carry the auth server's URL literally in
 * {@code @TestPropertySource} — no chicken-and-egg with port allocation.
 *
 * <p>Two {@link SecurityFilterChain} beans coexist: one for the authorization server at
 * {@code @Order(1)} ({@code /oauth2/**} etc.), and the mocapi chain (HIGHEST_PRECEDENCE) for {@code
 * /mcp/**} plus the RFC 9728 metadata endpoint. They match disjoint path sets.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    classes = MocapiOAuth2IntegrationTest.TestApp.class)
@TestPropertySource(
    properties = {
      "server.port=" + MocapiOAuth2IntegrationTest.TEST_PORT,
      "mocapi.oauth2.resource=http://localhost:" + MocapiOAuth2IntegrationTest.TEST_PORT + "/mcp",
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:"
          + MocapiOAuth2IntegrationTest.TEST_PORT,
      "spring.security.oauth2.resourceserver.jwt.audiences=http://localhost:"
          + MocapiOAuth2IntegrationTest.TEST_PORT
          + "/mcp",
      "mocapi.session-encryption-master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
      "mocapi.server-title=Integration Test MCP"
    })
class MocapiOAuth2IntegrationTest {

  static final int TEST_PORT = 18999;

  private static final String BASE_URL = "http://localhost:" + TEST_PORT;

  private RestClient client() {
    return RestClient.builder().baseUrl(BASE_URL).build();
  }

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_OF_OBJECT =
      new ParameterizedTypeReference<>() {};

  @Test
  void metadata_endpoint_advertises_resource_and_authorization_server() {
    Map<String, Object> body =
        client().get().uri("/.well-known/oauth-protected-resource").retrieve().body(MAP_OF_OBJECT);

    assertThat(body)
        .isNotNull()
        .containsEntry("resource", BASE_URL + "/mcp")
        .extractingByKey(
            "authorization_servers", org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .containsExactly(BASE_URL);
  }

  @Test
  void unauthenticated_mcp_request_returns_401_with_resource_metadata_challenge() {
    try {
      client()
          .post()
          .uri("/mcp")
          .contentType(MediaType.APPLICATION_JSON)
          .body("{}")
          .retrieve()
          .toBodilessEntity();
      throw new AssertionError("expected 401");
    } catch (org.springframework.web.client.HttpClientErrorException e) {
      assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
      String wwwAuth = e.getResponseHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE);
      assertThat(wwwAuth).startsWith("Bearer").contains("resource_metadata=");
    }
  }

  @Test
  void valid_client_credentials_token_reaches_mcp_controller() {
    String accessToken = grantClientCredentialsToken();

    String body =
        client()
            .post()
            .uri("/mcp")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .retrieve()
            .body(String.class);

    assertThat(body).contains("\"ok\":true");
  }

  @Test
  void metadata_advertises_bearer_methods_as_header() {
    Map<String, Object> body =
        client().get().uri("/.well-known/oauth-protected-resource").retrieve().body(MAP_OF_OBJECT);

    assertThat(body)
        .extractingByKey(
            "bearer_methods_supported", org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .contains("header");
  }

  @Test
  void metadata_advertises_resource_name_from_mcp_server_title() {
    // mocapi.server-title is set in @TestPropertySource; mocapi pulls resource_name from the
    // Implementation bean so the OAuth2 metadata doesn't need its own duplicate property.
    Map<String, Object> body =
        client().get().uri("/.well-known/oauth-protected-resource").retrieve().body(MAP_OF_OBJECT);

    assertThat(body).containsEntry("resource_name", "Integration Test MCP");
  }

  private String grantClientCredentialsToken() {
    RestClient raw =
        RestClient.builder()
            .baseUrl(BASE_URL)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();

    Map<String, Object> response =
        raw.method(HttpMethod.POST)
            .uri("/oauth2/token")
            .header(HttpHeaders.AUTHORIZATION, basicAuth("mcp-test-client", "secret"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body("grant_type=client_credentials&scope=mcp.read")
            .retrieve()
            .body(MAP_OF_OBJECT);

    assertThat(response).isNotNull();
    String token = (String) response.get("access_token");
    assertThat(token).isNotBlank();
    return token;
  }

  private static String basicAuth(String user, String pass) {
    return "Basic "
        + java.util.Base64.getEncoder()
            .encodeToString((user + ":" + pass).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  // -------------------------- Test application --------------------------

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class TestApp {

    /**
     * Spring Authorization Server's canned security chain for {@code /oauth2/**}, {@code
     * /.well-known/openid-configuration}, the JWKS endpoint, and friends.
     */
    @Bean
    @Order(1)
    // Sonar S1130: HttpSecurity.build() actually declares `throws Exception` (see
    // AbstractConfiguredSecurityBuilder#build). Sonar's reachability analysis can't see
    // through the generic return type, so the throws is required but flagged as redundant.
    @SuppressWarnings("java:S1130")
    SecurityFilterChain authServerChain(HttpSecurity http) throws Exception {
      OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();
      http.securityMatcher(configurer.getEndpointsMatcher())
          .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
          .csrf(csrf -> csrf.ignoringRequestMatchers(configurer.getEndpointsMatcher()))
          .with(configurer, withDefaults());
      return http.formLogin(withDefaults()).build();
    }

    /** One client registered for the {@code client_credentials} grant. */
    @Bean
    RegisteredClientRepository registeredClientRepository() {
      RegisteredClient client =
          RegisteredClient.withId(UUID.randomUUID().toString())
              .clientId("mcp-test-client")
              .clientSecret("{noop}secret")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
              .scope("mcp.read")
              .tokenSettings(
                  TokenSettings.builder()
                      .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                      .build())
              .build();
      return new InMemoryRegisteredClientRepository(client);
    }

    /** RSA keypair for signing tokens; served by Spring Authorization Server's JWKS endpoint. */
    @Bean
    JWKSource<SecurityContext> jwkSource() throws Exception {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair keyPair = gen.generateKeyPair();
      RSAKey rsaKey =
          new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
              .privateKey((RSAPrivateKey) keyPair.getPrivate())
              .keyID(UUID.randomUUID().toString())
              .build();
      return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
      return AuthorizationServerSettings.builder().build();
    }

    /**
     * Stamps {@code aud=<resource>} onto every access token so the mocapi resource server's
     * audience validator (wired automatically when {@code
     * spring.security.oauth2.resourceserver.jwt.audiences} is set) accepts them. Must match both
     * the {@code audiences} property and {@code mocapi.oauth2.resource} — the new resolver enforces
     * that those two agree.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
      return ctx -> ctx.getClaims().audience(List.of("http://localhost:" + TEST_PORT + "/mcp"));
    }

    /**
     * Custom decoder backed by the locally-generated RSA public key. Bypasses Spring Boot's
     * auto-config issuer-uri discovery path, which would otherwise try to hit the same process
     * during bean initialization.
     */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) throws Exception {
      RSAKey rsaKey =
          (RSAKey)
              jwkSource
                  .get(
                      new com.nimbusds.jose.jwk.JWKSelector(
                          new com.nimbusds.jose.jwk.JWKMatcher.Builder()
                              .keyType(com.nimbusds.jose.jwk.KeyType.RSA)
                              .build()),
                      null)
                  .get(0);
      return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
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
