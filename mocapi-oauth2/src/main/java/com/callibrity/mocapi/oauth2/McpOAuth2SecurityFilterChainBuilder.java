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

import com.callibrity.mocapi.model.Implementation;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Builds the single {@link SecurityFilterChain} mocapi owns for the MCP endpoint plus the RFC 9728
 * well-known metadata path. Composed on top of Spring Security's {@link
 * OAuth2ProtectedResourceMetadataFilter} and {@link
 * org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer}.
 *
 * <p>Instantiable and testable without a live {@link HttpSecurity} — the builder captures static
 * configuration at construction and receives the {@link HttpSecurity} at {@link
 * #build(HttpSecurity)} time, matching Spring's own {@link
 * org.springframework.security.config.annotation.web.HttpSecurityBuilder} pattern.
 */
public final class McpOAuth2SecurityFilterChainBuilder {

  private final MocapiOAuth2Properties properties;
  private final JwtDecoder jwtDecoder;
  private final OpaqueTokenIntrospector opaqueTokenIntrospector;
  private final List<String> audiences;
  private final String springIssuerUri;
  private final Implementation mcpServerInfo;
  private final List<OAuth2ProtectedResourceMetadataCustomizer> metadataCustomizers;
  private final List<MocapiOAuth2SecurityFilterChainCustomizer> chainCustomizers;
  private final String mcpEndpoint;
  private final String metadataPath;

  // The 10 parameters aren't a design smell so much as the Spring Security OAuth2 resource-
  // server API surface refracted through a single builder: token decoders (JWT + opaque),
  // audience validation inputs, metadata-document inputs, and filter-chain customization hooks.
  // Splitting into parameter objects would hide the dependency graph the autoconfig already
  // assembles directly. Keep the explicit list; let autoconfig be the one place that wires it.
  @SuppressWarnings("java:S107")
  public McpOAuth2SecurityFilterChainBuilder(
      MocapiOAuth2Properties properties,
      JwtDecoder jwtDecoder,
      OpaqueTokenIntrospector opaqueTokenIntrospector,
      List<String> audiences,
      String springIssuerUri,
      Implementation mcpServerInfo,
      List<OAuth2ProtectedResourceMetadataCustomizer> metadataCustomizers,
      List<MocapiOAuth2SecurityFilterChainCustomizer> chainCustomizers,
      String mcpEndpoint,
      String metadataPath) {
    this.properties = properties;
    this.jwtDecoder = jwtDecoder;
    this.opaqueTokenIntrospector = opaqueTokenIntrospector;
    this.audiences = audiences;
    this.springIssuerUri = springIssuerUri;
    this.mcpServerInfo = mcpServerInfo;
    this.metadataCustomizers = metadataCustomizers;
    this.chainCustomizers = chainCustomizers;
    this.mcpEndpoint = mcpEndpoint;
    this.metadataPath = metadataPath;
  }

  // Sonar S4502: CSRF protection is disabled here intentionally. The MCP endpoint is a
  // stateless, bearer-token-authenticated JSON-RPC API — RFC 6750 tokens are only attached by
  // JS that explicitly sets the Authorization header, so there is no ambient-credential attack
  // vector for CSRF to defend against. Spring Security's own guidance for stateless OAuth2
  // resource servers is to disable CSRF; enabling it here would require MCP clients to fetch
  // and echo a CSRF token, which the MCP protocol has no mechanism for.
  @SuppressWarnings("java:S4502")
  public SecurityFilterChain build(HttpSecurity http) throws Exception {
    Consumer<OAuth2ProtectedResourceMetadata.Builder> builderCustomizer =
        McpMetadataBuilderCustomizerFactory.create(
            properties, audiences, springIssuerUri, mcpServerInfo, metadataCustomizers);

    http.securityMatcher(mcpEndpoint + "/**", metadataPath)
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(metadataPath).permitAll().anyRequest().authenticated())
        .csrf(csrf -> csrf.disable())
        .oauth2ResourceServer(
            rs -> {
              McpOAuth2TokenModeConfigurer.apply(
                  rs, jwtDecoder, opaqueTokenIntrospector, audiences);
              rs.protectedResourceMetadata(
                  prm -> prm.protectedResourceMetadataCustomizer(builderCustomizer));
            });

    for (MocapiOAuth2SecurityFilterChainCustomizer customizer : chainCustomizers) {
      customizer.customize(http);
    }
    return http.build();
  }
}
