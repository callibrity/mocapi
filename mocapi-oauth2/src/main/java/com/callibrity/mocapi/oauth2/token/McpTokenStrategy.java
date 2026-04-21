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
package com.callibrity.mocapi.oauth2.token;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;

/**
 * Strategy for configuring bearer-token validation on an {@link OAuth2ResourceServerConfigurer}.
 * One instance per mocapi deployment — mocapi's autoconfig registers either {@link
 * JwtMcpTokenStrategy} or {@link OpaqueTokenMcpTokenStrategy} as a Spring bean based on which
 * Spring Boot auto-wired bean is present ({@code JwtDecoder} or {@code OpaqueTokenIntrospector}).
 *
 * <p>Applied in two places by {@link McpFilterChains}:
 *
 * <ul>
 *   <li>The MCP endpoint filter chain ({@code /mcp/**}) — enforces bearer-token authentication on
 *       every request.
 *   <li>The metadata filter chain ({@code /.well-known/oauth-protected-resource}) — required only
 *       to satisfy Spring Security's DSL, which refuses to build an {@code oauth2ResourceServer}
 *       configurer without a bearer-token format declared. The metadata chain's {@code
 *       authorizeHttpRequests} rule is {@code permitAll}, so tokens are never actually validated on
 *       the metadata path.
 * </ul>
 *
 * <p>Users implementing a new token-validation flavor (hypothetical future token formats) can
 * register their own {@link McpTokenStrategy} {@link
 * org.springframework.context.annotation.Primary @Primary} bean to take over both chains.
 */
@FunctionalInterface
public interface McpTokenStrategy {

  /** Apply this token-validation mode to the given {@code oauth2ResourceServer} configurer. */
  void apply(OAuth2ResourceServerConfigurer<HttpSecurity> oauth2);
}
