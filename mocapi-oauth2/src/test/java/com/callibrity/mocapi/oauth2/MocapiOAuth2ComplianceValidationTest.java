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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Direct unit coverage for {@link MocapiOAuth2AutoConfiguration#validateComplianceMode}. Two
 * branches aren't reachable from the Spring-context validation tests:
 *
 * <ul>
 *   <li>{@code rs == null} ternary — {@code OAuth2ResourceServerProperties} is always on the
 *       classpath when this module is being tested.
 *   <li>{@code audiences == null} left-hand {@code ||} — Spring Boot's property binder initializes
 *       the list rather than leaving it null.
 * </ul>
 *
 * A direct call with a stubbed {@link ObjectProvider} drives both paths.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2ComplianceValidationTest {

  @Test
  void passes_when_decoder_present_and_audiences_configured() {
    assertThatCode(
            () ->
                MocapiOAuth2AutoConfiguration.validateComplianceMode(
                    provider(mock(JwtDecoder.class)),
                    provider(resourceServerProperties(List.of("mcp-test")))))
        .doesNotThrowAnyException();
  }

  @Test
  void fails_when_jwt_decoder_is_missing() {
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.validateComplianceMode(
                    provider(null), provider(resourceServerProperties(List.of("mcp-test")))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no JwtDecoder bean");
  }

  @Test
  void fails_when_resource_server_properties_bean_is_absent() {
    // Defensive branch: OAuth2ResourceServerProperties would normally be on the classpath, but
    // the method explicitly handles the case. The null ObjectProvider short-circuits to an
    // empty audiences list, which trips the audience-required check.
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.validateComplianceMode(
                    provider(mock(JwtDecoder.class)), provider(null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audiences");
  }

  @Test
  void fails_when_audiences_list_is_null() {
    // Another defensive branch: Spring Boot's binder normally initializes the list, but if a
    // custom OAuth2ResourceServerProperties ever leaves it null the check still catches it.
    OAuth2ResourceServerProperties rs = new OAuth2ResourceServerProperties();
    rs.getJwt().setAudiences(null);
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.validateComplianceMode(
                    provider(mock(JwtDecoder.class)), provider(rs)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audiences");
  }

  @Test
  void fails_when_audiences_list_is_empty() {
    assertThatThrownBy(
            () ->
                MocapiOAuth2AutoConfiguration.validateComplianceMode(
                    provider(mock(JwtDecoder.class)),
                    provider(resourceServerProperties(List.of()))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("audiences");
  }

  private static OAuth2ResourceServerProperties resourceServerProperties(List<String> audiences) {
    OAuth2ResourceServerProperties rs = new OAuth2ResourceServerProperties();
    rs.getJwt().setAudiences(new java.util.ArrayList<>(audiences));
    return rs;
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
