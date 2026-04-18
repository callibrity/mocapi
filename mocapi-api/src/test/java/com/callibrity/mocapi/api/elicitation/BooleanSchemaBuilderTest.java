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

import com.callibrity.mocapi.model.BooleanSchema;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BooleanSchemaBuilderTest {

  @Test
  void minimal_boolean_should_have_type_and_description() {
    BooleanSchema schema = new BooleanSchemaBuilder().description("Active").build();

    assertThat(schema.type()).isEqualTo("boolean");
    assertThat(schema.description()).isEqualTo("Active");
  }

  @Test
  void title_should_be_included() {
    BooleanSchema schema =
        new BooleanSchemaBuilder().description("Active").title("Is Active").build();

    assertThat(schema.title()).isEqualTo("Is Active");
  }

  @Test
  void default_true_should_be_included() {
    BooleanSchema schema =
        new BooleanSchemaBuilder().description("Active").defaultValue(true).build();

    assertThat(schema.defaultValue()).isTrue();
  }

  @Test
  void default_false_should_be_included() {
    BooleanSchema schema =
        new BooleanSchemaBuilder().description("Active").defaultValue(false).build();

    assertThat(schema.defaultValue()).isFalse();
  }

  @Test
  void constraint_chaining_should_work() {
    BooleanSchema schema =
        new BooleanSchemaBuilder()
            .description("Active")
            .title("Is Active")
            .defaultValue(true)
            .build();

    assertThat(schema.title()).isEqualTo("Is Active");
    assertThat(schema.defaultValue()).isTrue();
  }

  @Test
  void default_should_be_required() {
    BooleanSchemaBuilder builder = new BooleanSchemaBuilder();

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void optional_should_set_required_false() {
    BooleanSchemaBuilder builder = new BooleanSchemaBuilder().optional();

    assertThat(builder.isRequired()).isFalse();
  }
}
