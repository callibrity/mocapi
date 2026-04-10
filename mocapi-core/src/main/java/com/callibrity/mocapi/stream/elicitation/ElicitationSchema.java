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

import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * An MCP elicitation schema. Wraps the property definitions and required fields, and renders them
 * as a JSON Schema object node.
 */
public final class ElicitationSchema {

  private final Map<String, ObjectNode> properties;
  private final List<String> required;

  ElicitationSchema(Map<String, ObjectNode> properties, List<String> required) {
    this.properties = properties;
    this.required = required;
  }

  /** Creates a new builder for constructing an {@link ElicitationSchema}. */
  public static ElicitationSchemaBuilder builder() {
    return new ElicitationSchemaBuilder();
  }

  /**
   * Renders this schema as a Jackson {@link ObjectNode}.
   *
   * @param objectMapper the ObjectMapper to use for node creation
   * @return an ObjectNode representing the JSON Schema
   */
  public ObjectNode toObjectNode(ObjectMapper objectMapper) {
    ObjectNode schema = objectMapper.createObjectNode();
    schema.put("type", "object");

    ObjectNode propsNode = objectMapper.createObjectNode();
    for (var entry : properties.entrySet()) {
      propsNode.set(entry.getKey(), entry.getValue());
    }
    schema.set("properties", propsNode);

    if (!required.isEmpty()) {
      ArrayNode requiredArray = objectMapper.createArrayNode();
      for (String name : required) {
        requiredArray.add(name);
      }
      schema.set("required", requiredArray);
    }

    return schema;
  }
}
