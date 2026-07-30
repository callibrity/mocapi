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
import java.util.List;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

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

  /** Authority prefix Spring Security's JWT and opaque-token scope converters emit. */
  private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

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
        .authorizeHttpRequests(auth -> authorizeMcpEndpoint(auth, config.requiredScopes()))
        .csrf(AbstractHttpConfigurer::disable)
        .oauth2ResourceServer(rs -> config.tokenStrategy().apply(rs));

    for (McpFilterChainCustomizer customizer : config.chainCustomizers()) {
      customizer.customize(http);
    }
    return http.build();
  }

  /**
   * Applies the endpoint's authorization rule. This is the <em>only</em> {@code anyRequest()} call
   * mocapi makes on this chain, and that is load-bearing: {@code
   * HttpSecurity.authorizeHttpRequests} reuses one registry across calls, and {@code anyRequest()}
   * asserts it has not already been configured — so a second call (from here or from an {@link
   * McpFilterChainCustomizer}) fails the context at startup with "Can't configure anyRequest after
   * itself". Rules are also evaluated in registration order with first-match-wins, so a
   * customizer's later {@code requestMatchers(...)} rule would never be reached behind this one.
   * Resource-level scopes therefore have to be expressed here, through {@code requiredScopes},
   * rather than added by a customizer.
   *
   * <p>With no required scopes this is plain {@code authenticated()} — the behavior before the
   * property existed. With required scopes, all of them must be present (AND), and Spring's {@code
   * BearerTokenAccessDeniedHandler} turns the resulting denial into a {@code 403} bearer challenge
   * carrying {@code error="insufficient_scope"} (RFC 6750 §3.1).
   */
  private static void authorizeMcpEndpoint(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
      List<String> requiredScopes) {
    if (requiredScopes == null || requiredScopes.isEmpty()) {
      auth.anyRequest().authenticated();
    } else {
      auth.anyRequest().access(allOfScopes(requiredScopes));
    }
  }

  /**
   * AND-composes one {@code hasAuthority("SCOPE_<scope>")} manager per required scope, matching the
   * AND semantics of {@code @RequiresScope} at the handler layer.
   *
   * <p>Folded pairwise rather than passed as an array on purpose: {@code
   * AuthorizationManagers.allOf} grants access when handed <em>zero</em> managers, so building the
   * argument list dynamically would turn an empty scope list into "permit everyone" — bypassing
   * even authentication. The empty case is handled by the caller instead, and this method is only
   * ever reached with at least one scope.
   */
  private static AuthorizationManager<RequestAuthorizationContext> allOfScopes(
      List<String> requiredScopes) {
    AuthorizationManager<RequestAuthorizationContext> combined = null;
    for (String scope : requiredScopes) {
      AuthorizationManager<RequestAuthorizationContext> next =
          AuthorityAuthorizationManager.hasAuthority(SCOPE_AUTHORITY_PREFIX + scope);
      combined = (combined == null) ? next : AuthorizationManagers.allOf(combined, next);
    }
    return combined;
  }
}
