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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * Wraps another {@link OpaqueTokenIntrospector} and enforces that the introspected token's {@code
 * aud} claim intersects a configured list of expected audiences. Mirrors the behavior Spring Boot
 * auto-wires for JWTs via {@code JwtClaimValidator("aud", ...)} — which doesn't apply on the
 * opaque-token path, because introspection-based validation happens post-introspection on the
 * token's attributes map rather than on a {@code Jwt} decoder.
 *
 * <p>Required for MCP 2025-11-25 compliance in opaque-token mode: the spec mandates audience
 * validation to prevent confused-deputy token reuse across MCP servers, and without this wrapper a
 * deployment using opaque tokens would silently skip it.
 */
public class AudienceCheckingOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

  private final OpaqueTokenIntrospector delegate;
  private final List<String> expectedAudiences;

  public AudienceCheckingOpaqueTokenIntrospector(
      OpaqueTokenIntrospector delegate, List<String> expectedAudiences) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    this.expectedAudiences = List.copyOf(Objects.requireNonNull(expectedAudiences, "audiences"));
  }

  @Override
  public OAuth2AuthenticatedPrincipal introspect(String token) {
    OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
    Object aud = principal.getAttribute(OAuth2TokenIntrospectionClaimNames.AUD);
    if (!audienceMatches(aud)) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error(
              OAuth2ErrorCodes.INVALID_TOKEN,
              "The required audience is missing or does not match "
                  + "any of the configured values: "
                  + expectedAudiences,
              null));
    }
    return principal;
  }

  private boolean audienceMatches(Object aud) {
    if (aud instanceof Collection<?> list) {
      for (Object value : list) {
        if (expectedAudiences.contains(String.valueOf(value))) {
          return true;
        }
      }
      return false;
    }
    if (aud instanceof String single) {
      return expectedAudiences.contains(single);
    }
    return false;
  }
}
