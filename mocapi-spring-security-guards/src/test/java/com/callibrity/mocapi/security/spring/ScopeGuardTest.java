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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ScopeGuardTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void constructor_rejects_empty_scope_list() {
    assertThatThrownBy(ScopeGuard::new).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void denies_when_no_authentication() {
    GuardDecision decision = new ScopeGuard("admin:write").check();
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
    GuardDecision decision = new ScopeGuard("admin:write").check();
    assertThat(decision).isInstanceOf(Deny.class);
    assertThat(((Deny) decision).reason()).isEqualTo("unauthenticated");
  }

  @Test
  void denies_when_required_scope_missing_and_lists_all_missing() {
    setAuthentication(authenticatedWith("SCOPE_read"));
    GuardDecision decision = new ScopeGuard("admin:write", "audit:read").check();
    assertThat(decision).isInstanceOf(Deny.class);
    assertThat(((Deny) decision).reason()).isEqualTo("missing scope(s): admin:write, audit:read");
  }

  @Test
  void denies_when_only_some_required_scopes_present() {
    setAuthentication(authenticatedWith("SCOPE_admin:write", "SCOPE_read"));
    GuardDecision decision = new ScopeGuard("admin:write", "audit:read").check();
    assertThat(decision).isInstanceOf(Deny.class);
    assertThat(((Deny) decision).reason()).isEqualTo("missing scope(s): audit:read");
  }

  @Test
  void toString_describes_required_scopes_sorted() {
    assertThat(new ScopeGuard("zeta", "alpha").toString()).isEqualTo("RequiresScope(alpha,zeta)");
  }

  @Test
  void allows_when_all_required_scopes_present() {
    setAuthentication(authenticatedWith("SCOPE_admin:write", "SCOPE_audit:read", "SCOPE_extra"));
    GuardDecision decision = new ScopeGuard("admin:write", "audit:read").check();
    assertThat(decision).isInstanceOf(Allow.class);
  }

  private static UsernamePasswordAuthenticationToken authenticatedWith(String... authorities) {
    List<SimpleGrantedAuthority> granted =
        List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
    return UsernamePasswordAuthenticationToken.authenticated("user", "n/a", granted);
  }

  private static void setAuthentication(Authentication authentication) {
    SecurityContext context = new SecurityContextImpl(authentication);
    SecurityContextHolder.setContext(context);
  }
}
