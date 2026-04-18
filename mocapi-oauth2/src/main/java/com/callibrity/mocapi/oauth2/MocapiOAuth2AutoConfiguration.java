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
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Auto-configuration that wires mocapi's MCP endpoint for OAuth2 bearer-token protection and serves
 * an RFC 9728 protected-resource metadata document at the {@code
 * /.well-known/oauth-protected-resource} path Spring Security's built-in filter owns.
 *
 * <p>What this auto-configuration does:
 *
 * <ul>
 *   <li>Registers a single {@link SecurityFilterChain} scoped to the MCP endpoint (shared {@code
 *       mocapi.endpoint} property, defaulting to {@code /mcp}) plus the well-known metadata
 *       endpoint. The metadata endpoint is {@code permitAll} so clients can discover it without a
 *       token; everything else under the matched paths requires a valid JWT.
 *   <li>Adds Spring's {@link OAuth2ProtectedResourceMetadataFilter} to that chain, configured with
 *       a customizer that populates the metadata builder from {@link MocapiOAuth2Properties} on
 *       every request. User-provided {@link OAuth2ProtectedResourceMetadataCustomizer} beans run
 *       after mocapi's defaults so they can override or extend the document.
 * </ul>
 *
 * <p>What this auto-configuration does <em>not</em> do (because Spring already does):
 *
 * <ul>
 *   <li>Decode/validate JWTs — Spring Boot's {@code OAuth2ResourceServerAutoConfiguration}
 *       registers a {@code JwtDecoder} from {@code spring.security.oauth2.resourceserver.jwt.*}.
 *   <li>Audience enforcement — same Spring Boot auto-config wires an {@code aud} validator when
 *       {@code spring.security.oauth2.resourceserver.jwt.audiences} is set.
 *   <li>Emit {@code WWW-Authenticate: Bearer ... resource_metadata="..."} on 401 — Spring Security
 *       7.0's {@code BearerTokenAuthenticationEntryPoint} does this natively.
 * </ul>
 */
@AutoConfiguration(after = OAuth2ResourceServerAutoConfiguration.class)
@EnableConfigurationProperties(MocapiOAuth2Properties.class)
@PropertySource("classpath:mocapi-oauth2-defaults.properties")
public class MocapiOAuth2AutoConfiguration {

  /**
   * Path at which the protected-resource metadata document is served. Fixed by RFC 9728 §3; Spring
   * Security's {@link OAuth2ProtectedResourceMetadataFilter} hardcodes the same value but keeps its
   * constant package-private, so we restate the literal here rather than reflect it in.
   */
  public static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

  /**
   * Fails fast at bean-init time on misconfigurations the MCP 2025-11-25 authorization spec
   * rejects:
   *
   * <ul>
   *   <li>No {@code JwtDecoder} on the classpath — means no JWT validation is happening, which
   *       would leave the MCP endpoint unprotected even with this module present. Triggers when the
   *       user forgot to set any {@code spring.security.oauth2.resourceserver.jwt.*} property.
   *   <li>Empty {@code spring.security.oauth2.resourceserver.jwt.audiences} — the spec mandates
   *       audience validation to prevent confused-deputy token reuse across MCP servers. Spring
   *       Boot auto-wires the {@code aud} validator only when this property is set, so an empty
   *       list silently skips enforcement.
   * </ul>
   */
  public MocapiOAuth2AutoConfiguration(
      ObjectProvider<JwtDecoder> jwtDecoder,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties) {
    validateComplianceMode(jwtDecoder, springResourceServerProperties);
  }

  private static void validateComplianceMode(
      ObjectProvider<JwtDecoder> jwtDecoder,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties) {
    if (jwtDecoder.getIfAvailable() == null) {
      throw new IllegalStateException(
          """
          mocapi-oauth2 is on the classpath but no JwtDecoder bean is registered — the MCP \
          endpoint would be exposed without JWT validation, which violates the MCP 2025-11-25 \
          authorization spec. Configure a JWT decoder via one of the standard Spring Boot \
          properties: spring.security.oauth2.resourceserver.jwt.issuer-uri, \
          spring.security.oauth2.resourceserver.jwt.jwk-set-uri, or \
          spring.security.oauth2.resourceserver.jwt.public-key-location.""");
    }
    OAuth2ResourceServerProperties rs = springResourceServerProperties.getIfAvailable();
    java.util.List<String> audiences =
        (rs == null) ? java.util.List.of() : rs.getJwt().getAudiences();
    if (audiences == null || audiences.isEmpty()) {
      throw new IllegalStateException(
          """
          spring.security.oauth2.resourceserver.jwt.audiences is empty. The MCP 2025-11-25 \
          authorization spec mandates audience validation: MCP servers MUST reject access tokens \
          that were not issued specifically for them (the aud claim must match). Configure at \
          least one expected audience value to prevent confused-deputy token reuse.""");
    }
  }

