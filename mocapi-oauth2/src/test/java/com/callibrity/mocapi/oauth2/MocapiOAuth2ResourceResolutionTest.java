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

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link MocapiOAuth2ResourceResolver#resolve}. The resolver enforces the
 * invariant that the {@code resource} value mocapi publishes in its RFC 9728 metadata document is a
 * member of the configured {@code audiences} list — otherwise clients following the metadata would
 * get tokens the server rejects during validation.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MocapiOAuth2ResourceResolutionTest {

  @Test
  void explicit_resource_in_audiences_is_used_as_is() {
    String resolved =
        MocapiOAuth2ResourceResolver.resolve(
            properties("https://api.example.com"),
            List.of("https://api.example.com", "https://api-legacy.example.com"));

    assertThat(resolved).isEqualTo("https://api.example.com");
  }

  @Test
  void explicit_resource_not_in_audiences_fails_with_mismatch_error() {
    MocapiOAuth2Properties props = properties("https://other.example.com");
    List<String> audiences = List.of("https://api.example.com");
    assertThatThrownBy(() -> MocapiOAuth2ResourceResolver.resolve(props, audiences))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not a member of")
        .hasMessageContaining("https://other.example.com");
  }

  @Test
  void single_audience_auto_derives_resource_when_unset() {
    String resolved =
        MocapiOAuth2ResourceResolver.resolve(properties(null), List.of("https://api.example.com"));

    assertThat(resolved).isEqualTo("https://api.example.com");
  }

  @Test
  void blank_resource_is_treated_as_unset_and_auto_derives() {
    String resolved =
        MocapiOAuth2ResourceResolver.resolve(properties("   "), List.of("https://api.example.com"));

    assertThat(resolved).isEqualTo("https://api.example.com");
  }

  @Test
  void multiple_audiences_without_explicit_resource_fails_as_ambiguous() {
    MocapiOAuth2Properties props = properties(null);
    List<String> audiences = List.of("https://api-a.example.com", "https://api-b.example.com");
    assertThatThrownBy(() -> MocapiOAuth2ResourceResolver.resolve(props, audiences))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("is not set and cannot be derived")
        .hasMessageContaining("2 entries");
  }

  @Test
  void empty_audiences_with_unset_resource_fails() {
    MocapiOAuth2Properties props = properties(null);
    List<String> audiences = List.of();
    assertThatThrownBy(() -> MocapiOAuth2ResourceResolver.resolve(props, audiences))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("0 entries");
  }

  private static MocapiOAuth2Properties properties(String resource) {
    return new MocapiOAuth2Properties(resource, List.of(), List.of(), null, null, null);
  }
}
