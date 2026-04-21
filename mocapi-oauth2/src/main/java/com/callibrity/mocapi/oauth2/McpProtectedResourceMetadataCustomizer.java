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

import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Strategy for adding arbitrary claims to the protected-resource metadata document before it is
 * served. Register one or more beans implementing this interface to append fields mocapi does not
 * surface directly through {@link MocapiOAuth2Properties} — for example {@code
 * tls_client_certificate_bound_access_tokens}, custom MCP-specific claims, or non-standard
 * authorization-server metadata.
 *
 * <p>All customizer beans are invoked in Spring's natural ordering after the base builder is
 * populated from {@link MocapiOAuth2Properties}, so user customizers can both add new claims and
 * override mocapi-supplied defaults.
 */
@FunctionalInterface
public interface McpProtectedResourceMetadataCustomizer {

  /**
   * Mutate the given builder before {@link OAuth2ProtectedResourceMetadata.Builder#build()} is
   * called.
   *
   * @param builder the in-progress metadata builder
   */
  void customize(OAuth2ProtectedResourceMetadata.Builder builder);
}
