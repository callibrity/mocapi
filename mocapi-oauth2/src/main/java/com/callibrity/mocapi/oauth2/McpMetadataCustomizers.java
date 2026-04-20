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

import com.callibrity.mocapi.model.Implementation;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Builds the {@link Consumer} that populates Spring Security's {@link
 * OAuth2ProtectedResourceMetadata.Builder} with mocapi's defaults (resolved resource,
 * authorization-servers fallback, scopes, resource-name from {@link Implementation}, optional RFC
 * 9728 claims) and then runs user-supplied {@link OAuth2ProtectedResourceMetadataCustomizer} beans
 * so they can override or extend the document.
 */
public final class McpMetadataCustomizers {

  private McpMetadataCustomizers() {}

  /**
   * @param properties mocapi-oauth2 configuration
   * @param audiences the {@code spring.security.oauth2.resourceserver.jwt.audiences} values; never
   *     {@code null}
   * @param springIssuerUri the value of {@code
   *     spring.security.oauth2.resourceserver.jwt.issuer-uri}; {@code null} or blank when unset
   * @param mcpServerInfo the MCP server-info bean used to derive {@code resource_name}; may be
   *     {@code null}
   * @param userCustomizers application-supplied customizers run after mocapi's defaults
   */
  public static Consumer<OAuth2ProtectedResourceMetadata.Builder> of(
      MocapiOAuth2Properties properties,
      List<String> audiences,
      String springIssuerUri,
      Implementation mcpServerInfo,
      List<OAuth2ProtectedResourceMetadataCustomizer> userCustomizers) {
    String resolvedResource = MocapiOAuth2ResourceResolver.resolve(properties, audiences);
    List<String> authorizationServers =
        MocapiOAuth2ResourceResolver.authorizationServers(properties, springIssuerUri);
    return builder -> {
      builder.resource(resolvedResource);
      authorizationServers.forEach(builder::authorizationServer);
      properties.scopes().forEach(builder::scope);
      // bearer_methods_supported is defaulted to ["header"] by Spring Security's own default
      // customizer, so we don't add it here — doing so would duplicate the entry.
      MocapiOAuth2ResourceResolver.resourceName(mcpServerInfo).ifPresent(builder::resourceName);
      if (properties.resourceDocumentation() != null) {
        builder.claim("resource_documentation", properties.resourceDocumentation());
      }
      if (properties.resourcePolicyUri() != null) {
        builder.claim("resource_policy_uri", properties.resourcePolicyUri());
      }
      if (properties.resourceTosUri() != null) {
        builder.claim("resource_tos_uri", properties.resourceTosUri());
      }
      userCustomizers.forEach(c -> c.customize(builder));
    };
  }
}
