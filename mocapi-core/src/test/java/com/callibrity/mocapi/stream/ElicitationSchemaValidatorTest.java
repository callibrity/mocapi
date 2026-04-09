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
package com.callibrity.mocapi.stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ElicitationSchemaValidatorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private ObjectNode objectSchema() {
    ObjectNode schema = mapper.createObjectNode();
    schema.put("type", "object");
    schema.putObject("properties");
    return schema;
  }

  private ObjectNode addProperty(ObjectNode schema, String name) {
    return ((ObjectNode) schema.get("properties")).putObject(name);
  }

  @Test
  void shouldAcceptValidFlatRecordWithStringIntBoolean() {
    ObjectNode schema = objectSchema();
    addProperty(schema, "name").put("type", "string");
    addProperty(schema, "age").put("type", "integer");
    addProperty(schema, "active").put("type", "boolean");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptNumberType() {
    ObjectNode schema = objectSchema();
    addProperty(schema, "score").put("type", "number");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptStringWithAllowedFormats() {
    for (String format : new String[] {"email", "uri", "date", "date-time"}) {
      ObjectNode schema = objectSchema();
      ObjectNode prop = addProperty(schema, "field");
      prop.put("type", "string");
      prop.put("format", format);

      assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
    }
  }

  @Test
  void shouldAcceptStringWithConstraints() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "name");
    prop.put("type", "string");
    prop.put("minLength", 1);
    prop.put("maxLength", 100);
    prop.put("pattern", "^[A-Z].*");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptStringWithEnumValues() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "color");
    prop.put("type", "string");
    ArrayNode enumArr = prop.putArray("enum");
    enumArr.add("red");
    enumArr.add("green");
    enumArr.add("blue");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptStringWithOneOf() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "color");
    prop.put("type", "string");
    ArrayNode oneOf = prop.putArray("oneOf");
    ObjectNode entry = oneOf.addObject();
    entry.put("const", "red");
    entry.put("title", "Red");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptPropertyWithOnlyOneOf() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "color");
    ArrayNode oneOf = prop.putArray("oneOf");
    ObjectNode entry = oneOf.addObject();
    entry.put("const", "red");
    entry.put("title", "Red");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptMultiSelectArrayWithEnumItems() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "tags");
    prop.put("type", "array");
    ObjectNode items = prop.putObject("items");
    ArrayNode enumArr = items.putArray("enum");
    enumArr.add("a");
    enumArr.add("b");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptMultiSelectArrayWithAnyOfConstItems() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "tags");
    prop.put("type", "array");
    ObjectNode items = prop.putObject("items");
    ArrayNode anyOf = items.putArray("anyOf");
    ObjectNode entry = anyOf.addObject();
    entry.put("const", "a");
    entry.put("title", "Option A");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNestedObjectProperty() {
    ObjectNode schema = objectSchema();
    addProperty(schema, "address").put("type", "object");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'address'")
        .hasMessageContaining("type 'object'")
        .hasMessageContaining("not allowed");
  }

  @Test
  void shouldRejectNonEnumArrayProperty() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "items");
    prop.put("type", "array");
    ObjectNode items = prop.putObject("items");
    items.put("type", "string");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'items'")
        .hasMessageContaining("multi-select enum pattern");
  }

  @Test
  void shouldRejectArrayPropertyWithNoItems() {
    ObjectNode schema = objectSchema();
    addProperty(schema, "things").put("type", "array");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'things'")
        .hasMessageContaining("multi-select enum pattern");
  }

  @Test
  void shouldRejectUnsupportedStringFormat() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "host");
    prop.put("type", "string");
    prop.put("format", "hostname");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'host'")
        .hasMessageContaining("unsupported format 'hostname'")
        .hasMessageContaining("Allowed formats: email, uri, date, date-time");
  }

  @Test
  void shouldRejectRefAtRootLevel() {
    ObjectNode schema = objectSchema();
    schema.put("$ref", "#/$defs/Foo");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("'$ref'")
        .hasMessageContaining("not allowed");
  }

  @Test
  void shouldRejectDefsAtRootLevel() {
    ObjectNode schema = objectSchema();
    schema.putObject("$defs");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("'$defs'")
        .hasMessageContaining("not allowed");
  }

  @Test
  void shouldRejectAllOfAtRootLevel() {
    ObjectNode schema = objectSchema();
    schema.putArray("allOf");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("'allOf'")
        .hasMessageContaining("not allowed");
  }

  @Test
  void shouldRejectNotInProperty() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "field");
    prop.put("type", "string");
    prop.putObject("not");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'field'")
        .hasMessageContaining("'not'")
        .hasMessageContaining("not allowed");
  }

  @Test
  void shouldRejectIfThenElseInProperty() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "field");
    prop.put("type", "string");
    prop.putObject("if");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'field'")
        .hasMessageContaining("'if'");
  }

  @Test
  void shouldRejectRefInProperty() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "nested");
    prop.put("$ref", "#/$defs/Something");

    assertThatThrownBy(() -> ElicitationSchemaValidator.validate(schema))
        .isInstanceOf(McpElicitationException.class)
        .hasMessageContaining("Property 'nested'")
        .hasMessageContaining("'$ref'");
  }

  @Test
  void shouldAcceptSchemaWithNoProperties() {
    ObjectNode schema = objectSchema();
    schema.remove("properties");

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptIntegerWithConstraints() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "age");
    prop.put("type", "integer");
    prop.put("minimum", 0);
    prop.put("maximum", 150);
    prop.put("default", 25);

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptBooleanWithDefault() {
    ObjectNode schema = objectSchema();
    ObjectNode prop = addProperty(schema, "flag");
    prop.put("type", "boolean");
    prop.put("default", true);

    assertThatCode(() -> ElicitationSchemaValidator.validate(schema)).doesNotThrowAnyException();
  }
}
