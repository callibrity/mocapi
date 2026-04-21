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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.oauth2.MocapiOAuth2Properties;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuthorizationServersMetadataCustomizerTest {

  @Test
  void uses_explicit_authorization_servers_when_list_is_non_empty() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            null,
            List.of("https://a.example.com", "https://b.example.com"),
            List.of(),
            null,
            null,
            null);
    OAuth2ResourceServerProperties rsProps = new OAuth2ResourceServerProperties();
    rsProps.getJwt().setIssuerUri("https://ignored.example.com");

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new AuthorizationServersMetadataCustomizer(props, rsProps).customize(builder);

    verify(builder).authorizationServer("https://a.example.com");
    verify(builder).authorizationServer("https://b.example.com");
  }

  @Test
  void falls_back_to_jwt_issuer_uri_when_explicit_list_is_empty() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(null, List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps = new OAuth2ResourceServerProperties();
    rsProps.getJwt().setIssuerUri("https://issuer.example.com");

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new AuthorizationServersMetadataCustomizer(props, rsProps).customize(builder);

    verify(builder).authorizationServer("https://issuer.example.com");
  }

  @Test
  void contributes_nothing_when_explicit_list_empty_and_issuer_uri_blank() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(null, List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps = new OAuth2ResourceServerProperties();

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new AuthorizationServersMetadataCustomizer(props, rsProps).customize(builder);

    verify(builder, never()).authorizationServer(ArgumentMatchers.anyString());
  }
}
