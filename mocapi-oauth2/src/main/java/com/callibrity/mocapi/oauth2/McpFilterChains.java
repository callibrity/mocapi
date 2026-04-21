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

import com.callibrity.mocapi.oauth2.metadata.McpMetadataCustomizer;
import com.callibrity.mocapi.oauth2.token.JwtMcpTokenStrategy;
import com.callibrity.mocapi.oauth2.token.McpTokenStrategy;
import com.callibrity.mocapi.oauth2.token.OpaqueTokenMcpTokenStrategy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Static factories that assemble the two {@link SecurityFilterChain SecurityFilterChains} mocapi
 * owns for MCP over OAuth2:
 *
 * <ul>
 *   <li>{@link #createMcpMetadataFilterChain(HttpSecurity, McpMetadataFilterChainConfig)} — serves
 *       the OAuth2 Protected Resource Metadata document at {@code
 *       /.well-known/oauth-protected-resource} (RFC 9728). {@code permitAll}, CSRF disabled, {@link
 *       OAuth2ProtectedResourceMetadataFilter} wired, document populated from {@link
 *       MocapiOAuth2Properties} + registered {@link McpMetadataCustomizer} beans.
 *   <li>{@link #createMcpFilterChain(HttpSecurity, McpFilterChainConfig)} — enforces OAuth2
 *       bearer-token authentication on the MCP endpoint.
 * </ul>
 *
 * <p>Both chains apply the same {@link McpTokenStrategy} to their {@code oauth2ResourceServer}
 * configurer. The MCP chain uses it to enforce authentication; the metadata chain uses it only to
 * satisfy Spring Security's DSL (which refuses to build {@code oauth2ResourceServer} without a
 * bearer-token format declared) — the metadata chain still {@code permitAll}s every request.
 *
 * <p>All chains run user-supplied customizers last so user configuration composes on top of
 * mocapi's defaults. Chain ordering (metadata before MCP) is the caller's responsibility via
 * {@code @Order} on the registering {@code @Bean} methods.
 */
public final class McpFilterChains {

  /** RFC 9728 §3 well-known path; Spring Security's metadata filter hardcodes the same. */
  public static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

  private McpFilterChains() {}

  /**
   * Builds the {@link SecurityFilterChain} that serves the OAuth2 Protected Resource Metadata
   * document. Public by design ({@code permitAll}) — RFC 9728 §3 requires the endpoint to be
   * fetchable without authentication so clients can discover how to authenticate.
   */
  @SuppressWarnings("java:S4502") // Disabling CSRF is safe
  public static SecurityFilterChain createMcpMetadataFilterChain(
      HttpSecurity http, McpMetadataFilterChainConfig config) throws Exception {
    http.securityMatcher(METADATA_PATH)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2ResourceServer(
            rs -> {
              config.tokenStrategy().apply(rs);
              rs.protectedResourceMetadata(
                  prm ->
                      prm.protectedResourceMetadataCustomizer(
                          builder ->
                              config
                                  .metadataCustomizers()
                                  .forEach(customizer -> customizer.customize(builder))));
            });

    for (McpMetadataFilterChainCustomizer customizer : config.chainCustomizers()) {
      customizer.customize(http);
    }
    return http.build();
  }

  /**
   * Builds the {@link SecurityFilterChain} that enforces OAuth2 bearer-token authentication on the
   * MCP endpoint. Token validation mode is controlled by the {@link McpTokenStrategy} on the config
   * — {@link JwtMcpTokenStrategy} or {@link OpaqueTokenMcpTokenStrategy} for mocapi's built-in
   * modes, or a user-supplied implementation for alternatives.
   */
  @SuppressWarnings("java:S4502") // Disabling CSRF is safe
  public static SecurityFilterChain createMcpFilterChain(
      HttpSecurity http, McpFilterChainConfig config) throws Exception {
    http.securityMatcher(config.mcpEndpoint(), config.mcpEndpoint() + "/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2ResourceServer(rs -> config.tokenStrategy().apply(rs));

    for (McpFilterChainCustomizer customizer : config.chainCustomizers()) {
      customizer.customize(http);
    }
    return http.build();
  }
}
