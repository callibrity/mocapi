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

import com.callibrity.mocapi.model.SingleSelectEnumSchema;
import com.callibrity.mocapi.model.TitledSingleSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledSingleSelectEnumSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class SingleSelectEnumSchemaBuilderTest {

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

  // --- Default (untitled) tests ---

  @Test
  void fromEnumShouldDefaultToUntitled() {
    SingleSelectEnumSchema schema = SingleSelectEnumSchemaBuilder.fromEnum(Tag.class).build();

    assertThat(schema).isInstanceOf(UntitledSingleSelectEnumSchema.class);
    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.type()).isEqualTo("string");
    assertThat(untitled.values()).containsExactly("JAVA", "PYTHON", "GO");
  }

  @Test
  void fromEnumWithDefaultShouldIncludeDefault() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.fromEnum(Tag.class).defaultValue(Tag.PYTHON).build();

    assertThat(schema).isInstanceOf(UntitledSingleSelectEnumSchema.class);
    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.defaultValue()).isEqualTo("PYTHON");
  }

  @Test
  void fromRawStringsShouldProduceUntitled() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.from(List.of("red", "green", "blue")).build();

    assertThat(schema).isInstanceOf(UntitledSingleSelectEnumSchema.class);
    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.type()).isEqualTo("string");
    assertThat(untitled.values()).containsExactly("red", "green", "blue");
  }

  @Test
  void fromRawStringsWithDefaultShouldIncludeDefault() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.from(List.of("red", "green")).defaultValue("green").build();

    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.defaultValue()).isEqualTo("green");
  }

  @Test
  void fromArbitraryItemsShouldDefaultToUntitled() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.from(statuses, Status::code).build();

    assertThat(schema).isInstanceOf(UntitledSingleSelectEnumSchema.class);
    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.values()).containsExactly("a", "i");
  }

  @Test
  void fromArbitraryItemsWithDefaultShouldIncludeDefault() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.from(statuses, Status::code)
            .defaultValue(statuses.get(0))
            .build();

    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.defaultValue()).isEqualTo("a");
  }

  // --- Titled tests ---

  @Test
  void fromEnumWithTitledShouldProduceTitled() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.fromEnum(Tag.class).titled(Enum::name).build();

    assertThat(schema).isInstanceOf(TitledSingleSelectEnumSchema.class);
    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf()).hasSize(3);
    assertThat(titled.oneOf().get(0).value()).isEqualTo("JAVA");
    assertThat(titled.oneOf().get(0).title()).isEqualTo("JAVA");
  }

  @Test
  void fromEnumWithTitledToStringShouldUseToString() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.fromEnum(TitledColor.class)
            .titled(TitledColor::toString)
            .build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf().get(0).value()).isEqualTo("RED");
    assertThat(titled.oneOf().get(0).title()).isEqualTo("Crimson Red");
  }

  @Test
  void fromEnumWithCustomTitledFnShouldUseIt() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.fromEnum(Tag.class)
            .titled(t -> t.name().toLowerCase())
            .build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf().get(0).title()).isEqualTo("java");
  }

  @Test
  void fromRawStringsWithTitledShouldProduceTitled() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.from(List.of("red", "green", "blue"))
            .titled(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
            .build();

    assertThat(schema).isInstanceOf(TitledSingleSelectEnumSchema.class);
    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf()).hasSize(3);
    assertThat(titled.oneOf().get(0).value()).isEqualTo("red");
    assertThat(titled.oneOf().get(0).title()).isEqualTo("Red");
  }

  @Test
  void fromArbitraryItemsWithTitledShouldUseCustomTitle() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.from(statuses, Status::code).titled(Status::label).build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf().get(0).title()).isEqualTo("Active");
  }

  @Test
  void titledWithDefaultValueShouldIncludeDefault() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.fromEnum(Tag.class)
            .titled(t -> t.name().toLowerCase())
            .defaultValue(Tag.PYTHON)
            .build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.defaultValue()).isEqualTo("PYTHON");
    assertThat(titled.oneOf().get(0).title()).isEqualTo("java");
  }

  // --- Required/optional tests ---

  @Test
  void optionalShouldSetRequiredFalse() {
    SingleSelectEnumSchemaBuilder<Tag> builder =
        SingleSelectEnumSchemaBuilder.fromEnum(Tag.class).optional();

    assertThat(builder.isRequired()).isFalse();
  }

  @Test
  void defaultShouldBeRequired() {
    SingleSelectEnumSchemaBuilder<Tag> builder = SingleSelectEnumSchemaBuilder.fromEnum(Tag.class);

    assertThat(builder.isRequired()).isTrue();
  }

  @Test
  void descriptionAndTitleShouldBeSetOnSchema() {
    SingleSelectEnumSchema schema =
        SingleSelectEnumSchemaBuilder.fromEnum(Tag.class)
            .description("Pick a language")
            .title("Language")
            .build();

    assertThat(schema).isInstanceOf(UntitledSingleSelectEnumSchema.class);
    var untitled = (UntitledSingleSelectEnumSchema) schema;
    assertThat(untitled.description()).isEqualTo("Pick a language");
    assertThat(untitled.title()).isEqualTo("Language");
  }
}
