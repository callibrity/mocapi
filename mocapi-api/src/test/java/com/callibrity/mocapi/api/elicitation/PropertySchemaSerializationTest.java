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

import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PropertySchemaSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Nested
  class Primitive_properties {
    @Test
    void string_property_serializes_correctly() throws Exception {
      var schema = new StringSchemaBuilder().description("Email").email().build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"string\"")
          .contains("\"format\":\"email\"")
          .contains("\"description\":\"Email\"");
    }

    @Test
    void string_property_omits_null_fields() throws Exception {
      var schema = new StringSchemaBuilder().description("Name").build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .doesNotContain("title")
          .doesNotContain("default")
          .doesNotContain("minLength")
          .doesNotContain("maxLength")
          .doesNotContain("format");
    }

    @Test
    void integer_property_serializes_correctly() throws Exception {
      var schema = new IntegerSchemaBuilder().description("Age").minimum(0).maximum(150).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"integer\"")
          .contains("\"minimum\":")
          .contains("\"maximum\":");
    }

    @Test
    void number_property_serializes_correctly() throws Exception {
      var schema = new NumberSchemaBuilder().description("Score").defaultValue(95.5).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json).contains("\"type\":\"number\"").contains("\"default\":95.5");
    }

    @Test
    void boolean_property_serializes_correctly() throws Exception {
      var schema = new BooleanSchemaBuilder().description("Active").defaultValue(true).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json).contains("\"type\":\"boolean\"").contains("\"default\":true");
    }
  }

  @Nested
  class Enum_properties {
    @Test
    void enum_property_serializes_with_enum_keyword() throws Exception {
      PrimitiveSchemaDefinition schema =
          SingleSelectEnumSchemaBuilder.from(List.of("red", "green", "blue")).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"string\"")
          .contains("\"enum\":[\"red\",\"green\",\"blue\"]");
    }

    @Test
    void titled_enum_property_serializes_with_one_of() throws Exception {
      PrimitiveSchemaDefinition schema =
          SingleSelectEnumSchemaBuilder.from(List.of("a", "b"), s -> s)
              .titled(String::toUpperCase)
              .build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"string\"")
          .contains("\"oneOf\":")
          .contains("\"const\":\"a\"")
          .contains("\"title\":\"A\"");
    }

    @Test
    void multi_select_property_serializes_correctly() throws Exception {
      PrimitiveSchemaDefinition schema =
          MultiSelectEnumSchemaBuilder.from(List.of("java", "python")).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"array\"")
          .contains("\"items\":")
          .contains("\"enum\":[\"java\",\"python\"]");
    }

    @Test
    void titled_multi_select_property_serializes_correctly() throws Exception {
      PrimitiveSchemaDefinition schema =
          MultiSelectEnumSchemaBuilder.from(List.of("a", "b"), s -> s)
              .titled(String::toUpperCase)
              .build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"array\"")
          .contains("\"anyOf\":")
          .contains("\"const\":\"a\"")
          .contains("\"title\":\"A\"");
    }

    @Test
    @SuppressWarnings(
        "deprecation") // Tests deprecated LegacyTitledEnumSchemaBuilder per MCP spec backward
    // compatibility
    void legacy_enum_property_serializes_correctly() throws Exception {
      var schema =
          new LegacyTitledEnumSchemaBuilder(List.of("a", "b"), List.of("Alpha", "Beta")).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json)
          .contains("\"type\":\"string\"")
          .contains("\"enum\":[\"a\",\"b\"]")
          .contains("\"enumNames\":[\"Alpha\",\"Beta\"]");
    }
  }

  @Nested
  class Json_property_annotations {
    @Test
    void default_value_uses_json_property_annotation() throws Exception {
      var schema = new StringSchemaBuilder().description("Name").defaultValue("Alice").build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json).contains("\"default\":\"Alice\"").doesNotContain("defaultValue");
    }

    @Test
    void enum_field_uses_json_property_annotation() throws Exception {
      PrimitiveSchemaDefinition schema = SingleSelectEnumSchemaBuilder.from(List.of("x")).build();

      String json = mapper.writeValueAsString(schema);

      assertThat(json).contains("\"enum\":").doesNotContain("\"values\"");
    }
  }
}
