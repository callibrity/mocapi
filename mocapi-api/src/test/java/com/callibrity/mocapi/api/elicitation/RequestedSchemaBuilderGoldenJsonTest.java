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

import com.callibrity.mocapi.model.RequestedSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RequestedSchemaBuilderGoldenJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }

  @Test
  @SuppressWarnings(
      "deprecation") // Tests deprecated chooseLegacy() per MCP spec backward compatibility
  void builder_schema_should_match_golden_fixture() throws Exception {
    RequestedSchema requestedSchema =
        new RequestedSchemaBuilder()
            .string("name", "Full name", "Jane Doe")
            .string("email", "Email address", s -> s.email().optional())
            .integer("age", "Your age", a -> a.minimum(0).maximum(150).defaultValue(30))
            .number("score", "Test score", n -> n.minimum(0.0).maximum(100.0).defaultValue(95.5))
            .bool("active", "Is active", true)
            .choose("color", List.of("red", "green", "blue"), "green")
            .choose("priority", Priority.class, Priority.MEDIUM)
            .chooseMany("tags", List.of("java", "python", "go"))
            .chooseMany("roles", Priority.class, c -> c.defaults(List.of(Priority.LOW)).minItems(1))
            .chooseLegacy("legacy", List.of("a", "b"), List.of("Alpha", "Beta"))
            .build();

    JsonNode actual = objectMapper.valueToTree(requestedSchema);

    String goldenJson =
        """
        {
          "type": "object",
          "properties": {
            "name": {"type":"string","description":"Full name","default":"Jane Doe"},
            "email": {"type":"string","description":"Email address","format":"email"},
            "age": {"type":"integer","description":"Your age","minimum":0,"maximum":150,"default":30},
            "score": {"type":"number","description":"Test score","minimum":0.0,"maximum":100.0,"default":95.5},
            "active": {"type":"boolean","description":"Is active","default":true},
            "color": {"type":"string","enum":["red","green","blue"],"default":"green"},
            "priority": {"type":"string","enum":["LOW","MEDIUM","HIGH"],"default":"MEDIUM"},
            "tags": {"type":"array","items":{"type":"string","enum":["java","python","go"]}},
            "roles": {"type":"array","minItems":1,"items":{"type":"string","enum":["LOW","MEDIUM","HIGH"]},"default":["LOW"]},
            "legacy": {"type":"string","enum":["a","b"],"enumNames":["Alpha","Beta"]}
          },
          "required": ["name","age","score","active","color","priority","tags","roles","legacy"]
        }""";
    JsonNode expected = objectMapper.readTree(goldenJson);

    assertThat(actual).isEqualTo(expected);

    ObjectNode actualProps = (ObjectNode) actual.get("properties");
    List<String> propertyOrder = actualProps.properties().stream().map(Map.Entry::getKey).toList();
    assertThat(propertyOrder)
        .containsExactly(
            "name",
            "email",
            "age",
            "score",
            "active",
            "color",
            "priority",
            "tags",
            "roles",
            "legacy");
  }
}
