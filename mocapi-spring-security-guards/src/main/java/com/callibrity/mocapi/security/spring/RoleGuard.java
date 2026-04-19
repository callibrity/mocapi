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

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.callibrity.mocapi.server.guards.Guard;
import com.callibrity.mocapi.server.guards.GuardDecision;
import com.callibrity.mocapi.server.guards.GuardDecision.Allow;
import com.callibrity.mocapi.server.guards.GuardDecision.Deny;
import java.util.Arrays;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link Guard} that allows the call when the current {@code Authentication} holds at least one of
 * the configured roles (OR semantics). Role values may be supplied bare ({@code "ADMIN"}) or with
 * the canonical Spring Security prefix ({@code "ROLE_ADMIN"}); both normalize to the same granted
 * authority.
 */
public final class RoleGuard implements Guard {

  private static final String ROLE_PREFIX = "ROLE_";

  private final Set<String> allowedAuthorities;

  public RoleGuard(String... roles) {
    if (roles.length == 0) {
      throw new IllegalArgumentException("RoleGuard requires at least one role");
    }
    this.allowedAuthorities =
        Arrays.stream(roles)
            .map(r -> r.startsWith(ROLE_PREFIX) ? r : ROLE_PREFIX + r)
            .collect(toUnmodifiableSet());
  }

  @Override
  public GuardDecision check() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return new Deny("unauthenticated");
    }
    boolean hasRole =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(allowedAuthorities::contains);
    if (!hasRole) {
      return new Deny("insufficient role");
    }
    return new Allow();
  }
}
