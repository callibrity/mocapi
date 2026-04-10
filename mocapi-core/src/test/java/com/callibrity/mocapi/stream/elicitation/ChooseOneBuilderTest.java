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

import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.TitledSingleSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledSingleSelectEnumSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChooseOneBuilderTest {

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

  @Test
  void fromEnumShouldProduceTitledEnumWithConstTitle() {
    PrimitiveSchemaDefinition schema = ChooseOneBuilder.fromEnum(Tag.class).build();

    assertThat(schema).isInstanceOf(TitledSingleSelectEnumSchema.class);
    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.type()).isEqualTo("string");
    assertThat(titled.oneOf()).hasSize(3);
    assertThat(titled.oneOf().get(0).value()).isEqualTo("JAVA");
    assertThat(titled.oneOf().get(0).title()).isEqualTo("JAVA");
  }

  @Test
  void fromEnumShouldUseToStringForTitle() {
    PrimitiveSchemaDefinition schema = ChooseOneBuilder.fromEnum(TitledColor.class).build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf().get(0).value()).isEqualTo("RED");
    assertThat(titled.oneOf().get(0).title()).isEqualTo("Crimson Red");
  }

  @Test
  void fromEnumWithCustomTitleFnShouldUseIt() {
    PrimitiveSchemaDefinition schema =
        ChooseOneBuilder.fromEnum(Tag.class).titleFn(t -> t.name().toLowerCase()).build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf().get(0).title()).isEqualTo("java");
  }

  @Test
  void fromEnumWithDefaultShouldIncludeDefault() {
    PrimitiveSchemaDefinition schema =
        ChooseOneBuilder.fromEnum(Tag.class).defaultValue(Tag.PYTHON).build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.defaultValue()).isEqualTo("PYTHON");
  }

  @Test
  void fromRawStringsShouldProducePlainEnum() {
    PrimitiveSchemaDefinition schema =
        ChooseOneBuilder.from(List.of("red", "green", "blue")).build();

    assertThat(schema).isInstanceOf(UntitledSingleSelectEnumSchema.class);
    var enumSchema = (UntitledSingleSelectEnumSchema) schema;
    assertThat(enumSchema.type()).isEqualTo("string");
    assertThat(enumSchema.values()).containsExactly("red", "green", "blue");
  }

  @Test
  void fromRawStringsWithDefaultShouldIncludeDefault() {
    PrimitiveSchemaDefinition schema =
        ChooseOneBuilder.from(List.of("red", "green")).defaultValue("green").build();

    var enumSchema = (UntitledSingleSelectEnumSchema) schema;
    assertThat(enumSchema.defaultValue()).isEqualTo("green");
  }

  @Test
  void fromArbitraryItemsShouldProduceTitledEnum() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    PrimitiveSchemaDefinition schema = ChooseOneBuilder.from(statuses, Status::code).build();

    assertThat(schema).isInstanceOf(TitledSingleSelectEnumSchema.class);
    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf()).hasSize(2);
    assertThat(titled.oneOf().get(0).value()).isEqualTo("a");
  }

  @Test
  void fromArbitraryItemsWithTitleFnShouldUseCustomTitle() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    PrimitiveSchemaDefinition schema =
        ChooseOneBuilder.from(statuses, Status::code).titleFn(Status::label).build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.oneOf().get(0).title()).isEqualTo("Active");
  }

  @Test
  void fromArbitraryItemsWithDefaultShouldIncludeDefault() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    PrimitiveSchemaDefinition schema =
        ChooseOneBuilder.from(statuses, Status::code).defaultValue(statuses.get(0)).build();

    var titled = (TitledSingleSelectEnumSchema) schema;
    assertThat(titled.defaultValue()).isEqualTo("a");
  }

  @Test
  void optionalShouldSetRequiredFalse() {
    ChooseOneBuilder<Tag> builder = ChooseOneBuilder.fromEnum(Tag.class).optional();

    assertThat(builder.isRequired()).isFalse();
  }

  @Test
  void defaultShouldBeRequired() {
    ChooseOneBuilder<Tag> builder = ChooseOneBuilder.fromEnum(Tag.class);

    assertThat(builder.isRequired()).isTrue();
  }
}
