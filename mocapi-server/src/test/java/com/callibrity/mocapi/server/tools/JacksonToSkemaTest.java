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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.erosb.jsonsKema.JsonParser;
import com.github.erosb.jsonsKema.Schema;
import com.github.erosb.jsonsKema.SchemaLoader;
import com.github.erosb.jsonsKema.ValidationFailure;
import com.github.erosb.jsonsKema.Validator;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The converter must be behavior-equivalent to the previous {@code new
 * JsonParser(node.toString()).parse()} path: for any input, validating the converted tree against a
 * schema must produce the same pass/fail result as validating the text-parsed tree. These tests
 * assert that equivalence directly across value shapes, so the optimization cannot silently change
 * what gets accepted or rejected.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JacksonToSkemaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String TYPED_SCHEMA =
      """
      {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "type": "object",
        "properties": {
          "name":   { "type": "string" },
          "age":    { "type": "integer", "minimum": 0 },
          "score":  { "type": "number" },
          "active": { "type": "boolean" },
          "tags":   { "type": "array", "items": { "type": "string" } },
          "nested": { "type": "object", "properties": { "x": { "type": "integer" } } },
          "note":   { "type": ["string", "null"] }
        },
        "required": ["name", "age"]
      }
      """;

  @ParameterizedTest
  @ValueSource(
      strings = {
        // Accepted
        "{\"name\":\"ada\",\"age\":36}",
        "{\"name\":\"ada\",\"age\":36,\"score\":9.5,\"active\":true,\"tags\":[\"a\",\"b\"]}",
        "{\"name\":\"ada\",\"age\":0,\"nested\":{\"x\":1},\"note\":null}",
        "{\"name\":\"ada\",\"age\":36,\"note\":\"hi\"}",
        // Rejected — the constraints exercise the distinctions the converter must preserve
        "{\"age\":36}", //                 missing required 'name'
        "{\"name\":\"ada\"}", //           missing required 'age'
        "{\"name\":42,\"age\":36}", //     name: number where string required
        "{\"name\":\"ada\",\"age\":3.5}", // age: decimal where integer required (number fidelity)
        "{\"name\":\"ada\",\"age\":-1}", // age: below minimum
        "{\"name\":\"ada\",\"age\":36,\"active\":\"yes\"}", // active: string where boolean required
        "{\"name\":\"ada\",\"age\":36,\"tags\":[1,2]}", //     tags: numbers where strings required
        "{\"name\":\"ada\",\"age\":36,\"nested\":{\"x\":\"no\"}}", // nested type mismatch
      })
  void converted_tree_validates_identically_to_the_text_parsed_tree(String argsJson) {
    Schema schema = compile(TYPED_SCHEMA);
    JsonNode node = MAPPER.readTree(argsJson);

    boolean viaTextParse =
        Validator.forSchema(schema).validate(new JsonParser(node.toString()).parse()) == null;
    boolean viaConverter =
        Validator.forSchema(schema).validate(JacksonToSkema.convert(node)) == null;

    assertThat(viaConverter)
        .as("converter must agree with text-parse for %s", argsJson)
        .isEqualTo(viaTextParse);
  }

  @Test
  void scalar_and_null_roots_convert() {
    // Non-object roots must also convert (a tool could accept a bare string/number/array).
    for (String json : new String[] {"\"hi\"", "42", "3.14", "true", "null", "[1,2,3]"}) {
      JsonNode node = MAPPER.readTree(json);
      ValidationFailure viaText =
          Validator.forSchema(compile("true")).validate(new JsonParser(node.toString()).parse());
      ValidationFailure viaConv =
          Validator.forSchema(compile("true")).validate(JacksonToSkema.convert(node));
      assertThat(viaConv == null).as("root %s", json).isEqualTo(viaText == null);
    }
  }

  @Test
  void a_null_node_reference_converts_to_json_null() {
    // Defensive: a null JsonNode reference must not NPE — it validates as JSON null.
    boolean accepted =
        Validator.forSchema(compile("true")).validate(JacksonToSkema.convert(null)) == null;
    assertThat(accepted).isTrue();
  }

  @Test
  void pojo_and_binary_nodes_convert_via_their_serialized_form() {
    // Regression guard (AuditIntegrationTest): arguments built programmatically with
    // ObjectNode.putPOJO produce POJONode values, which answer false to every isX() predicate.
    // They must validate through their JSON serialization, exactly as the old toString()+parse
    // path did — not be coerced to null (which would reject a required string field).
    var mapper = MAPPER;
    tools.jackson.databind.node.ObjectNode pojoArgs = mapper.createObjectNode();
    pojoArgs.putPOJO("name", "world"); // POJONode wrapping a String
    tools.jackson.databind.node.ObjectNode binaryArgs = mapper.createObjectNode();
    binaryArgs.put("blob", new byte[] {1, 2, 3}); // BinaryNode -> base64 string

    Schema nameString =
        compile(
            """
            {"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}
            """);
    Schema blobString =
        compile(
            """
            {"type":"object","properties":{"blob":{"type":"string"}}}
            """);

    // Both must agree with the text-parse path (accept), proving the fallback is faithful.
    assertThat(Validator.forSchema(nameString).validate(JacksonToSkema.convert(pojoArgs)))
        .as("POJONode name must validate as the string it serializes to")
        .isEqualTo(
            Validator.forSchema(nameString).validate(new JsonParser(pojoArgs.toString()).parse()));
    assertThat(Validator.forSchema(blobString).validate(JacksonToSkema.convert(binaryArgs)))
        .as("BinaryNode must validate as its base64 string form")
        .isEqualTo(
            Validator.forSchema(blobString)
                .validate(new JsonParser(binaryArgs.toString()).parse()));
  }

  private static Schema compile(String schemaJson) {
    return new SchemaLoader(new JsonParser(schemaJson).parse()).load();
  }
}
