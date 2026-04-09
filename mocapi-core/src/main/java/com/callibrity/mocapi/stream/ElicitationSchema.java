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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A type-safe builder for MCP elicitation schemas. Enforces the spec's allowed property types and
 * always produces an object-typed JSON Schema.
 */
public final class ElicitationSchema {

  private final Map<String, ObjectNode> properties;
  private final List<String> required;

  private ElicitationSchema(Map<String, ObjectNode> properties, List<String> required) {
    this.properties = properties;
    this.required = required;
  }

  /** Creates a new builder for constructing an {@link ElicitationSchema}. */
  public static Builder builder() {
    return new Builder();
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

  /** Builder for constructing {@link ElicitationSchema} instances. */
  public static final class Builder {

    private final Map<String, ObjectNode> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Builder() {}

    public Builder stringProperty(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder stringProperty(String name, String description, String defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder integerProperty(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder integerProperty(String name, String description, int defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder numberProperty(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder numberProperty(String name, String description, double defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder booleanProperty(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder booleanProperty(String name, String description, boolean defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder enumProperty(String name, List<String> values) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      ArrayNode enumArray = objectMapper.createArrayNode();
      for (String v : values) {
        enumArray.add(v);
      }
      prop.set("enum", enumArray);
      properties.put(name, prop);
      return this;
    }

    public Builder titledEnumProperty(String name, List<TitledValue> values) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      ArrayNode oneOf = objectMapper.createArrayNode();
      for (TitledValue tv : values) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("const", tv.value());
        option.put("title", tv.title());
        oneOf.add(option);
      }
      prop.set("oneOf", oneOf);
      properties.put(name, prop);
      return this;
    }

    public Builder multiSelectProperty(String name, List<String> values) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "array");
      ObjectNode items = objectMapper.createObjectNode();
      items.put("type", "string");
      ArrayNode enumArray = objectMapper.createArrayNode();
      for (String v : values) {
        enumArray.add(v);
      }
      items.set("enum", enumArray);
      prop.set("items", items);
      properties.put(name, prop);
      return this;
    }

    public Builder titledMultiSelectProperty(String name, List<TitledValue> values) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "array");
      ObjectNode items = objectMapper.createObjectNode();
      ArrayNode oneOf = objectMapper.createArrayNode();
      for (TitledValue tv : values) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("const", tv.value());
        option.put("title", tv.title());
        oneOf.add(option);
      }
      items.set("oneOf", oneOf);
      prop.set("items", items);
      properties.put(name, prop);
      return this;
    }

    public Builder required(String... names) {
      for (String name : names) {
        if (!required.contains(name)) {
          required.add(name);
        }
      }
      return this;
    }

    public ElicitationSchema build() {
      return new ElicitationSchema(new LinkedHashMap<>(properties), new ArrayList<>(required));
    }

    private void requireUniqueName(String name) {
      if (properties.containsKey(name)) {
        throw new IllegalArgumentException("Duplicate property name: " + name);
      }
    }
  }
}
