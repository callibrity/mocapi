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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * MCP 2025-11-25 authorization-spec compliance guardrails. Called at bean-init time from {@code
 * MocapiOAuth2AutoConfiguration} to fail fast on misconfigurations that would otherwise leave the
 * MCP endpoint exposed without token validation or without audience enforcement.
 *
 * <p>Pure logic: takes already-resolved concrete values (not {@code ObjectProvider}s) so it can be
 * exercised directly from unit tests without a Spring context.
 */
public final class MocapiOAuth2Compliance {

  private MocapiOAuth2Compliance() {}

  /**
   * Fails fast when either of the MCP-mandated prerequisites is missing:
   *
   * <ul>
   *   <li>Neither {@link JwtDecoder} nor {@link OpaqueTokenIntrospector} is registered — means no
   *       token validation is happening, which would leave the MCP endpoint unprotected even with
   *       this module present. Triggers when the user forgot to set any {@code
   *       spring.security.oauth2.resourceserver.jwt.*} or {@code
   *       spring.security.oauth2.resourceserver.opaquetoken.*} property.
   *   <li>Empty {@code audiences} list — the spec mandates audience validation to prevent
   *       confused-deputy token reuse across MCP servers. Spring Boot auto-wires the {@code aud}
   *       validator only when this property is set, so an empty list silently skips enforcement.
   *       The same property is reused for both JWT and opaque-token modes (see {@link
   *       AudienceCheckingOpaqueTokenIntrospector}).
   * </ul>
   *
   * @param jwtDecoder the resolved decoder bean, or {@code null} if none is registered
   * @param opaqueTokenIntrospector the resolved introspector bean, or {@code null} if none is
   *     registered
   * @param audiences the configured {@code spring.security.oauth2.resourceserver.jwt.audiences}
   *     list; never {@code null}
   */
  public static void validate(
      JwtDecoder jwtDecoder,
      OpaqueTokenIntrospector opaqueTokenIntrospector,
      List<String> audiences) {
    if (jwtDecoder == null && opaqueTokenIntrospector == null) {
      throw new IllegalStateException(
          """
          mocapi-oauth2 is on the classpath but neither JwtDecoder nor OpaqueTokenIntrospector \
          is registered — the MCP endpoint would be exposed without token validation, which \
          violates the MCP 2025-11-25 authorization spec. Configure token validation via one of: \
          spring.security.oauth2.resourceserver.jwt.issuer-uri (or .jwk-set-uri / \
          .public-key-location) for JWT tokens, or \
          spring.security.oauth2.resourceserver.opaquetoken.introspection-uri for opaque \
          tokens.""");
    }
    if (audiences.isEmpty()) {
      throw new IllegalStateException(
          """
          spring.security.oauth2.resourceserver.jwt.audiences is empty. The MCP 2025-11-25 \
          authorization spec mandates audience validation: MCP servers MUST reject access tokens \
          that were not issued specifically for them (the aud claim must match). Configure at \
          least one expected audience value to prevent confused-deputy token reuse. The property \
          applies to both JWT and opaque-token modes.""");
    }
  }
}
