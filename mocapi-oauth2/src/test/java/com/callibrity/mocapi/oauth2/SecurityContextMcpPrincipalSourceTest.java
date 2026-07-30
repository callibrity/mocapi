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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SecurityContextMcpPrincipalSourceTest {

  private final SecurityContextMcpPrincipalSource source = new SecurityContextMcpPrincipalSource();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returns_the_authentication_name_when_authenticated() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(source.currentPrincipal()).isEqualTo("alice");
  }

  @Test
  void returns_null_when_unauthenticated() {
    assertThat(source.currentPrincipal()).isNull();
  }

  @Test
  void returns_null_for_anonymous_authentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    assertThat(source.currentPrincipal()).isNull();
  }

  @Test
  void returns_null_when_an_authentication_is_present_but_not_yet_authenticated() {
    // A pre-authentication token — credentials supplied, not yet validated. Binding requestState to
    // it would tie an MRTR round trip to an identity the server has not actually verified.
    SecurityContextHolder.getContext()
        .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("alice", "n/a"));

    assertThat(source.currentPrincipal()).isNull();
  }

  @Test
  void returns_null_when_the_authentication_name_is_null() {
    // A custom AuthenticationProvider can yield a token with no name. Returning it verbatim would
    // put a null principal into the requestState binding rather than leaving the token unbound.
    SecurityContextHolder.getContext().setAuthentication(authenticatedWithName(null));

    assertThat(source.currentPrincipal()).isNull();
  }

  @Test
  void returns_null_when_the_authentication_name_is_blank() {
    // Blank is treated as absent: a whitespace principal must not become a binding value that two
    // different unnamed callers could both satisfy on replay.
    SecurityContextHolder.getContext().setAuthentication(authenticatedWithName("   "));

    assertThat(source.currentPrincipal()).isNull();
  }

  private static Authentication authenticatedWithName(String name) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authentication.getName()).thenReturn(name);
    return authentication;
  }
}
