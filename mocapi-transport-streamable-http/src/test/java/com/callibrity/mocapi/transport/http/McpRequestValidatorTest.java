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
package com.callibrity.mocapi.transport.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class McpRequestValidatorTest {

  private final McpRequestValidator validator =
      new McpRequestValidator(List.of("localhost", "example.com"));

  @Test
  void nullOriginIsValid() {
    assertThat(validator.isValidOrigin(null)).isTrue();
  }

  @Test
  void allowedOriginIsValid() {
    assertThat(validator.isValidOrigin("http://localhost")).isTrue();
  }

  @Test
  void allowedOriginWithPortIsValid() {
    assertThat(validator.isValidOrigin("http://localhost:8080")).isTrue();
  }

  @Test
  void allowedOriginWithHttpsIsValid() {
    assertThat(validator.isValidOrigin("https://example.com")).isTrue();
  }

  @Test
  void disallowedOriginIsInvalid() {
    assertThat(validator.isValidOrigin("http://evil.com")).isFalse();
  }

  @Test
  void malformedOriginIsInvalid() {
    assertThat(validator.isValidOrigin("not a valid uri!@#$")).isFalse();
  }

  @Test
  void emptyOriginIsInvalid() {
    assertThat(validator.isValidOrigin("")).isFalse();
  }

  @Test
  void originWithNoHostIsInvalid() {
    assertThat(validator.isValidOrigin("file:///etc/passwd")).isFalse();
  }
}
