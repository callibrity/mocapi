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
package com.callibrity.mocapi.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultAuditCallerIdentityProviderTest {

  private final DefaultAuditCallerIdentityProvider provider =
      new DefaultAuditCallerIdentityProvider();

  @AfterEach
  void clear_security_context() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void returns_anonymous_when_no_authentication_in_context() {
    assertThat(provider.currentCaller()).isEqualTo(AuditCallerIdentityProvider.ANONYMOUS);
  }

  @Test
  void returns_anonymous_when_authentication_is_not_authenticated() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(false);
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(provider.currentCaller()).isEqualTo(AuditCallerIdentityProvider.ANONYMOUS);
  }

  @Test
  void returns_anonymous_when_authentication_name_is_null() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getName()).thenReturn(null);
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(provider.currentCaller()).isEqualTo(AuditCallerIdentityProvider.ANONYMOUS);
  }

  @Test
  void returns_anonymous_when_authentication_name_is_empty() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getName()).thenReturn("");
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(provider.currentCaller()).isEqualTo(AuditCallerIdentityProvider.ANONYMOUS);
  }

  @Test
  void returns_authentication_name_when_authenticated_and_non_empty() {
    Authentication auth = mock(Authentication.class);
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getName()).thenReturn("alice");
    SecurityContextHolder.getContext().setAuthentication(auth);

    assertThat(provider.currentCaller()).isEqualTo("alice");
  }
}
