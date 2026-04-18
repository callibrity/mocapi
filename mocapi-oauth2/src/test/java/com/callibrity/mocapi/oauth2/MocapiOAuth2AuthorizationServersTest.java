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

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;

/**
 * Direct unit coverage for {@link MocapiOAuth2AutoConfiguration#authorizationServersFor}. The
 * {@code rs == null} and blank-issuer-uri branches aren't reachable from {@code @SpringBootTest}
 * contexts because {@code OAuth2ResourceServerProperties} is always on the classpath and the test
 * fixtures always set a non-blank {@code issuer-uri}. A direct call with a stubbed {@link
 * ObjectProvider} exercises the defense-in-depth fallthroughs.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2AuthorizationServersTest {

  @Test
  void explicit_property_wins_when_list_is_non_empty() {
    MocapiOAuth2Properties props = properties(List.of("https://idp.example.com"));
    List<String> result =
        MocapiOAuth2AutoConfiguration.authorizationServersFor(
            props, provider(resourceServerProperties("https://ignored.example.com")));
    assertThat(result).containsExactly("https://idp.example.com");
  }

  @Test
  void falls_back_to_spring_issuer_uri_when_property_is_empty() {
    MocapiOAuth2Properties props = properties(List.of());
    List<String> result =
        MocapiOAuth2AutoConfiguration.authorizationServersFor(
            props, provider(resourceServerProperties("https://fallback.example.com")));
    assertThat(result).containsExactly("https://fallback.example.com");
  }

  @Test
  void returns_empty_when_resource_server_properties_are_absent() {
    MocapiOAuth2Properties props = properties(List.of());
    List<String> result =
        MocapiOAuth2AutoConfiguration.authorizationServersFor(props, provider(null));
    assertThat(result).isEmpty();
  }

  @Test
  void returns_empty_when_spring_issuer_uri_is_null() {
    MocapiOAuth2Properties props = properties(List.of());
    List<String> result =
        MocapiOAuth2AutoConfiguration.authorizationServersFor(
            props, provider(resourceServerProperties(null)));
    assertThat(result).isEmpty();
  }

  @Test
  void returns_empty_when_spring_issuer_uri_is_blank() {
    MocapiOAuth2Properties props = properties(List.of());
    List<String> result =
        MocapiOAuth2AutoConfiguration.authorizationServersFor(
            props, provider(resourceServerProperties("   ")));
    assertThat(result).isEmpty();
  }

  private static MocapiOAuth2Properties properties(List<String> authorizationServers) {
    return new MocapiOAuth2Properties(
        "https://mcp.example.com",
        new java.util.ArrayList<>(authorizationServers),
        List.of(),
        null,
        null,
        null);
  }

  private static OAuth2ResourceServerProperties resourceServerProperties(String issuerUri) {
    OAuth2ResourceServerProperties rs = new OAuth2ResourceServerProperties();
    rs.getJwt().setIssuerUri(issuerUri);
    return rs;
  }

  /**
   * Stub {@link ObjectProvider} whose {@code getIfAvailable} returns a fixed value. Only that one
   * method is exercised by {@code authorizationServersFor}, so other contract methods throw.
   */
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
