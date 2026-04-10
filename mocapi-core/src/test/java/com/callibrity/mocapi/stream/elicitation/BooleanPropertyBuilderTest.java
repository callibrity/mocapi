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

class BooleanPropertyBuilderTest {

  @Test
  void shouldBuildMinimalBooleanProperty() {
    BooleanPropertySchema schema = new BooleanPropertyBuilder().description("Is active").build();

    assertThat(schema.type()).isEqualTo("boolean");
    assertThat(schema.description()).isEqualTo("Is active");
    assertThat(schema.defaultValue()).isNull();
    assertThat(schema.required()).isTrue();
  }

  @Test
  void shouldIncludeTitle() {
    BooleanPropertySchema schema =
        new BooleanPropertyBuilder().description("Is active").title("Active Status").build();

    assertThat(schema.title()).isEqualTo("Active Status");
  }

  @Test
  void shouldIncludeDefaultValueTrue() {
    BooleanPropertySchema schema =
        new BooleanPropertyBuilder().description("Is active").defaultValue(true).build();

    assertThat(schema.defaultValue()).isTrue();
  }

  @Test
  void shouldIncludeDefaultValueFalse() {
    BooleanPropertySchema schema =
        new BooleanPropertyBuilder().description("Is active").defaultValue(false).build();

    assertThat(schema.defaultValue()).isFalse();
  }

  @Test
  void shouldChainTitleAndDefault() {
    BooleanPropertySchema schema =
        new BooleanPropertyBuilder()
            .description("Is active")
            .title("Active Status")
            .defaultValue(true)
            .build();

    assertThat(schema.title()).isEqualTo("Active Status");
    assertThat(schema.defaultValue()).isTrue();
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    BooleanPropertySchema schema =
        new BooleanPropertyBuilder().description("Is active").optional().build();

    assertThat(schema.required()).isFalse();
  }
}
