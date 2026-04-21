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
package com.callibrity.mocapi.oauth2.metadata;

import com.callibrity.mocapi.oauth2.MocapiOAuth2Properties;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

/**
 * Mocapi baseline {@link McpMetadataCustomizer} that adds one or more {@code authorization_server}
 * entries to the protected-resource metadata document. Prefers the explicit {@code
 * mocapi.oauth2.authorization-servers} list; otherwise falls back to Spring Boot's JWT {@code
 * issuer-uri} so single-IdP setups don't have to restate the value. Contributes nothing when
 * neither source is configured.
 */
public final class AuthorizationServersMetadataCustomizer implements McpMetadataCustomizer {

  private final List<String> authorizationServers;

  public AuthorizationServersMetadataCustomizer(
      MocapiOAuth2Properties properties, OAuth2ResourceServerProperties resourceServerProperties) {
    if (!properties.authorizationServers().isEmpty()) {
      this.authorizationServers = List.copyOf(properties.authorizationServers());
    } else {
      String springIssuerUri = resourceServerProperties.getJwt().getIssuerUri();
      this.authorizationServers =
          StringUtils.isNotBlank(springIssuerUri) ? List.of(springIssuerUri) : List.of();
    }
  }

  @Override
  public void customize(OAuth2ProtectedResourceMetadata.Builder builder) {
    authorizationServers.forEach(builder::authorizationServer);
  }
}
