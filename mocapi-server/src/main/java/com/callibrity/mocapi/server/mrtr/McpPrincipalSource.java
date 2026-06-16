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
package com.callibrity.mocapi.server.mrtr;

/**
 * Supplies the authenticated principal for the current request so the MRTR engine can bind a {@code
 * requestState} token to its caller and reject a retry presented by a different principal (the MRTR
 * replay-prevention guidance). The mocapi core is authentication-agnostic, so this is a seam: the
 * default implementation returns {@code null} (unauthenticated), and an authenticated deployment
 * provides a bean that reads its own security context (e.g. the OAuth2 JWT subject).
 */
@FunctionalInterface
public interface McpPrincipalSource {

  /**
   * Returns a stable identifier for the authenticated principal of the in-flight request, or {@code
   * null} if the request is unauthenticated. Must be consistent within a single MRTR conversation
   * so that round 1 and the retry resolve to the same value.
   *
   * @return the current principal id, or {@code null} when unauthenticated
   */
  String currentPrincipal();
}
