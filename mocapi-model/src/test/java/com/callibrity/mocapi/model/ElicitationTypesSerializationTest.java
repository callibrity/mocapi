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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ElicitationTypesSerializationTest {

  private final ObjectMapper mapper = JsonMapper.builder().build();

  @Test
  void elicitActionSerializesAsLowercase() throws Exception {
    assertThat(mapper.writeValueAsString(ElicitAction.ACCEPT)).isEqualTo("\"accept\"");
    assertThat(mapper.writeValueAsString(ElicitAction.DECLINE)).isEqualTo("\"decline\"");
    assertThat(mapper.writeValueAsString(ElicitAction.CANCEL)).isEqualTo("\"cancel\"");
  }

  @Test
  void elicitActionDeserializesFromLowercase() throws Exception {
    assertThat(mapper.readValue("\"accept\"", ElicitAction.class)).isEqualTo(ElicitAction.ACCEPT);
    assertThat(mapper.readValue("\"decline\"", ElicitAction.class)).isEqualTo(ElicitAction.DECLINE);
    assertThat(mapper.readValue("\"cancel\"", ElicitAction.class)).isEqualTo(ElicitAction.CANCEL);
  }

  @Test
  void elicitResultRoundTrip() throws Exception {
    ObjectNode content = mapper.createObjectNode();
    content.put("name", "Alice");
    var result = new ElicitResult(ElicitAction.ACCEPT, content);
    String json = mapper.writeValueAsString(result);
    assertThat(json).contains("\"action\":\"accept\"").contains("\"name\":\"Alice\"");

    var deserialized = mapper.readValue(json, ElicitResult.class);
    assertThat(deserialized.action()).isEqualTo(ElicitAction.ACCEPT);
    assertThat(deserialized.content().get("name").asString()).isEqualTo("Alice");
  }

  @Test
  void elicitResultNullContentOmitted() throws Exception {
    var result = new ElicitResult(ElicitAction.DECLINE, null);
    String json = mapper.writeValueAsString(result);
    assertThat(json).doesNotContain("content");
  }

  @Test
  void stringFormatSerializesCorrectly() throws Exception {
    assertThat(mapper.writeValueAsString(StringFormat.EMAIL)).isEqualTo("\"email\"");
    assertThat(mapper.writeValueAsString(StringFormat.URI)).isEqualTo("\"uri\"");
    assertThat(mapper.writeValueAsString(StringFormat.DATE)).isEqualTo("\"date\"");
    assertThat(mapper.writeValueAsString(StringFormat.DATE_TIME)).isEqualTo("\"date-time\"");
  }

  @Test
  void stringFormatDeserializesCorrectly() throws Exception {
    assertThat(mapper.readValue("\"date-time\"", StringFormat.class))
        .isEqualTo(StringFormat.DATE_TIME);
    assertThat(mapper.readValue("\"email\"", StringFormat.class)).isEqualTo(StringFormat.EMAIL);
  }

  @Test
  void stringSchemaSerializesToJsonSchema() throws Exception {
    var schema = new StringSchema("Name", "Your name", 1, 100, null, null);
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"string\"")
        .contains("\"title\":\"Name\"")
        .contains("\"minLength\":1")
        .contains("\"maxLength\":100")
        .doesNotContain("\"format\"")
        .doesNotContain("\"default\"");
  }

  @Test
  void stringSchemaWithFormat() throws Exception {
    var schema = new StringSchema("Email", null, null, null, StringFormat.EMAIL, null);
    String json = mapper.writeValueAsString(schema);
    assertThat(json).contains("\"format\":\"email\"");
  }

  @Test
  void numberSchemaSerializesToJsonSchema() throws Exception {
    var schema = new NumberSchema("number", "Rating", "A rating", 1, 10, 5);
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"number\"")
        .contains("\"minimum\":1")
        .contains("\"maximum\":10")
        .contains("\"default\":5");
  }

  @Test
  void integerSchemaUsesIntegerType() throws Exception {
    var schema = new NumberSchema("integer", "Count", null, 0, null, null);
    String json = mapper.writeValueAsString(schema);
    assertThat(json).contains("\"type\":\"integer\"").doesNotContain("\"maximum\"");
  }

  @Test
  void booleanSchemaSerializesToJsonSchema() throws Exception {
    var schema = new BooleanSchema("Agree", "Do you agree?", true);
    String json = mapper.writeValueAsString(schema);
    assertThat(json).contains("\"type\":\"boolean\"").contains("\"default\":true");
  }

  @Test
  void untitledSingleSelectEnumSchema() throws Exception {
    var schema =
        new UntitledSingleSelectEnumSchema(
            "Color", "Pick a color", List.of("red", "green", "blue"), "red");
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"string\"")
        .contains("\"enum\":[\"red\",\"green\",\"blue\"]")
        .contains("\"default\":\"red\"");
  }

  @Test
  void titledSingleSelectEnumSchema() throws Exception {
    var schema =
        new TitledSingleSelectEnumSchema(
            "Priority",
            null,
            List.of(new EnumOption("high", "High"), new EnumOption("low", "Low")),
            null);
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"string\"")
        .contains("\"oneOf\":")
        .contains("\"const\":\"high\"")
        .contains("\"title\":\"High\"");
  }

  @Test
  void untitledMultiSelectEnumSchema() throws Exception {
    var items = new EnumItemsSchema(List.of("a", "b", "c"));
    var schema = new UntitledMultiSelectEnumSchema("Tags", null, 1, 3, items, null);
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"array\"")
        .contains("\"minItems\":1")
        .contains("\"maxItems\":3")
        .contains("\"items\":")
        .contains("\"enum\":[\"a\",\"b\",\"c\"]");
  }

  @Test
  void enumItemsSchemaIncludesType() throws Exception {
    var items = new EnumItemsSchema(List.of("x", "y"));
    String json = mapper.writeValueAsString(items);
    assertThat(json).contains("\"type\":\"string\"").contains("\"enum\":[\"x\",\"y\"]");
  }

  @Test
  void titledMultiSelectEnumSchema() throws Exception {
    var items =
        new TitledEnumItemsSchema(
            List.of(new EnumOption("a", "Alpha"), new EnumOption("b", "Beta")));
    var schema =
        new TitledMultiSelectEnumSchema("Options", "Pick options", null, null, items, null);
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"array\"")
        .contains("\"anyOf\":")
        .contains("\"const\":\"a\"")
        .contains("\"title\":\"Alpha\"");
  }

  @Test
  @SuppressWarnings(
      "deprecation") // Tests deprecated LegacyTitledEnumSchema per MCP spec backward compatibility
  void legacyTitledEnumSchema() throws Exception {
    var schema =
        new LegacyTitledEnumSchema(
            "Status", null, List.of("active", "inactive"), List.of("Active", "Inactive"), "active");
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"string\"")
        .contains("\"enum\":[\"active\",\"inactive\"]")
        .contains("\"enumNames\":[\"Active\",\"Inactive\"]")
        .contains("\"default\":\"active\"");
  }

  @Test
  void requestedSchemaSerializesToObjectSchema() throws Exception {
    Map<String, PrimitiveSchemaDefinition> props = new LinkedHashMap<>();
    props.put("name", new StringSchema("Name", null, null, null, null, null));
    props.put("age", new NumberSchema("integer", "Age", null, 0, 150, null));
    var schema = new RequestedSchema(props, List.of("name"));
    String json = mapper.writeValueAsString(schema);
    assertThat(json)
        .contains("\"type\":\"object\"")
        .contains("\"required\":[\"name\"]")
        .contains("\"properties\":{")
        .contains("\"name\":{")
        .contains("\"age\":{");
  }

  @Test
  void enumOptionSerialization() throws Exception {
    var option = new EnumOption("val", "Display Value");
    String json = mapper.writeValueAsString(option);
    assertThat(json).contains("\"const\":\"val\"").contains("\"title\":\"Display Value\"");
  }
}
