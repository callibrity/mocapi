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

import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates that a JSON Schema generated from a Java bean conforms to the MCP elicitation spec's
 * {@code PrimitiveSchemaDefinition} constraints.
 */
public final class ElicitationSchemaValidator {

  private static final Set<String> ALLOWED_PRIMITIVE_TYPES =
      Set.of("string", "integer", "number", "boolean");

  private static final Set<String> ALLOWED_STRING_FORMATS =
      Set.of("email", "uri", "date", "date-time");

  private static final Set<String> DISALLOWED_KEYWORDS =
      Set.of("$ref", "$defs", "allOf", "not", "if", "then", "else");

  private ElicitationSchemaValidator() {}

  public static void validate(ObjectNode schema) {
    validateNoDisallowedKeywords(schema, null);
    JsonNode properties = schema.get("properties");
    if (properties == null || !properties.isObject()) {
      return;
    }
    properties.properties().forEach(entry -> validateProperty(entry.getKey(), entry.getValue()));
  }

  private static void validateNoDisallowedKeywords(JsonNode node, String propertyName) {
    if (!node.isObject()) {
      return;
    }
    for (String keyword : DISALLOWED_KEYWORDS) {
      if (node.has(keyword)) {
        String location =
            propertyName != null ? "Property '" + propertyName + "' uses" : "Schema uses";
        throw new McpElicitationException(
            location + " '" + keyword + "' which is not allowed in MCP elicitation schemas.");
      }
    }
  }

  private static void validateProperty(String name, JsonNode propertySchema) {
    validateNoDisallowedKeywords(propertySchema, name);

    String type = propertySchema.path("type").asString();
    if (type == null || type.isEmpty()) {
      if (propertySchema.has("oneOf") || propertySchema.has("anyOf")) {
        return;
      }
      throw new McpElicitationException(
          "Property '"
              + name
              + "' has no 'type' field and is not a recognized enum pattern."
              + " Only string, integer, number, boolean, and enum/multi-select are supported.");
    }

    if ("object".equals(type)) {
      throw new McpElicitationException(
          "Property '"
              + name
              + "' has type 'object' which is not allowed in MCP elicitation schemas."
              + " Only string, integer, number, boolean, and enum/multi-select are supported.");
    }

    if ("array".equals(type)) {
      validateArrayProperty(name, propertySchema);
      return;
    }

    if (!ALLOWED_PRIMITIVE_TYPES.contains(type)) {
      throw new McpElicitationException(
          "Property '"
              + name
              + "' has type '"
              + type
              + "' which is not allowed in MCP elicitation schemas."
              + " Only string, integer, number, boolean, and enum/multi-select are supported.");
    }

    if ("string".equals(type) && propertySchema.has("format")) {
      String format = propertySchema.get("format").asString();
      if (!ALLOWED_STRING_FORMATS.contains(format)) {
        throw new McpElicitationException(
            "Property '"
                + name
                + "' has unsupported format '"
                + format
                + "'. Allowed formats: email, uri, date, date-time.");
      }
    }
  }

  private static void validateArrayProperty(String name, JsonNode propertySchema) {
    JsonNode items = propertySchema.get("items");
    if (items == null) {
      throw new McpElicitationException(
          "Property '"
              + name
              + "' has type 'array' which does not match the MCP multi-select enum pattern.");
    }
    boolean hasEnumInItems = items.has("enum");
    boolean hasAnyOfWithConst = items.has("anyOf") && containsConstEntries(items.get("anyOf"));
    if (!hasEnumInItems && !hasAnyOfWithConst) {
      throw new McpElicitationException(
          "Property '"
              + name
              + "' has type 'array' which does not match the MCP multi-select enum pattern.");
    }
  }

  private static boolean containsConstEntries(JsonNode anyOfNode) {
    if (!anyOfNode.isArray() || anyOfNode.isEmpty()) {
      return false;
    }
    for (JsonNode entry : anyOfNode) {
      if (entry.has("const")) {
        return true;
      }
    }
    return false;
  }
}
