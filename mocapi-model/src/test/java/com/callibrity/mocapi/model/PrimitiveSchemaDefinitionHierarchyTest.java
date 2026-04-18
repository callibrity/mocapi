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
package com.callibrity.mocapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PrimitiveSchemaDefinitionHierarchyTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void single_select_branch_instanceof_chain() {
    var untitled = new UntitledSingleSelectEnumSchema("Color", null, List.of("red", "blue"), null);
    assertThat(untitled)
        .isInstanceOf(SingleSelectEnumSchema.class)
        .isInstanceOf(EnumSchema.class)
        .isInstanceOf(PrimitiveSchemaDefinition.class);

    var titled =
        new TitledSingleSelectEnumSchema(
            "Priority", null, List.of(new EnumOption("high", "High")), null);
    assertThat(titled)
        .isInstanceOf(SingleSelectEnumSchema.class)
        .isInstanceOf(EnumSchema.class)
        .isInstanceOf(PrimitiveSchemaDefinition.class);
  }

  @Test
  void multi_select_branch_instanceof_chain() {
    var untitled =
        new UntitledMultiSelectEnumSchema(
            "Tags", null, 1, 3, new EnumItemsSchema(List.of("a", "b")), null);
    assertThat(untitled)
        .isInstanceOf(MultiSelectEnumSchema.class)
        .isInstanceOf(EnumSchema.class)
        .isInstanceOf(PrimitiveSchemaDefinition.class);

    var titled =
        new TitledMultiSelectEnumSchema(
            "Opts",
            null,
            null,
            null,
            new TitledEnumItemsSchema(List.of(new EnumOption("a", "Alpha"))),
            null);
    assertThat(titled)
        .isInstanceOf(MultiSelectEnumSchema.class)
        .isInstanceOf(EnumSchema.class)
        .isInstanceOf(PrimitiveSchemaDefinition.class);
  }

  @Test
  @SuppressWarnings(
      "deprecation") // Tests the deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility
  void legacy_titled_enum_instanceof_chain() {
    var legacy =
        new LegacyTitledEnumSchema("Status", null, List.of("active"), List.of("Active"), null);
    assertThat(legacy).isInstanceOf(EnumSchema.class).isInstanceOf(PrimitiveSchemaDefinition.class);
  }

  @Test
  void non_enum_types_are_not_enum_schema() {
    var string = new StringSchema("Name", null, null, null, null, null);
    var number = new NumberSchema("number", "Rating", null, null, null, null);
    var bool = new BooleanSchema("Flag", null, null);

    assertThat(string).isNotInstanceOf(EnumSchema.class);
    assertThat(number).isNotInstanceOf(EnumSchema.class);
    assertThat(bool).isNotInstanceOf(EnumSchema.class);
  }

  @Test
  void switch_over_primitive_schema_definition_is_exhaustive() {
    List<PrimitiveSchemaDefinition> schemas =
        List.of(
            new StringSchema("S", null, null, null, null, null),
            new NumberSchema("number", "N", null, null, null, null),
            new BooleanSchema("B", null, null),
            new UntitledSingleSelectEnumSchema("E", null, List.of("a"), null));

    for (PrimitiveSchemaDefinition schema : schemas) {
      String label =
          switch (schema) {
            case StringSchema _ -> "string";
            case NumberSchema _ -> "number";
            case BooleanSchema _ -> "boolean";
            case EnumSchema _ -> "enum";
          };
      assertThat(label).isNotNull();
    }
  }

  @Test
  @SuppressWarnings(
      "deprecation") // Switch must cover deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility
  void switch_over_enum_schema_is_exhaustive() {
    List<EnumSchema> enums =
        List.of(
            new UntitledSingleSelectEnumSchema("A", null, List.of("x"), null),
            new UntitledMultiSelectEnumSchema(
                "B", null, null, null, new EnumItemsSchema(List.of("y")), null),
            new LegacyTitledEnumSchema("C", null, List.of("z"), List.of("Z"), null));

    for (EnumSchema e : enums) {
      String label =
          switch (e) {
            case SingleSelectEnumSchema _ -> "single";
            case MultiSelectEnumSchema _ -> "multi";
            case LegacyTitledEnumSchema _ -> "legacy";
          };
      assertThat(label).isNotNull();
    }
  }

  @Test
  @SuppressWarnings(
      "deprecation") // Tests wire format of deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility
  void wire_format_unchanged_for_all_eight_leaf_types() throws Exception {
    List<PrimitiveSchemaDefinition> schemas =
        List.of(
            new StringSchema("Name", "desc", 1, 100, StringFormat.EMAIL, "default@test.com"),
            new NumberSchema("number", "Rating", "A rating", 1, 10, 5),
            new BooleanSchema("Agree", "Do you agree?", true),
            new UntitledSingleSelectEnumSchema(
                "Color", "Pick", List.of("red", "green", "blue"), "red"),
            new TitledSingleSelectEnumSchema(
                "Priority",
                null,
                List.of(new EnumOption("high", "High"), new EnumOption("low", "Low")),
                null),
            new UntitledMultiSelectEnumSchema(
                "Tags", null, 1, 3, new EnumItemsSchema(List.of("a", "b", "c")), null),
            new TitledMultiSelectEnumSchema(
                "Options",
                "Pick options",
                null,
                null,
                new TitledEnumItemsSchema(
                    List.of(new EnumOption("a", "Alpha"), new EnumOption("b", "Beta"))),
                null),
            new LegacyTitledEnumSchema(
                "Status",
                null,
                List.of("active", "inactive"),
                List.of("Active", "Inactive"),
                "active"));

    String[] expected = {
      "{\"title\":\"Name\",\"description\":\"desc\",\"minLength\":1,\"maxLength\":100,\"format\":\"email\",\"default\":\"default@test.com\",\"type\":\"string\"}",
      "{\"type\":\"number\",\"title\":\"Rating\",\"description\":\"A rating\",\"minimum\":1,\"maximum\":10,\"default\":5}",
      "{\"title\":\"Agree\",\"description\":\"Do you agree?\",\"default\":true,\"type\":\"boolean\"}",
      "{\"title\":\"Color\",\"description\":\"Pick\",\"enum\":[\"red\",\"green\",\"blue\"],\"default\":\"red\",\"type\":\"string\"}",
      "{\"title\":\"Priority\",\"oneOf\":[{\"const\":\"high\",\"title\":\"High\"},{\"const\":\"low\",\"title\":\"Low\"}],\"type\":\"string\"}",
      "{\"title\":\"Tags\",\"minItems\":1,\"maxItems\":3,\"items\":{\"enum\":[\"a\",\"b\",\"c\"],\"type\":\"string\"},\"type\":\"array\"}",
      "{\"title\":\"Options\",\"description\":\"Pick options\",\"items\":{\"anyOf\":[{\"const\":\"a\",\"title\":\"Alpha\"},{\"const\":\"b\",\"title\":\"Beta\"}],\"type\":\"string\"},\"type\":\"array\"}",
      "{\"title\":\"Status\",\"enum\":[\"active\",\"inactive\"],\"enumNames\":[\"Active\",\"Inactive\"],\"default\":\"active\",\"type\":\"string\"}"
    };

    for (int i = 0; i < schemas.size(); i++) {
      String json = mapper.writeValueAsString(schemas.get(i));
      assertThat(json)
          .as("Wire format for %s", schemas.get(i).getClass().getSimpleName())
          .isEqualTo(expected[i]);
    }
  }
}
