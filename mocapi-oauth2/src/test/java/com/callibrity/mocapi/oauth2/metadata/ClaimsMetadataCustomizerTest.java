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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.oauth2.MocapiOAuth2Properties;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ClaimsMetadataCustomizerTest {

  @Test
  void adds_all_three_claims_when_all_fields_populated() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            null,
            List.of(),
            List.of(),
            "https://docs.example.com",
            "https://policy.example.com",
            "https://tos.example.com");

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ClaimsMetadataCustomizer(props).customize(builder);

    verify(builder).claim("resource_documentation", "https://docs.example.com");
    verify(builder).claim("resource_policy_uri", "https://policy.example.com");
    verify(builder).claim("resource_tos_uri", "https://tos.example.com");
  }

  @Test
  void adds_nothing_when_all_fields_null() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(null, List.of(), List.of(), null, null, null);

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ClaimsMetadataCustomizer(props).customize(builder);

    verify(builder, never()).claim(anyString(), ArgumentMatchers.any());
  }

  @Test
  void adds_only_documentation_when_policy_and_tos_null() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            null, List.of(), List.of(), "https://docs.example.com", null, null);

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ClaimsMetadataCustomizer(props).customize(builder);

    verify(builder).claim("resource_documentation", "https://docs.example.com");
    verify(builder, never())
        .claim(ArgumentMatchers.eq("resource_policy_uri"), ArgumentMatchers.any());
    verify(builder, never()).claim(ArgumentMatchers.eq("resource_tos_uri"), ArgumentMatchers.any());
  }

  @Test
  void adds_only_policy_when_documentation_and_tos_null() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            null, List.of(), List.of(), null, "https://policy.example.com", null);

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ClaimsMetadataCustomizer(props).customize(builder);

    verify(builder).claim("resource_policy_uri", "https://policy.example.com");
    verify(builder, never())
        .claim(ArgumentMatchers.eq("resource_documentation"), ArgumentMatchers.any());
    verify(builder, never()).claim(ArgumentMatchers.eq("resource_tos_uri"), ArgumentMatchers.any());
  }

  @Test
  void adds_only_tos_when_documentation_and_policy_null() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            null, List.of(), List.of(), null, null, "https://tos.example.com");

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ClaimsMetadataCustomizer(props).customize(builder);

    verify(builder).claim("resource_tos_uri", "https://tos.example.com");
    verify(builder, never())
        .claim(ArgumentMatchers.eq("resource_documentation"), ArgumentMatchers.any());
    verify(builder, never())
        .claim(ArgumentMatchers.eq("resource_policy_uri"), ArgumentMatchers.any());
  }
}
