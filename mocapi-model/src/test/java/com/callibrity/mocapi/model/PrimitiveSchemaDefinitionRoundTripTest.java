/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves every {@link PrimitiveSchemaDefinition} leaf variant round-trips through a plain {@link
 * JsonMapper} — no wire-mapper configuration, no {@code @JsonTypeInfo} — via {@link
 * PrimitiveSchemaDefinitionDeserializer}, and that the serialized shape still matches the {@code
 * type}/{@code enum} expectations pinned in {@code docs/plans/2026-07-28-schema.ts}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PrimitiveSchemaDefinitionRoundTripTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  static Stream<PrimitiveSchemaDefinition> variants() {
    return Stream.of(
        new StringSchema("Name", "desc", 1, 100, StringFormat.EMAIL, "default@test.com"),
        new NumberSchema("number", "Rating", "A rating", 1, 10, 5),
        new NumberSchema("integer", "Count", "A count", 0, 100, 1),
        new BooleanSchema("Agree", "Do you agree?", true),
        new UntitledSingleSelectEnumSchema("Color", "Pick", List.of("red", "green", "blue"), "red"),
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
        legacyTitledEnumSchema());
  }

  @SuppressWarnings(
      "deprecation") // Round-trips the deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility (docs/plans/2026-07-28-schema.ts)
  private static LegacyTitledEnumSchema legacyTitledEnumSchema() {
    return new LegacyTitledEnumSchema(
        "Status", null, List.of("active", "inactive"), List.of("Active", "Inactive"), "active");
  }

  @ParameterizedTest
  @MethodSource("variants")
  void primitive_schema_definition_round_trips_through_a_plain_mapper(
      PrimitiveSchemaDefinition schema) {
    String json = mapper.writeValueAsString(schema);

    PrimitiveSchemaDefinition deserialized =
        mapper.readValue(json, PrimitiveSchemaDefinition.class);

    assertThat(deserialized).isEqualTo(schema);
  }

  @ParameterizedTest
  @MethodSource("variants")
  @SuppressWarnings(
      "deprecation") // Switch must cover deprecated LegacyTitledEnumSchema per MCP spec backward
  // compatibility (docs/plans/2026-07-28-schema.ts)
  void serialized_shape_matches_the_schema_ts_type_and_enum_discriminators(
      PrimitiveSchemaDefinition schema) {
    var node = mapper.convertValue(schema, tools.jackson.databind.node.ObjectNode.class);

    switch (schema) {
      case StringSchema _ -> assertThat(node.path("type").asString()).isEqualTo("string");
      case NumberSchema number -> assertThat(node.path("type").asString()).isEqualTo(number.type());
      case BooleanSchema _ -> assertThat(node.path("type").asString()).isEqualTo("boolean");
      case UntitledSingleSelectEnumSchema _ -> {
        assertThat(node.path("type").asString()).isEqualTo("string");
        assertThat(node.has("enum")).isTrue();
        assertThat(node.has("oneOf")).isFalse();
      }
      case TitledSingleSelectEnumSchema _ -> {
        assertThat(node.path("type").asString()).isEqualTo("string");
        assertThat(node.has("oneOf")).isTrue();
      }
      case UntitledMultiSelectEnumSchema _ -> {
        assertThat(node.path("type").asString()).isEqualTo("array");
        assertThat(node.path("items").has("enum")).isTrue();
      }
      case TitledMultiSelectEnumSchema _ -> {
        assertThat(node.path("type").asString()).isEqualTo("array");
        assertThat(node.path("items").has("anyOf")).isTrue();
      }
      case LegacyTitledEnumSchema _ -> {
        assertThat(node.path("type").asString()).isEqualTo("string");
        assertThat(node.has("enum")).isTrue();
        assertThat(node.has("enumNames")).isTrue();
      }
    }
  }
}
