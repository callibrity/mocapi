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
 * Strategy for layering additional configuration onto the mocapi-managed security chain for the
 * OAuth2 Protected Resource Metadata endpoint ({@code /.well-known/oauth-protected-resource}). Use
 * this for HTTP-layer concerns like CORS, security headers, and rate limiting.
 *
 * <p><strong>Auth policy is frozen.</strong> RFC 9728 §3 requires the metadata endpoint to be
 * publicly accessible so unauthenticated clients can fetch it to discover how to authenticate. Do
 * not use this customizer to add authentication requirements to the endpoint; doing so produces a
 * non-compliant MCP server. The endpoint is {@code permitAll} by design.
 *
 * <p>All customizer beans are invoked in Spring's natural ordering after mocapi's defaults are
 * applied.
 */
@FunctionalInterface
public interface McpMetadataFilterChainCustomizer {

  /**
   * Mutate the given {@link HttpSecurity} configuration before the metadata chain is built.
   *
   * @param http the in-progress security configuration, already restricted to the metadata path
   *     with {@code permitAll} + CSRF disabled + the {@code OAuth2ProtectedResourceMetadataFilter}
   *     wired
   * @throws Exception if the customization itself fails (matches the signature of every {@code
   *     HttpSecurity} configurer so users can call those freely)
   */
  // Sonar S112: see McpFilterChainCustomizer for the same rationale.
  @SuppressWarnings("java:S112")
  void customize(HttpSecurity http) throws Exception;
}
