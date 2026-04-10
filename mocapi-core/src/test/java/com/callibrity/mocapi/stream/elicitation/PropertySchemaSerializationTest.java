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
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PropertySchemaSerializationTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void stringPropertySerializesCorrectly() throws Exception {
    var schema = new StringSchemaBuilder().description("Email").email().build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"string\"");
    assertThat(json).contains("\"format\":\"email\"");
    assertThat(json).contains("\"description\":\"Email\"");
  }

  @Test
  void stringPropertyOmitsNullFields() throws Exception {
    var schema = new StringSchemaBuilder().description("Name").build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).doesNotContain("title");
    assertThat(json).doesNotContain("default");
    assertThat(json).doesNotContain("minLength");
    assertThat(json).doesNotContain("maxLength");
    assertThat(json).doesNotContain("format");
  }

  @Test
  void integerPropertySerializesCorrectly() throws Exception {
    var schema = new IntegerSchemaBuilder().description("Age").minimum(0).maximum(150).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"integer\"");
    assertThat(json).contains("\"minimum\":");
    assertThat(json).contains("\"maximum\":");
  }

  @Test
  void numberPropertySerializesCorrectly() throws Exception {
    var schema = new NumberSchemaBuilder().description("Score").defaultValue(95.5).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"number\"");
    assertThat(json).contains("\"default\":95.5");
  }

  @Test
  void booleanPropertySerializesCorrectly() throws Exception {
    var schema = new BooleanSchemaBuilder().description("Active").defaultValue(true).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"boolean\"");
    assertThat(json).contains("\"default\":true");
  }

  @Test
  void enumPropertySerializesWithEnumKeyword() throws Exception {
    PrimitiveSchemaDefinition schema =
        SingleSelectEnumSchemaBuilder.from(List.of("red", "green", "blue")).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"string\"");
    assertThat(json).contains("\"enum\":[\"red\",\"green\",\"blue\"]");
  }

  @Test
  void titledEnumPropertySerializesWithOneOf() throws Exception {
    PrimitiveSchemaDefinition schema =
        SingleSelectEnumSchemaBuilder.from(List.of("a", "b"), s -> s)
            .titled(s -> s.toUpperCase())
            .build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"string\"");
    assertThat(json).contains("\"oneOf\":");
    assertThat(json).contains("\"const\":\"a\"");
    assertThat(json).contains("\"title\":\"A\"");
  }

  @Test
  void multiSelectPropertySerializesCorrectly() throws Exception {
    PrimitiveSchemaDefinition schema =
        MultiSelectEnumSchemaBuilder.from(List.of("java", "python")).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"array\"");
    assertThat(json).contains("\"items\":");
    assertThat(json).contains("\"enum\":[\"java\",\"python\"]");
  }

  @Test
  void titledMultiSelectPropertySerializesCorrectly() throws Exception {
    PrimitiveSchemaDefinition schema =
        MultiSelectEnumSchemaBuilder.from(List.of("a", "b"), s -> s)
            .titled(s -> s.toUpperCase())
            .build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"array\"");
    assertThat(json).contains("\"anyOf\":");
    assertThat(json).contains("\"const\":\"a\"");
    assertThat(json).contains("\"title\":\"A\"");
  }

  @Test
  void legacyEnumPropertySerializesCorrectly() throws Exception {
    var schema =
        new LegacyTitledEnumSchemaBuilder(List.of("a", "b"), List.of("Alpha", "Beta")).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"type\":\"string\"");
    assertThat(json).contains("\"enum\":[\"a\",\"b\"]");
    assertThat(json).contains("\"enumNames\":[\"Alpha\",\"Beta\"]");
  }

  @Test
  void defaultValueUsesJsonPropertyAnnotation() throws Exception {
    var schema = new StringSchemaBuilder().description("Name").defaultValue("Alice").build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"default\":\"Alice\"");
    assertThat(json).doesNotContain("defaultValue");
  }

  @Test
  void enumFieldUsesJsonPropertyAnnotation() throws Exception {
    PrimitiveSchemaDefinition schema = SingleSelectEnumSchemaBuilder.from(List.of("x")).build();

    String json = mapper.writeValueAsString(schema);

    assertThat(json).contains("\"enum\":");
    assertThat(json).doesNotContain("\"values\"");
  }
}
