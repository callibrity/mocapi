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

import com.callibrity.mocapi.model.MultiSelectEnumSchema;
import com.callibrity.mocapi.model.TitledMultiSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledMultiSelectEnumSchema;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MultiSelectEnumSchemaBuilderTest {

  enum Tag {
    JAVA,
    PYTHON,
    GO
  }

  enum TitledColor {
    RED("Crimson Red"),
    GREEN("Forest Green");

    private final String title;

    TitledColor(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  @Nested
  class Untitled {

    @Test
    void from_enum_should_default_to_untitled() {
      MultiSelectEnumSchema schema = MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.type()).isEqualTo("array");
      assertThat(untitled.items().values()).containsExactly("JAVA", "PYTHON", "GO");
    }

    @Test
    void from_enum_with_empty_defaults_should_omit_default_values() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).defaults(List.of()).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.defaultValues()).isNull();
    }

    @Test
    void from_enum_with_defaults_should_include_defaults() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class)
              .defaults(List.of(Tag.JAVA, Tag.GO))
              .build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.defaultValues()).containsExactly("JAVA", "GO");
    }

    @Test
    void from_enum_with_max_items_should_include_max_items() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).maxItems(2).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.maxItems()).isEqualTo(2);
    }

    @Test
    void from_enum_with_min_items_should_include_min_items() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).minItems(1).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.minItems()).isEqualTo(1);
    }

    @Test
    void from_raw_strings_should_produce_untitled() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.from(List.of("java", "python", "go")).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.type()).isEqualTo("array");
      assertThat(untitled.items().type()).isEqualTo("string");
      assertThat(untitled.items().values()).containsExactly("java", "python", "go");
    }

    @Test
    void from_arbitrary_items_should_default_to_untitled() {
      record Role(String code, String label) {}
      List<Role> roles = List.of(new Role("admin", "Admin"), new Role("user", "User"));

      MultiSelectEnumSchema schema = MultiSelectEnumSchemaBuilder.from(roles, Role::code).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.items().values()).containsExactly("admin", "user");
    }
  }

  @Nested
  class Titled {

    @Test
    void from_enum_with_titled_should_produce_titled() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).titled(Enum::name).build();

      assertThat(schema).isInstanceOf(TitledMultiSelectEnumSchema.class);
      var titled = (TitledMultiSelectEnumSchema) schema;
      assertThat(titled.items().anyOf()).hasSize(3);
      assertThat(titled.items().anyOf().get(0).value()).isEqualTo("JAVA");
      assertThat(titled.items().anyOf().get(0).title()).isEqualTo("JAVA");
    }

    @Test
    void from_enum_with_titled_to_string_should_use_to_string() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(TitledColor.class)
              .titled(TitledColor::toString)
              .build();

      var titled = (TitledMultiSelectEnumSchema) schema;
      assertThat(titled.items().anyOf().get(0).title()).isEqualTo("Crimson Red");
    }

    @Test
    void from_enum_with_custom_titled_fn_should_use_it() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class)
              .titled(t -> t.name().toLowerCase())
              .build();

      var titled = (TitledMultiSelectEnumSchema) schema;
      assertThat(titled.items().anyOf().get(0).title()).isEqualTo("java");
    }

    @Test
    void from_arbitrary_items_with_titled_should_use_it() {
      record Role(String code, String label) {}
      List<Role> roles = List.of(new Role("admin", "Administrator"));

      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.from(roles, Role::code).titled(Role::label).build();

      var titled = (TitledMultiSelectEnumSchema) schema;
      assertThat(titled.items().anyOf().get(0).title()).isEqualTo("Administrator");
    }

    @Test
    void titled_with_multiple_constraints_should_chain() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class)
              .titled(t -> t.name().toLowerCase())
              .defaults(List.of(Tag.PYTHON))
              .minItems(1)
              .maxItems(3)
              .build();

      var titled = (TitledMultiSelectEnumSchema) schema;
      assertThat(titled.items().anyOf().get(0).title()).isEqualTo("java");
      assertThat(titled.defaultValues()).containsExactly("PYTHON");
      assertThat(titled.minItems()).isEqualTo(1);
      assertThat(titled.maxItems()).isEqualTo(3);
    }
  }

  @Nested
  class Required_and_optional {

    @Test
    void optional_should_set_required_false() {
      MultiSelectEnumSchemaBuilder<Tag> builder =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).optional();

      assertThat(builder.isRequired()).isFalse();
    }

    @Test
    void default_should_be_required() {
      MultiSelectEnumSchemaBuilder<Tag> builder = MultiSelectEnumSchemaBuilder.fromEnum(Tag.class);

      assertThat(builder.isRequired()).isTrue();
    }
  }

  @Nested
  class Metadata {

    @Test
    void description_and_title_should_be_set_on_schema() {
      MultiSelectEnumSchema schema =
          MultiSelectEnumSchemaBuilder.fromEnum(Tag.class)
              .description("Pick languages")
              .title("Languages")
              .build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      var untitled = (UntitledMultiSelectEnumSchema) schema;
      assertThat(untitled.description()).isEqualTo("Pick languages");
      assertThat(untitled.title()).isEqualTo("Languages");
    }

    @Test
    void build_without_defaults_should_leave_default_values_null() {
      MultiSelectEnumSchema schema = MultiSelectEnumSchemaBuilder.fromEnum(Tag.class).build();

      assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
      assertThat(((UntitledMultiSelectEnumSchema) schema).defaultValues()).isNull();
    }
  }
}
