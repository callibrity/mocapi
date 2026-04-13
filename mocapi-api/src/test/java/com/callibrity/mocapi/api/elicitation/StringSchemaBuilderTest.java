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
import org.junit.jupiter.api.Test;

class StringSchemaBuilderTest {

  @Test
  void minimalStringShouldHaveTypeAndDescription() {
    StringSchema schema = new StringSchemaBuilder().description("Email").build();

    assertThat(schema.type()).isEqualTo("string");
    assertThat(schema.description()).isEqualTo("Email");
  }

  @Test
  void titleShouldBeIncluded() {
    StringSchema schema =
        new StringSchemaBuilder().description("Email").title("Your Email").build();

    assertThat(schema.title()).isEqualTo("Your Email");
  }

  @Test
  void defaultValueShouldBeIncluded() {
    StringSchema schema =
        new StringSchemaBuilder().description("Name").defaultValue("Alice").build();

    assertThat(schema.defaultValue()).isEqualTo("Alice");
  }

  @Test
  void minLengthAndMaxLengthShouldBeIncluded() {
    StringSchema schema =
        new StringSchemaBuilder().description("Code").minLength(3).maxLength(10).build();

    assertThat(schema.minLength()).isEqualTo(3);
    assertThat(schema.maxLength()).isEqualTo(10);
  }

  @Test
  void emailShorthandShouldSetFormat() {
    StringSchema schema = new StringSchemaBuilder().description("Email").email().build();

    assertThat(schema.format().toJson()).isEqualTo("email");
  }

  @Test
  void uriShorthandShouldSetFormat() {
    StringSchema schema = new StringSchemaBuilder().description("URL").uri().build();

    assertThat(schema.format().toJson()).isEqualTo("uri");
  }

  @Test
  void constraintChainingShouldWork() {
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
  void defaultShouldBeRequired() {
    StringSchemaBuilder builder = new StringSchemaBuilder();

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    StringSchemaBuilder builder = new StringSchemaBuilder().optional();

    assertThat(builder.isRequired()).isFalse();
  }
}
