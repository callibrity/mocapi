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

import com.callibrity.mocapi.model.NumberSchema;
import org.junit.jupiter.api.Test;

class IntegerPropertyBuilderTest {

  @Test
  void shouldBuildMinimalIntegerProperty() {
    NumberSchema schema = new IntegerPropertyBuilder().description("Age").build();

    assertThat(schema.type()).isEqualTo("integer");
    assertThat(schema.description()).isEqualTo("Age");
    assertThat(schema.defaultValue()).isNull();
  }

  @Test
  void shouldIncludeTitle() {
    NumberSchema schema = new IntegerPropertyBuilder().description("Age").title("Your Age").build();

    assertThat(schema.title()).isEqualTo("Your Age");
  }

  @Test
  void shouldIncludeDefaultValue() {
    NumberSchema schema = new IntegerPropertyBuilder().description("Age").defaultValue(30).build();

    assertThat(schema.defaultValue()).isEqualTo(30);
  }

  @Test
  void shouldIncludeMinimumAndMaximum() {
    NumberSchema schema =
        new IntegerPropertyBuilder().description("Age").minimum(0).maximum(150).build();

    assertThat(schema.minimum()).isEqualTo(0);
    assertThat(schema.maximum()).isEqualTo(150);
  }

  @Test
  void shouldChainAllConstraints() {
    NumberSchema schema =
        new IntegerPropertyBuilder()
            .description("Age")
            .title("Your Age")
            .defaultValue(25)
            .minimum(0)
            .maximum(150)
            .build();

    assertThat(schema.title()).isEqualTo("Your Age");
    assertThat(schema.defaultValue()).isEqualTo(25);
    assertThat(schema.minimum()).isEqualTo(0);
    assertThat(schema.maximum()).isEqualTo(150);
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    IntegerPropertyBuilder builder = new IntegerPropertyBuilder().description("Age").optional();

    assertThat(builder.isRequired()).isFalse();
  }

  @Test
  void defaultShouldBeRequired() {
    IntegerPropertyBuilder builder = new IntegerPropertyBuilder().description("Age");

    assertThat(builder.isRequired()).isTrue();
  }
}
