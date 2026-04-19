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

import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * Picks the token-validation mode for the MCP security filter chain based on which Spring Boot
 * auto-wired bean is available: {@link JwtDecoder} for JWT mode, {@link OpaqueTokenIntrospector}
 * for introspection-based opaque-token mode. JWT wins if both happen to be present (same precedence
 * Spring Boot's own auto-config applies).
 *
 * <p>In opaque-token mode, the introspector is wrapped with {@link
 * AudienceCheckingOpaqueTokenIntrospector} to enforce the same {@code aud} validation Spring Boot
 * auto-wires for JWTs — MCP requires it, and Spring's opaque-token path otherwise skips audience
 * checks.
 */
public final class McpOAuth2TokenModeConfigurer {

  private McpOAuth2TokenModeConfigurer() {}

  public static void apply(
      OAuth2ResourceServerConfigurer<HttpSecurity> rs,
      JwtDecoder jwtDecoder,
      OpaqueTokenIntrospector opaqueTokenIntrospector,
      List<String> audiences) {
    if (jwtDecoder != null) {
      rs.jwt(jwt -> {});
      return;
    }
    if (opaqueTokenIntrospector == null) {
      // MocapiOAuth2Compliance.validate already rejected this case; belt-and-suspenders.
      throw new IllegalStateException("No JwtDecoder or OpaqueTokenIntrospector available");
    }
    OpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(opaqueTokenIntrospector, audiences);
    rs.opaqueToken(opaque -> opaque.introspector(introspector));
  }
}
