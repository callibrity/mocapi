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

class NumberPropertyBuilderTest {

  @Test
  void shouldBuildMinimalNumberProperty() {
    NumberSchema schema = new NumberPropertyBuilder().description("Score").build();

    assertThat(schema.type()).isEqualTo("number");
    assertThat(schema.description()).isEqualTo("Score");
    assertThat(schema.defaultValue()).isNull();
  }

  @Test
  void shouldIncludeTitle() {
    NumberSchema schema =
        new NumberPropertyBuilder().description("Score").title("Your Score").build();

    assertThat(schema.title()).isEqualTo("Your Score");
  }

  @Test
  void shouldIncludeDefaultValue() {
    NumberSchema schema =
        new NumberPropertyBuilder().description("Score").defaultValue(95.5).build();

    assertThat(schema.defaultValue()).isEqualTo(95.5);
  }

  @Test
  void shouldIncludeMinimumAndMaximum() {
    NumberSchema schema =
        new NumberPropertyBuilder().description("Score").minimum(0.0).maximum(100.0).build();

    assertThat(schema.minimum()).isEqualTo(0.0);
    assertThat(schema.maximum()).isEqualTo(100.0);
  }

  @Test
  void shouldChainAllConstraints() {
    NumberSchema schema =
        new NumberPropertyBuilder()
            .description("Score")
            .title("Your Score")
            .defaultValue(75.0)
            .minimum(0.0)
            .maximum(100.0)
            .build();

    assertThat(schema.title()).isEqualTo("Your Score");
    assertThat(schema.defaultValue()).isEqualTo(75.0);
    assertThat(schema.minimum()).isEqualTo(0.0);
    assertThat(schema.maximum()).isEqualTo(100.0);
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    NumberPropertyBuilder builder = new NumberPropertyBuilder().description("Score").optional();

    assertThat(builder.isRequired()).isFalse();
  }

  @Test
  void defaultShouldBeRequired() {
    NumberPropertyBuilder builder = new NumberPropertyBuilder().description("Score");

    assertThat(builder.isRequired()).isTrue();
  }
}
