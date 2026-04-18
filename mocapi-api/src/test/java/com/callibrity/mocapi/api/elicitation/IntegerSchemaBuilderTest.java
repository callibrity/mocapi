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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class IntegerSchemaBuilderTest {

  @Test
  void minimal_integer_should_have_type_and_description() {
    NumberSchema schema = new IntegerSchemaBuilder().description("Age").build();

    assertThat(schema.type()).isEqualTo("integer");
    assertThat(schema.description()).isEqualTo("Age");
  }

  @Test
  void title_should_be_included() {
    NumberSchema schema = new IntegerSchemaBuilder().description("Age").title("Your Age").build();

    assertThat(schema.title()).isEqualTo("Your Age");
  }

  @Test
  void default_value_should_be_included() {
    NumberSchema schema = new IntegerSchemaBuilder().description("Age").defaultValue(25).build();

    assertThat(schema.defaultValue()).isEqualTo(25);
  }

  @Test
  void minimum_should_be_included() {
    NumberSchema schema = new IntegerSchemaBuilder().description("Age").minimum(0).build();

    assertThat(schema.minimum()).isEqualTo(0);
  }

  @Test
  void maximum_should_be_included() {
    NumberSchema schema = new IntegerSchemaBuilder().description("Age").maximum(150).build();

    assertThat(schema.maximum()).isEqualTo(150);
  }

  @Test
  void constraint_chaining_should_work() {
    NumberSchema schema =
        new IntegerSchemaBuilder()
            .description("Age")
            .title("Your Age")
            .minimum(0)
            .maximum(150)
            .defaultValue(30)
            .build();

    assertThat(schema.title()).isEqualTo("Your Age");
    assertThat(schema.minimum()).isEqualTo(0);
    assertThat(schema.maximum()).isEqualTo(150);
    assertThat(schema.defaultValue()).isEqualTo(30);
  }

  @Test
  void default_should_be_required() {
    IntegerSchemaBuilder builder = new IntegerSchemaBuilder();

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void optional_should_set_required_false() {
    IntegerSchemaBuilder builder = new IntegerSchemaBuilder().optional();

    assertThat(builder.isRequired()).isFalse();
  }
}
