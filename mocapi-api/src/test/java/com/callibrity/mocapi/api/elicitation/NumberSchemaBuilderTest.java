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
package com.callibrity.mocapi.api.elicitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.model.NumberSchema;
import org.junit.jupiter.api.Test;

class NumberSchemaBuilderTest {

  @Test
  void minimalNumberShouldHaveTypeAndDescription() {
    NumberSchema schema = new NumberSchemaBuilder().description("Score").build();

    assertThat(schema.type()).isEqualTo("number");
    assertThat(schema.description()).isEqualTo("Score");
  }

  @Test
  void titleShouldBeIncluded() {
    NumberSchema schema =
        new NumberSchemaBuilder().description("Score").title("Test Score").build();

    assertThat(schema.title()).isEqualTo("Test Score");
  }

  @Test
  void defaultValueShouldBeIncluded() {
    NumberSchema schema = new NumberSchemaBuilder().description("Score").defaultValue(95.5).build();

    assertThat(schema.defaultValue()).isEqualTo(95.5);
  }

  @Test
  void minimumShouldBeIncluded() {
    NumberSchema schema = new NumberSchemaBuilder().description("Score").minimum(0.0).build();

    assertThat(schema.minimum()).isEqualTo(0.0);
  }

  @Test
  void maximumShouldBeIncluded() {
    NumberSchema schema = new NumberSchemaBuilder().description("Score").maximum(100.0).build();

    assertThat(schema.maximum()).isEqualTo(100.0);
  }

  @Test
  void constraintChainingShouldWork() {
    NumberSchema schema =
        new NumberSchemaBuilder()
            .description("Score")
            .title("Test Score")
            .minimum(0.0)
            .maximum(100.0)
            .defaultValue(50.0)
            .build();

    assertThat(schema.title()).isEqualTo("Test Score");
    assertThat(schema.minimum()).isEqualTo(0.0);
    assertThat(schema.maximum()).isEqualTo(100.0);
    assertThat(schema.defaultValue()).isEqualTo(50.0);
  }

  @Test
  void defaultShouldBeRequired() {
    NumberSchemaBuilder builder = new NumberSchemaBuilder();

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    NumberSchemaBuilder builder = new NumberSchemaBuilder().optional();

    assertThat(builder.isRequired()).isFalse();
  }
}
