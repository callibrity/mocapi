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

/** Direct unit coverage for {@link MocapiOAuth2ResourceResolver#authorizationServers}. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2AuthorizationServersTest {

  @Test
  void explicit_property_wins_when_list_is_non_empty() {
    List<String> result =
        MocapiOAuth2ResourceResolver.authorizationServers(
            properties(List.of("https://idp.example.com")), "https://ignored.example.com");
    assertThat(result).containsExactly("https://idp.example.com");
  }

  @Test
  void falls_back_to_spring_issuer_uri_when_property_is_empty() {
    List<String> result =
        MocapiOAuth2ResourceResolver.authorizationServers(
            properties(List.of()), "https://fallback.example.com");
    assertThat(result).containsExactly("https://fallback.example.com");
  }

  @Test
  void returns_empty_when_spring_issuer_uri_is_null() {
    List<String> result =
        MocapiOAuth2ResourceResolver.authorizationServers(properties(List.of()), null);
    assertThat(result).isEmpty();
  }

  @Test
  void returns_empty_when_spring_issuer_uri_is_blank() {
    List<String> result =
        MocapiOAuth2ResourceResolver.authorizationServers(properties(List.of()), "   ");
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
}
