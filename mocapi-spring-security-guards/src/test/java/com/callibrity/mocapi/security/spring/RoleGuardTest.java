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
package com.callibrity.mocapi.security.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.guards.GuardDecision.Allow;
import com.callibrity.mocapi.server.guards.GuardDecision.Deny;
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
import org.springframework.security.core.context.SecurityContextImpl;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RoleGuardTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void constructor_rejects_empty_role_list() {
    assertThatThrownBy(RoleGuard::new).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void denies_when_no_authentication() {
    GuardDecision decision = new RoleGuard("ADMIN").check();
    assertThat(decision).isInstanceOf(Deny.class);
    assertThat(((Deny) decision).reason()).isEqualTo("unauthenticated");
  }

  @Test
  void denies_when_authentication_is_not_authenticated() {
    AnonymousAuthenticationToken anon =
        new AnonymousAuthenticationToken(
            "key", "anon", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    anon.setAuthenticated(false);
    setAuthentication(anon);
    GuardDecision decision = new RoleGuard("ADMIN").check();
    assertThat(decision).isInstanceOf(Deny.class);
    assertThat(((Deny) decision).reason()).isEqualTo("unauthenticated");
  }

  @Test
  void denies_when_authentication_has_no_configured_role() {
    setAuthentication(authenticatedWith("ROLE_USER"));
    GuardDecision decision = new RoleGuard("ADMIN", "OPS").check();
    assertThat(decision).isInstanceOf(Deny.class);
    assertThat(((Deny) decision).reason()).isEqualTo("insufficient role");
  }

  @Test
  void allows_when_authentication_has_any_configured_role() {
    setAuthentication(authenticatedWith("ROLE_OPS"));
    GuardDecision decision = new RoleGuard("ADMIN", "OPS").check();
    assertThat(decision).isInstanceOf(Allow.class);
  }

  @Test
  void toString_describes_required_roles_sorted_without_prefix() {
    assertThat(new RoleGuard("OPS", "ROLE_ADMIN").toString()).isEqualTo("RequiresRole(ADMIN,OPS)");
  }

  @Test
  void accepts_bare_and_prefixed_role_values_interchangeably() {
    setAuthentication(authenticatedWith("ROLE_ADMIN"));
    assertThat(new RoleGuard("ADMIN").check()).isInstanceOf(Allow.class);
    assertThat(new RoleGuard("ROLE_ADMIN").check()).isInstanceOf(Allow.class);
  }

  private static UsernamePasswordAuthenticationToken authenticatedWith(String... authorities) {
    List<SimpleGrantedAuthority> granted =
        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
    return UsernamePasswordAuthenticationToken.authenticated("user", "n/a", granted);
  }

  private static void setAuthentication(Authentication authentication) {
    SecurityContextHolder.setContext(new SecurityContextImpl(authentication));
  }
}
