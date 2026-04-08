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
package com.callibrity.mocapi.autoconfigure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class McpRequestValidatorTest {

  private McpRequestValidator validator;

  @BeforeEach
  void setUp() {
    validator = new McpRequestValidator(List.of("localhost", "example.com"));
  }

  @Nested
  class ProtocolVersion {

    @Test
    void shouldAcceptCurrentVersion() {
      assertThat(validator.isValidProtocolVersion("2025-11-25")).isTrue();
    }

    @Test
    void shouldAcceptOlderVersions() {
      assertThat(validator.isValidProtocolVersion("2025-06-18")).isTrue();
      assertThat(validator.isValidProtocolVersion("2025-03-26")).isTrue();
      assertThat(validator.isValidProtocolVersion("2024-11-05")).isTrue();
    }

    @Test
    void shouldRejectUnknownVersion() {
      assertThat(validator.isValidProtocolVersion("1999-01-01")).isFalse();
    }

    @Test
    void shouldDefaultToCurrentVersionWhenNull() {
      assertThat(validator.isValidProtocolVersion(null)).isTrue();
    }
  }

  @Nested
  class OriginValidation {

    @Test
    void shouldAcceptNullOrigin() {
      assertThat(validator.isValidOrigin(null)).isTrue();
    }

    @Test
    void shouldAcceptAllowedOrigin() {
      assertThat(validator.isValidOrigin("http://localhost:8080")).isTrue();
    }

    @Test
    void shouldRejectDisallowedOrigin() {
      assertThat(validator.isValidOrigin("http://evil.example.org")).isFalse();
    }

    @Test
    void shouldRejectMalformedOrigin() {
      assertThat(validator.isValidOrigin("not a valid uri {{{")).isFalse();
    }
  }
}
