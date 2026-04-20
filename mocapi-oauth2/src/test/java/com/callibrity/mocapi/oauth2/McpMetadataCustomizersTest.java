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

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.Implementation;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpMetadataCustomizersTest {

  private static final String RESOURCE = "https://api.example.com";
  private static final String ISSUER = "https://issuer.example.com";

  @Test
  void populates_resource_and_authorization_servers_and_scopes_from_properties() {
    MocapiOAuth2Properties properties =
        new MocapiOAuth2Properties(RESOURCE, List.of(), List.of("read", "write"), null, null, null);

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of());

    assertThat(claims).containsEntry("resource", RESOURCE);
    assertThat(claims).containsEntry("authorization_servers", List.of(ISSUER));
    assertThat(claims).containsEntry("scopes_supported", List.of("read", "write"));
  }

  @Test
  void populates_resource_name_from_server_info_title() {
    MocapiOAuth2Properties properties = emptyProps();
    Implementation impl = new Implementation("my-server", "My Pretty Server", "1.0.0");

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, impl, List.of());

    assertThat(claims).containsEntry("resource_name", "My Pretty Server");
  }

  @Test
  void falls_back_to_server_info_name_when_title_blank() {
    MocapiOAuth2Properties properties = emptyProps();
    Implementation impl = new Implementation("my-server", "", "1.0.0");

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, impl, List.of());

    assertThat(claims).containsEntry("resource_name", "my-server");
  }

  @Test
  void omits_resource_name_when_server_info_is_null() {
    MocapiOAuth2Properties properties = emptyProps();

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of());

    assertThat(claims).doesNotContainKey("resource_name");
  }

  @Test
  void adds_optional_rfc9728_claims_when_set() {
    MocapiOAuth2Properties properties =
        new MocapiOAuth2Properties(
            RESOURCE,
            List.of(),
            List.of(),
            "https://docs.example.com",
            "https://policy.example.com",
            "https://tos.example.com");

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of());

    assertThat(claims)
        .containsEntry("resource_documentation", "https://docs.example.com")
        .containsEntry("resource_policy_uri", "https://policy.example.com")
        .containsEntry("resource_tos_uri", "https://tos.example.com");
  }

  @Test
  void omits_optional_rfc9728_claims_when_unset() {
    MocapiOAuth2Properties properties = emptyProps();

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of());

    assertThat(claims)
        .doesNotContainKey("resource_documentation")
        .doesNotContainKey("resource_policy_uri")
        .doesNotContainKey("resource_tos_uri");
  }

  @Test
  void user_supplied_customizers_run_after_defaults() {
    MocapiOAuth2Properties properties = emptyProps();
    OAuth2ProtectedResourceMetadataCustomizer userCustomizer =
        builder -> builder.claim("custom_claim", "value");

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of(userCustomizer));

    assertThat(claims).containsEntry("custom_claim", "value");
  }

  @Test
  void user_customizers_can_override_mocapi_defaults() {
    MocapiOAuth2Properties properties = emptyProps();
    OAuth2ProtectedResourceMetadataCustomizer userCustomizer =
        builder -> builder.resource("https://override.example.com");

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of(userCustomizer));

    assertThat(claims).containsEntry("resource", "https://override.example.com");
  }

  @Test
  void uses_explicit_authorization_servers_when_configured() {
    MocapiOAuth2Properties properties =
        new MocapiOAuth2Properties(
            RESOURCE,
            List.of("https://auth1.example.com", "https://auth2.example.com"),
            List.of(),
            null,
            null,
            null);

    Map<String, Object> claims =
        applyAndBuild(properties, List.of(RESOURCE), ISSUER, null, List.of());

    assertThat(claims)
        .containsEntry(
            "authorization_servers",
            List.of("https://auth1.example.com", "https://auth2.example.com"));
  }

  private static MocapiOAuth2Properties emptyProps() {
    return new MocapiOAuth2Properties(RESOURCE, List.of(), List.of(), null, null, null);
  }

  private static Map<String, Object> applyAndBuild(
      MocapiOAuth2Properties properties,
      List<String> audiences,
      String springIssuerUri,
      Implementation impl,
      List<OAuth2ProtectedResourceMetadataCustomizer> userCustomizers) {
    Consumer<OAuth2ProtectedResourceMetadata.Builder> customizer =
        McpMetadataCustomizers.of(properties, audiences, springIssuerUri, impl, userCustomizers);
    OAuth2ProtectedResourceMetadata.Builder builder = OAuth2ProtectedResourceMetadata.builder();
    customizer.accept(builder);
    return builder.build().getClaims();
  }
}
