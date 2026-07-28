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

import com.callibrity.mocapi.server.mrtr.McpPrincipalSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link McpPrincipalSource} backed by Spring Security's {@link SecurityContextHolder}. Returns the
 * authenticated principal's {@link Authentication#getName() name} (the JWT {@code sub} for a
 * resource-server deployment), or {@code null} when the request is unauthenticated or anonymous —
 * so the MRTR engine binds {@code requestState} to the real caller and rejects cross-principal
 * replay, but leaves unauthenticated traffic unbound.
 *
 * <p>Relies on the transport propagating the {@code SecurityContext} to the dispatch virtual thread
 * — Spring Security registers a {@code SecurityContextHolderThreadLocalAccessor} via the Service
 * loader, so the controller's {@code ContextSnapshotFactory.captureAll()} carries it across
 * automatically.
 */
public final class SecurityContextMcpPrincipalSource implements McpPrincipalSource {

  @Override
  public String currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken) {
      return null;
    }
    String name = authentication.getName();
    return (name == null || name.isBlank()) ? null : name;
  }
}
