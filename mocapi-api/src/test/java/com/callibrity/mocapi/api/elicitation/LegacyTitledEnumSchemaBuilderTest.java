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

import com.callibrity.mocapi.model.LegacyTitledEnumSchema;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SuppressWarnings(
    "deprecation") // Tests deliberately exercise deprecated LegacyTitledEnumSchemaBuilder per MCP
// spec backward compatibility
class LegacyTitledEnumSchemaBuilderTest {

  @Test
  void should_produce_enum_with_enum_names() {
    LegacyTitledEnumSchema schema =
        new LegacyTitledEnumSchemaBuilder(
                List.of("opt1", "opt2", "opt3"),
                List.of("Option One", "Option Two", "Option Three"))
            .build();

    assertThat(schema.type()).isEqualTo("string");
    assertThat(schema.values()).containsExactly("opt1", "opt2", "opt3");
    assertThat(schema.enumNames()).containsExactly("Option One", "Option Two", "Option Three");
  }

  @Test
  void default_should_be_required() {
    LegacyTitledEnumSchemaBuilder builder =
        new LegacyTitledEnumSchemaBuilder(List.of("a"), List.of("A"));

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void optional_should_set_required_false() {
    LegacyTitledEnumSchemaBuilder builder =
        new LegacyTitledEnumSchemaBuilder(List.of("a"), List.of("A")).optional();

    assertThat(builder.isRequired()).isFalse();
  }

  @Test
  void description_and_title_should_be_set_on_schema() {
    LegacyTitledEnumSchema schema =
        new LegacyTitledEnumSchemaBuilder(List.of("a", "b"), List.of("A", "B"))
            .description("Pick one")
            .title("Choice")
            .build();

    assertThat(schema.description()).isEqualTo("Pick one");
    assertThat(schema.title()).isEqualTo("Choice");
  }
}
