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

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Strategy for layering additional authorization rules onto the mocapi-managed security chain after
 * it has been configured with OAuth2 resource-server + bearer-token defaults. Use this to require
 * specific scopes, add CORS rules, restrict to certain roles, etc., without having to redeclare the
 * whole {@link org.springframework.security.web.SecurityFilterChain SecurityFilterChain}.
 *
 * <p>All customizer beans are invoked in Spring's natural ordering after mocapi's defaults are
 * applied, so user rules compose on top of (and can override) the built-ins.
 */
@FunctionalInterface
public interface MocapiOAuth2SecurityFilterChainCustomizer {

  /**
   * Mutate the given {@link HttpSecurity} configuration before the chain is built.
   *
   * @param http the in-progress security configuration, already restricted to the MCP endpoint path
   *     and pre-wired with OAuth2 resource-server + entry-point defaults
   * @throws Exception if the customization itself fails (matches the signature of every {@code
   *     HttpSecurity} configurer so users can call those freely)
   */
  // Sonar S112: the checked `throws Exception` intentionally mirrors Spring's own HttpSecurity
  // configurer methods (e.g. addFilter, apply, build) so implementers can invoke them without
  // having to wrap every call in a try/catch. Narrowing this to a specific exception type would
  // force every user to rethrow or wrap.
  @SuppressWarnings("java:S112")
  void customize(HttpSecurity http) throws Exception;
}
