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
import com.callibrity.mocapi.model.TitledMultiSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledMultiSelectEnumSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChooseManyBuilderTest {

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
  void fromEnumShouldProduceTitledMultiSelect() {
    PrimitiveSchemaDefinition schema = ChooseManyBuilder.fromEnum(Tag.class).build();

    assertThat(schema).isInstanceOf(TitledMultiSelectEnumSchema.class);
    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.type()).isEqualTo("array");
    assertThat(titled.items().anyOf()).hasSize(3);
    assertThat(titled.items().anyOf().get(0).value()).isEqualTo("JAVA");
    assertThat(titled.items().anyOf().get(0).title()).isEqualTo("JAVA");
  }

  @Test
  void fromEnumShouldUseToStringForTitle() {
    PrimitiveSchemaDefinition schema = ChooseManyBuilder.fromEnum(TitledColor.class).build();

    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.items().anyOf().get(0).title()).isEqualTo("Crimson Red");
  }

  @Test
  void fromEnumWithCustomTitleFnShouldUseIt() {
    PrimitiveSchemaDefinition schema =
        ChooseManyBuilder.fromEnum(Tag.class).titleFn(t -> t.name().toLowerCase()).build();

    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.items().anyOf().get(0).title()).isEqualTo("java");
  }

  @Test
  void fromEnumWithDefaultsShouldIncludeDefaults() {
    PrimitiveSchemaDefinition schema =
        ChooseManyBuilder.fromEnum(Tag.class).defaults(List.of(Tag.JAVA, Tag.GO)).build();

    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.defaultValues()).containsExactly("JAVA", "GO");
  }

  @Test
  void fromEnumWithMaxItemsShouldIncludeMaxItems() {
    PrimitiveSchemaDefinition schema = ChooseManyBuilder.fromEnum(Tag.class).maxItems(2).build();

    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.maxItems()).isEqualTo(2);
  }

  @Test
  void fromEnumWithMinItemsShouldIncludeMinItems() {
    PrimitiveSchemaDefinition schema = ChooseManyBuilder.fromEnum(Tag.class).minItems(1).build();

    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.minItems()).isEqualTo(1);
  }

  @Test
  void fromRawStringsShouldProduceMultiSelect() {
    PrimitiveSchemaDefinition schema =
        ChooseManyBuilder.from(List.of("java", "python", "go")).build();

    assertThat(schema).isInstanceOf(UntitledMultiSelectEnumSchema.class);
    var multi = (UntitledMultiSelectEnumSchema) schema;
    assertThat(multi.type()).isEqualTo("array");
    assertThat(multi.items().type()).isEqualTo("string");
    assertThat(multi.items().values()).containsExactly("java", "python", "go");
  }

  @Test
  void fromArbitraryItemsShouldProduceTitledMultiSelect() {
    record Role(String code, String label) {}
    List<Role> roles = List.of(new Role("admin", "Admin"), new Role("user", "User"));

    PrimitiveSchemaDefinition schema = ChooseManyBuilder.from(roles, Role::code).build();

    assertThat(schema).isInstanceOf(TitledMultiSelectEnumSchema.class);
    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.items().anyOf()).hasSize(2);
    assertThat(titled.items().anyOf().get(0).value()).isEqualTo("admin");
  }

  @Test
  void fromArbitraryItemsWithTitleFnShouldUseIt() {
    record Role(String code, String label) {}
    List<Role> roles = List.of(new Role("admin", "Administrator"));

    PrimitiveSchemaDefinition schema =
        ChooseManyBuilder.from(roles, Role::code).titleFn(Role::label).build();

    var titled = (TitledMultiSelectEnumSchema) schema;
    assertThat(titled.items().anyOf().get(0).title()).isEqualTo("Administrator");
  }

  @Test
  void shouldChainMultipleConstraints() {
    PrimitiveSchemaDefinition schema =
        ChooseManyBuilder.fromEnum(Tag.class)
            .titleFn(t -> t.name().toLowerCase())
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

  @Test
  void optionalShouldSetRequiredFalse() {
    ChooseManyBuilder<Tag> builder = ChooseManyBuilder.fromEnum(Tag.class).optional();

    assertThat(builder.isRequired()).isFalse();
  }

  @Test
  void defaultShouldBeRequired() {
    ChooseManyBuilder<Tag> builder = ChooseManyBuilder.fromEnum(Tag.class);

    assertThat(builder.isRequired()).isTrue();
  }
}
