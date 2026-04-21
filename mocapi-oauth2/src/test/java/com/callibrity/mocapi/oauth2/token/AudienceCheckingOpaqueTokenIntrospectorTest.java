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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

/**
 * Direct unit coverage for {@link AudienceCheckingOpaqueTokenIntrospector}. Covers every way the
 * {@code aud} claim can appear in an introspection response and the rejection path when none of the
 * expected audiences match.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AudienceCheckingOpaqueTokenIntrospectorTest {

  @Test
  void accepts_token_when_aud_list_contains_expected_audience() {
    AudienceCheckingOpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(
            stubReturning(Map.of("aud", List.of("other", "mcp-test", "another"))),
            List.of("mcp-test"));
    assertThatCode(() -> introspector.introspect("token")).doesNotThrowAnyException();
  }

  @Test
  void accepts_token_when_aud_is_single_string_matching_expected_audience() {
    AudienceCheckingOpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(
            stubReturning(Map.of("aud", "mcp-test")), List.of("mcp-test"));
    assertThatCode(() -> introspector.introspect("token")).doesNotThrowAnyException();
  }

  @Test
  void rejects_token_when_aud_list_does_not_contain_any_expected_audience() {
    AudienceCheckingOpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(
            stubReturning(Map.of("aud", List.of("other-resource"))), List.of("mcp-test"));
    assertThatThrownBy(() -> introspector.introspect("token"))
        .isInstanceOf(OAuth2AuthenticationException.class)
        .hasMessageContaining("required audience");
  }

  @Test
  void rejects_token_when_aud_single_string_does_not_match() {
    AudienceCheckingOpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(
            stubReturning(Map.of("aud", "other-resource")), List.of("mcp-test"));
    assertThatThrownBy(() -> introspector.introspect("token"))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void rejects_token_when_aud_claim_is_absent() {
    AudienceCheckingOpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(
            stubReturning(Map.of("sub", "user")), List.of("mcp-test"));
    assertThatThrownBy(() -> introspector.introspect("token"))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void accepts_token_when_any_of_multiple_expected_audiences_matches() {
    AudienceCheckingOpaqueTokenIntrospector introspector =
        new AudienceCheckingOpaqueTokenIntrospector(
            stubReturning(Map.of("aud", List.of("backend-b"))),
            List.of("backend-a", "backend-b", "backend-c"));
    OAuth2AuthenticatedPrincipal principal = introspector.introspect("token");
    Object aud = principal.getAttribute(OAuth2TokenIntrospectionClaimNames.AUD);
    assertThat(aud).isNotNull();
  }

  private static OpaqueTokenIntrospector stubReturning(Map<String, Object> claims) {
    OpaqueTokenIntrospector delegate = mock(OpaqueTokenIntrospector.class);
    OAuth2AuthenticatedPrincipal principal =
        new DefaultOAuth2AuthenticatedPrincipal(
            claims, AuthorityUtils.createAuthorityList("SCOPE_read"));
    when(delegate.introspect("token")).thenReturn(principal);
    return delegate;
  }
}
