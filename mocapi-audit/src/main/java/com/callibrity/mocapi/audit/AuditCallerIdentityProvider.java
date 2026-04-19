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

/**
 * Pluggable extraction of the caller identity that the audit layer stamps onto every event. The
 * default implementation reads Spring Security's {@code SecurityContextHolder}; applications with
 * richer identity semantics (e.g., tenant-id plus user-id composite) provide their own {@code
 * AuditCallerIdentityProvider} {@link org.springframework.context.annotation.Bean @Bean} to
 * override.
 */
@FunctionalInterface
public interface AuditCallerIdentityProvider {

  /** Value used when no authenticated caller is in scope. */
  String ANONYMOUS = "anonymous";

  /**
   * Returns the current caller's identity. Never {@code null}: implementations must return {@link
   * #ANONYMOUS} when no authenticated caller is in scope (e.g., during the initialize handshake,
   * under stdio transport without any authentication, or when Spring Security is absent).
   */
  String currentCaller();
}
