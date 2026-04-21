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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.oauth2.MocapiOAuth2Properties;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ResourceMetadataCustomizerTest {

  @Test
  void uses_explicit_resource_when_contained_in_audiences() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            "https://api.example.com", List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps =
        rsPropsWithAudiences("https://api.example.com", "other");

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ResourceMetadataCustomizer(props, rsProps).customize(builder);

    verify(builder).resource("https://api.example.com");
  }

  @Test
  void throws_when_explicit_resource_not_in_audiences() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(
            "https://api.example.com", List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps = rsPropsWithAudiences("https://other.example.com");

    assertThatThrownBy(() -> new ResourceMetadataCustomizer(props, rsProps))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not a member of");
  }

  @Test
  void defaults_to_sole_audience_when_resource_blank() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties("", List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps = rsPropsWithAudiences("https://api.example.com");

    OAuth2ProtectedResourceMetadata.Builder builder =
        mock(OAuth2ProtectedResourceMetadata.Builder.class);
    new ResourceMetadataCustomizer(props, rsProps).customize(builder);

    verify(builder).resource("https://api.example.com");
  }

  @Test
  void throws_when_resource_blank_and_no_audiences() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(null, List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps = new OAuth2ResourceServerProperties();

    assertThatThrownBy(() -> new ResourceMetadataCustomizer(props, rsProps))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be derived");
  }

  @Test
  void throws_when_resource_blank_and_multiple_audiences() {
    MocapiOAuth2Properties props =
        new MocapiOAuth2Properties(null, List.of(), List.of(), null, null, null);
    OAuth2ResourceServerProperties rsProps =
        rsPropsWithAudiences("https://a.example.com", "https://b.example.com");

    assertThatThrownBy(() -> new ResourceMetadataCustomizer(props, rsProps))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("cannot be derived");
  }

  private static OAuth2ResourceServerProperties rsPropsWithAudiences(String... audiences) {
    OAuth2ResourceServerProperties rs = new OAuth2ResourceServerProperties();
    rs.getJwt().setAudiences(List.of(audiences));
    return rs;
  }
}
