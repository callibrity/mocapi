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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;

/**
 * Unit coverage for {@link MocapiOAuth2AutoConfiguration#resolveResource}. The resolver enforces
 * the invariant that the {@code resource} value mocapi publishes in its RFC 9728 metadata document
 * is a member of the configured {@code audiences} list — otherwise clients following the metadata
 * would get tokens the server rejects during validation.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2ResourceResolutionTest {

  @Test
  void explicit_resource_in_audiences_is_used_as_is() {
    String resolved =
        MocapiOAuth2AutoConfiguration.resolveResource(
            properties("https://api.example.com"),
            rsProps(List.of("https://api.example.com", "https://api-legacy.example.com")));

    assertThat(resolved).isEqualTo("https://api.example.com");
  }

  @Test
  void explicit_resource_not_in_audiences_fails_with_mismatch_error() {
    // Clients following the metadata would get tokens with aud=https://other but the server's
    // audience validator only accepts "https://api.example.com" — silent incompatibility. The
    // resolver catches that at startup.
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.resolveResource(
                    properties("https://other.example.com"),
                    rsProps(List.of("https://api.example.com"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not a member of")
        .hasMessageContaining("https://other.example.com");
  }

  @Test
  void single_audience_auto_derives_resource_when_unset() {
    // Common case for simple setups (one Auth0 API, one Okta AS, one Keycloak client).
    String resolved =
        MocapiOAuth2AutoConfiguration.resolveResource(
            properties(null), rsProps(List.of("https://api.example.com")));

    assertThat(resolved).isEqualTo("https://api.example.com");
  }

  @Test
  void blank_resource_is_treated_as_unset_and_auto_derives() {
    // Spring property binding turns empty strings into "" not null when the property is present
    // but has no value. StringUtils.isNotBlank treats both as unset — make sure the resolver
    // agrees.
    String resolved =
        MocapiOAuth2AutoConfiguration.resolveResource(
            properties("   "), rsProps(List.of("https://api.example.com")));

    assertThat(resolved).isEqualTo("https://api.example.com");
  }

  @Test
  void multiple_audiences_without_explicit_resource_fails_as_ambiguous() {
    // Can't guess which audience to publish as the canonical resource. Asking the user to pick
    // is better than publishing the wrong one and breaking clients silently.
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.resolveResource(
                    properties(null),
                    rsProps(List.of("https://api-a.example.com", "https://api-b.example.com"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not set and cannot be derived")
        .hasMessageContaining("2 entries");
  }

  @Test
  void empty_audiences_with_unset_resource_fails() {
    // This case is normally already rejected by validateComplianceMode with a more specific
    // "audiences is empty" message, but resolveResource is also reachable in isolation so it
    // has to refuse too.
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.resolveResource(properties(null), rsProps(List.of())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("0 entries");
  }

  @Test
  void null_resource_server_properties_bean_is_handled_as_empty_audiences() {
    // Defensive path: OAuth2ResourceServerProperties is always on the classpath in this module,
    // but resolveResource guards against the null provider anyway.
    assertThatThrownBy(
            () -> MocapiOAuth2AutoConfiguration.resolveResource(properties(null), provider(null)))
        .isInstanceOf(IllegalStateException.class);
  }

  // --- fixtures ---------------------------------------------------------

  private static MocapiOAuth2Properties properties(String resource) {
    return new MocapiOAuth2Properties(resource, List.of(), List.of(), null, null, null);
  }

  private static ObjectProvider<OAuth2ResourceServerProperties> rsProps(List<String> audiences) {
    OAuth2ResourceServerProperties rs = new OAuth2ResourceServerProperties();
    rs.getJwt().setAudiences(new ArrayList<>(audiences));
    return provider(rs);
  }

  private static <T> ObjectProvider<T> provider(T value) {
    return new ObjectProvider<>() {
      @Override
      public T getIfAvailable() {
        return value;
      }

      @Override
      public T getObject() {
        throw new UnsupportedOperationException();
      }

      @Override
      public T getObject(Object... args) {
        throw new UnsupportedOperationException();
      }

      @Override
      public T getIfUnique() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
