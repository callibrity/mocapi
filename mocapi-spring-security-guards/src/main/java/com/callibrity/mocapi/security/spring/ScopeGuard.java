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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link Guard} that allows the call only when the current {@code Authentication} holds every
 * required OAuth2 scope. Scopes are read from the granted authorities with the {@code SCOPE_}
 * prefix Spring Security's JWT / opaque-token converters emit. All required scopes must be present
 * (AND semantics).
 */
public final class ScopeGuard implements Guard {

  private static final String SCOPE_PREFIX = "SCOPE_";

  private final Set<String> requiredScopes;

  public ScopeGuard(String... scopes) {
    if (scopes.length == 0) {
      throw new IllegalArgumentException("ScopeGuard requires at least one scope");
    }
    this.requiredScopes = Set.copyOf(List.of(scopes));
  }

  @Override
  public GuardDecision check() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return new Deny("unauthenticated");
    }
    Set<String> scopes =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith(SCOPE_PREFIX))
            .map(a -> a.substring(SCOPE_PREFIX.length()))
            .collect(toUnmodifiableSet());
    if (!scopes.containsAll(requiredScopes)) {
      Set<String> missing = new TreeSet<>(requiredScopes);
      missing.removeAll(scopes);
      return new Deny("missing scope(s): " + String.join(", ", missing));
    }
    return new Allow();
  }

  @Override
  public String toString() {
    return "RequiresScope(" + String.join(",", new TreeSet<>(requiredScopes)) + ")";
  }
}
