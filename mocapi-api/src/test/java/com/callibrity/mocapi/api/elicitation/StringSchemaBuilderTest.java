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

import com.callibrity.mocapi.model.StringSchema;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StringSchemaBuilderTest {

  @Test
  void minimal_string_should_have_type_and_description() {
    StringSchema schema = new StringSchemaBuilder().description("Email").build();

    assertThat(schema.type()).isEqualTo("string");
    assertThat(schema.description()).isEqualTo("Email");
  }

  @Test
  void title_should_be_included() {
    StringSchema schema =
        new StringSchemaBuilder().description("Email").title("Your Email").build();

    assertThat(schema.title()).isEqualTo("Your Email");
  }

  @Test
  void default_value_should_be_included() {
    StringSchema schema =
        new StringSchemaBuilder().description("Name").defaultValue("Alice").build();

    assertThat(schema.defaultValue()).isEqualTo("Alice");
  }

  @Test
  void min_length_and_max_length_should_be_included() {
    StringSchema schema =
        new StringSchemaBuilder().description("Code").minLength(3).maxLength(10).build();

    assertThat(schema.minLength()).isEqualTo(3);
    assertThat(schema.maxLength()).isEqualTo(10);
  }

  @Test
  void email_shorthand_should_set_format() {
    StringSchema schema = new StringSchemaBuilder().description("Email").email().build();

    assertThat(schema.format().toJson()).isEqualTo("email");
  }

  @Test
  void uri_shorthand_should_set_format() {
    StringSchema schema = new StringSchemaBuilder().description("URL").uri().build();

    assertThat(schema.format().toJson()).isEqualTo("uri");
  }

  @Test
  void constraint_chaining_should_work() {
    StringSchema schema =
        new StringSchemaBuilder()
            .description("Code")
            .title("Zip")
            .minLength(5)
            .maxLength(5)
            .defaultValue("00000")
            .build();

    assertThat(schema.title()).isEqualTo("Zip");
    assertThat(schema.minLength()).isEqualTo(5);
    assertThat(schema.maxLength()).isEqualTo(5);
    assertThat(schema.defaultValue()).isEqualTo("00000");
  }

  @Test
  void default_should_be_required() {
    StringSchemaBuilder builder = new StringSchemaBuilder();

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void optional_should_set_required_false() {
    StringSchemaBuilder builder = new StringSchemaBuilder().optional();

    assertThat(builder.isRequired()).isFalse();
  }
}
