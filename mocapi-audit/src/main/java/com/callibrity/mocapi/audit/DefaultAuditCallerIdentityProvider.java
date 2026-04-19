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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Default {@link AuditCallerIdentityProvider} that pulls the caller from Spring Security's {@link
 * SecurityContextHolder}. Returns the {@link Authentication#getName()} of the current principal
 * when present and authenticated; {@link #ANONYMOUS} otherwise.
 *
 * <p>Activated by {@code MocapiAuditAutoConfiguration} only when Spring Security is on the
 * classpath. Context propagation from the request thread to the handler virtual thread is assumed
 * to be wired (see spec 182).
 */
public final class DefaultAuditCallerIdentityProvider implements AuditCallerIdentityProvider {

  @Override
  public String currentCaller() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return ANONYMOUS;
    }
    String name = authentication.getName();
    return (name == null || name.isEmpty()) ? ANONYMOUS : name;
  }
}
