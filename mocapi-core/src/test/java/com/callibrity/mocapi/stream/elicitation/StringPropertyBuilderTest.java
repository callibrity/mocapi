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
package com.callibrity.mocapi.stream.elicitation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringPropertyBuilderTest {

  @Test
  void shouldBuildMinimalStringProperty() {
    StringPropertySchema schema = new StringPropertyBuilder().description("A description").build();

    assertThat(schema.type()).isEqualTo("string");
    assertThat(schema.description()).isEqualTo("A description");
    assertThat(schema.title()).isNull();
    assertThat(schema.defaultValue()).isNull();
    assertThat(schema.required()).isTrue();
  }

  @Test
  void shouldIncludeTitle() {
    StringPropertySchema schema =
        new StringPropertyBuilder().description("desc").title("Full Name").build();

    assertThat(schema.title()).isEqualTo("Full Name");
  }

  @Test
  void shouldIncludeDefaultValue() {
    StringPropertySchema schema =
        new StringPropertyBuilder().description("desc").defaultValue("hello").build();

    assertThat(schema.defaultValue()).isEqualTo("hello");
  }

  @Test
  void shouldIncludeMinAndMaxLength() {
    StringPropertySchema schema =
        new StringPropertyBuilder().description("desc").minLength(1).maxLength(255).build();

    assertThat(schema.minLength()).isEqualTo(1);
    assertThat(schema.maxLength()).isEqualTo(255);
  }

  @Test
  void shouldIncludePattern() {
    StringPropertySchema schema =
        new StringPropertyBuilder().description("desc").pattern("^[a-z]+$").build();

    assertThat(schema.pattern()).isEqualTo("^[a-z]+$");
  }

  @Test
  void shouldSetEmailFormat() {
    StringPropertySchema schema = new StringPropertyBuilder().description("desc").email().build();

    assertThat(schema.format()).isEqualTo("email");
  }

  @Test
  void shouldSetUriFormat() {
    StringPropertySchema schema = new StringPropertyBuilder().description("desc").uri().build();

    assertThat(schema.format()).isEqualTo("uri");
  }

  @Test
  void shouldSetDateFormat() {
    StringPropertySchema schema = new StringPropertyBuilder().description("desc").date().build();

    assertThat(schema.format()).isEqualTo("date");
  }

  @Test
  void shouldSetDateTimeFormat() {
    StringPropertySchema schema =
        new StringPropertyBuilder().description("desc").dateTime().build();

    assertThat(schema.format()).isEqualTo("date-time");
  }

  @Test
  void shouldChainMultipleConstraints() {
    StringPropertySchema schema =
        new StringPropertyBuilder()
            .description("desc")
            .title("ZIP")
            .pattern("^\\d{5}$")
            .maxLength(5)
            .defaultValue("12345")
            .build();

    assertThat(schema.title()).isEqualTo("ZIP");
    assertThat(schema.pattern()).isEqualTo("^\\d{5}$");
    assertThat(schema.maxLength()).isEqualTo(5);
    assertThat(schema.defaultValue()).isEqualTo("12345");
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    StringPropertySchema schema =
        new StringPropertyBuilder().description("Nickname").optional().build();

    assertThat(schema.required()).isFalse();
  }
}