  /**
   * The single {@link SecurityFilterChain} mocapi owns. Scoped to the configured MCP endpoint path
   * (same {@code mocapi.endpoint} property the streamable-http controller reads, defaulting to
   * {@code /mcp}) plus the well-known metadata path. The metadata endpoint is {@code permitAll} so
   * clients can discover it anonymously — otherwise they could never start the OAuth2 flow.
   */
  @Bean
  @ConditionalOnMissingBean(name = "mcpOAuth2SecurityFilterChain")
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public SecurityFilterChain mcpOAuth2SecurityFilterChain(
      HttpSecurity http,
      MocapiOAuth2Properties properties,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties,
      ObjectProvider<Implementation> mcpServerInfo,
      ObjectProvider<OAuth2ProtectedResourceMetadataCustomizer> metadataCustomizers,
      ObjectProvider<MocapiOAuth2SecurityFilterChainCustomizer> chainCustomizers,
      @Value("${mocapi.endpoint:/mcp}") String mcpEndpoint)
      throws Exception {
    var builderCustomizer =
        metadataBuilderCustomizer(
            properties, springResourceServerProperties, mcpServerInfo, metadataCustomizers);

    http.securityMatcher(mcpEndpoint + "/**", METADATA_PATH)
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(METADATA_PATH).permitAll().anyRequest().authenticated())
        .csrf(csrf -> csrf.disable())
        .oauth2ResourceServer(
            rs ->
                rs.jwt(jwt -> {})
                    .protectedResourceMetadata(
                        prm -> prm.protectedResourceMetadataCustomizer(builderCustomizer)));

    for (MocapiOAuth2SecurityFilterChainCustomizer customizer :
        chainCustomizers.orderedStream().toList()) {
      customizer.customize(http);
    }
    return http.build();
  }

  private static Consumer<OAuth2ProtectedResourceMetadata.Builder> metadataBuilderCustomizer(
      MocapiOAuth2Properties properties,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties,
      ObjectProvider<Implementation> mcpServerInfo,
      ObjectProvider<OAuth2ProtectedResourceMetadataCustomizer> userCustomizers) {
    return builder -> {
      builder.resource(properties.resource());
      authorizationServersFor(properties, springResourceServerProperties)
          .forEach(builder::authorizationServer);
      properties.scopes().forEach(builder::scope);
      // bearer_methods_supported is defaulted to ["header"] by Spring Security's own default
      // customizer, so we don't add it here — doing so would duplicate the entry.
      resourceNameFor(mcpServerInfo).ifPresent(builder::resourceName);
      if (properties.resourceDocumentation() != null) {
        builder.claim("resource_documentation", properties.resourceDocumentation());
      }
      if (properties.resourcePolicyUri() != null) {
        builder.claim("resource_policy_uri", properties.resourcePolicyUri());
      }
      if (properties.resourceTosUri() != null) {
        builder.claim("resource_tos_uri", properties.resourceTosUri());
      }
      userCustomizers.orderedStream().forEach(c -> c.customize(builder));
    };
  }

  /**
   * Picks the human-readable resource name from the MCP {@link Implementation} server-info bean —
   * {@code title} when set, otherwise {@code name}. Returns empty when no server-info bean is on
   * the classpath (e.g., in a tightly-scoped test context that doesn't autowire the streamable-http
   * auto-configuration), or when both {@code title} and {@code name} are blank.
   *
   * <p>Package-private for direct unit testing — the {@code impl == null} and both-blank branches
   * aren't reachable from Spring-context tests in this module because the {@code Implementation}
   * bean is always on the classpath via {@code mocapi-streamable-http-transport}.
   */
  static java.util.Optional<String> resourceNameFor(ObjectProvider<Implementation> mcpServerInfo) {
    Implementation impl = mcpServerInfo.getIfAvailable();
    if (impl == null) {
      return java.util.Optional.empty();
    }
    if (StringUtils.isNotBlank(impl.title())) {
      return java.util.Optional.of(impl.title());
    }
    if (StringUtils.isNotBlank(impl.name())) {
      return java.util.Optional.of(impl.name());
    }
    return java.util.Optional.empty();
  }

  /**
   * Returns the authorization-servers list mocapi advertises in the metadata document. Uses the
   * explicit {@code mocapi.oauth2.authorization-servers} property when set; otherwise falls back to
   * the standard Spring Boot resource-server {@code issuer-uri}, so single-IdP setups don't have to
   * restate the value.
   */
  private static java.util.List<String> authorizationServersFor(
      MocapiOAuth2Properties properties,
      ObjectProvider<OAuth2ResourceServerProperties> springResourceServerProperties) {
    if (!properties.authorizationServers().isEmpty()) {
      return properties.authorizationServers();
    }
    OAuth2ResourceServerProperties rs = springResourceServerProperties.getIfAvailable();
    if (rs == null) {
      return java.util.List.of();
    }
    String issuerUri = rs.getJwt().getIssuerUri();
    return (issuerUri == null || issuerUri.isBlank())
        ? java.util.List.of()
        : java.util.List.of(issuerUri);
  }
}
