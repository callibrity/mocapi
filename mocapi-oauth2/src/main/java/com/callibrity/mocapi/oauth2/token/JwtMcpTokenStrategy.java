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

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;

/**
 * {@link McpTokenStrategy} for JWT bearer tokens. Delegates to Spring Boot's own JWT auto-wiring —
 * the {@code JwtDecoder} bean registered via {@code spring.security.oauth2.resourceserver.jwt.*}
 * properties handles audience validation ({@code spring.security.oauth2.resourceserver.jwt
 * .audiences}) and issuer validation without any additional configuration here.
 */
public final class JwtMcpTokenStrategy implements McpTokenStrategy {

  @Override
  public void apply(OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) {
    oauth2.jwt(Customizer.withDefaults());
  }
}
