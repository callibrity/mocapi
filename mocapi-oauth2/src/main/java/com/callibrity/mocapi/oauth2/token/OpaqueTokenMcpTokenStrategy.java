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

import java.util.List;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * {@link McpTokenStrategy} for opaque bearer tokens validated via RFC 7662 introspection. Wraps the
 * configured {@link OpaqueTokenIntrospector} with {@link AudienceCheckingOpaqueTokenIntrospector}
 * so the MCP-required {@code aud} check runs on every introspected token — Spring's opaque-token
 * path skips audience checks by default, whereas the JWT path enforces them automatically via
 * {@code spring.security.oauth2.resourceserver.jwt.audiences}.
 */
public final class OpaqueTokenMcpTokenStrategy implements McpTokenStrategy {

  private final OpaqueTokenIntrospector introspector;

  public OpaqueTokenMcpTokenStrategy(OpaqueTokenIntrospector introspector, List<String> audiences) {
    this.introspector = new AudienceCheckingOpaqueTokenIntrospector(introspector, audiences);
  }

  @Override
  public void apply(OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) {
    oauth2.opaqueToken(opaque -> opaque.introspector(introspector));
  }
}
