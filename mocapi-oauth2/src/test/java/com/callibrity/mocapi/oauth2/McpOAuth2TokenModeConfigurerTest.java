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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpOAuth2TokenModeConfigurerTest {

  @SuppressWarnings("unchecked")
  private final OAuth2ResourceServerConfigurer<HttpSecurity> configurer =
      mock(OAuth2ResourceServerConfigurer.class);

  @Test
  void jwt_mode_is_applied_when_jwt_decoder_is_present() {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);

    McpOAuth2TokenModeConfigurer.apply(configurer, jwtDecoder, null, List.of());

    verify(configurer).jwt(any());
  }

  @Test
  void jwt_mode_wins_when_both_decoders_are_present() {
    JwtDecoder jwtDecoder = mock(JwtDecoder.class);
    OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);

    McpOAuth2TokenModeConfigurer.apply(configurer, jwtDecoder, introspector, List.of("api"));

    verify(configurer).jwt(any());
    verifyNoInteractions(introspector);
  }

  @Test
  void opaque_token_mode_is_applied_when_only_introspector_is_present() {
    OpaqueTokenIntrospector introspector = mock(OpaqueTokenIntrospector.class);

    McpOAuth2TokenModeConfigurer.apply(configurer, null, introspector, List.of("api"));

    verify(configurer).opaqueToken(any());
  }

  @Test
  void throws_illegal_state_when_both_decoders_are_null() {
    List<String> audiences = List.of();
    assertThatThrownBy(() -> McpOAuth2TokenModeConfigurer.apply(configurer, null, null, audiences))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JwtDecoder")
        .hasMessageContaining("OpaqueTokenIntrospector");
  }
}
