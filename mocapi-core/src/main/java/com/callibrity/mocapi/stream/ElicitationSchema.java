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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

  /** Sub-builder for string property constraints. */
  public static final class StringPropertyBuilder {

    private final ObjectNode prop;

    StringPropertyBuilder(ObjectNode prop) {
      this.prop = prop;
    }

    public StringPropertyBuilder title(String title) {
      prop.put("title", title);
      return this;
    }

    public StringPropertyBuilder minLength(int minLength) {
      prop.put("minLength", minLength);
      return this;
    }

    public StringPropertyBuilder maxLength(int maxLength) {
      prop.put("maxLength", maxLength);
      return this;
    }

    public StringPropertyBuilder pattern(String pattern) {
      prop.put("pattern", pattern);
      return this;
    }

    public StringPropertyBuilder format(String format) {
      prop.put("format", format);
      return this;
    }
  }

  /** Sub-builder for numeric (integer/number) property constraints. */
  public static final class NumericPropertyBuilder {

    private final ObjectNode prop;

    NumericPropertyBuilder(ObjectNode prop) {
      this.prop = prop;
    }

    public NumericPropertyBuilder title(String title) {
      prop.put("title", title);
      return this;
    }

    public NumericPropertyBuilder minimum(Number minimum) {
      prop.put("minimum", minimum.doubleValue());
      return this;
    }

    public NumericPropertyBuilder maximum(Number maximum) {
      prop.put("maximum", maximum.doubleValue());
      return this;
    }
  }

  /** Sub-builder for boolean property constraints. */
  public static final class BooleanPropertyBuilder {

    private final ObjectNode prop;

    BooleanPropertyBuilder(ObjectNode prop) {
      this.prop = prop;
    }

    public BooleanPropertyBuilder title(String title) {
      prop.put("title", title);
      return this;
    }
  }

  /** Sub-builder for multi-select (array) property constraints. */
  public static final class MultiSelectPropertyBuilder {

    private final ObjectNode prop;

    MultiSelectPropertyBuilder(ObjectNode prop) {
      this.prop = prop;
    }

    public MultiSelectPropertyBuilder minItems(int minItems) {
      prop.put("minItems", minItems);
      return this;
    }

    public MultiSelectPropertyBuilder maxItems(int maxItems) {
      prop.put("maxItems", maxItems);
      return this;
    }
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

    // --- Convenience aliases that return sub-builders ---

    public StringPropertyBuilder string(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      properties.put(name, prop);
      return new StringPropertyBuilder(prop);
    }

    public StringPropertyBuilder string(String name, String description, String defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return new StringPropertyBuilder(prop);
    }

    public NumericPropertyBuilder integer(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      properties.put(name, prop);
      return new NumericPropertyBuilder(prop);
    }

    public NumericPropertyBuilder integer(String name, String description, int defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return new NumericPropertyBuilder(prop);
    }

    public NumericPropertyBuilder number(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      properties.put(name, prop);
      return new NumericPropertyBuilder(prop);
    }

    public NumericPropertyBuilder number(String name, String description, double defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return new NumericPropertyBuilder(prop);
    }

    public BooleanPropertyBuilder bool(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      properties.put(name, prop);
      return new BooleanPropertyBuilder(prop);
    }

    public BooleanPropertyBuilder bool(String name, String description, boolean defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return new BooleanPropertyBuilder(prop);
    }

    // --- Enum class choose variants ---

    /**
     * Adds an enum single-select property using {@code oneOf} with {@code const}/{@code title}
     * entries. Each constant's {@code name()} becomes the {@code const} value and its {@code
     * toString()} becomes the {@code title}.
     */
    public <E extends Enum<E>> Builder choose(String name, Class<E> enumType) {
      requireUniqueName(name);
      ObjectNode prop =
          buildOneOfNode(List.of(enumType.getEnumConstants()), Enum::name, Object::toString);
      properties.put(name, prop);
      return this;
    }

    /** Adds an enum single-select property with a default value. */
    public <E extends Enum<E>> Builder choose(String name, Class<E> enumType, E defaultValue) {
      requireUniqueName(name);
      ObjectNode prop =
          buildOneOfNode(List.of(enumType.getEnumConstants()), Enum::name, Object::toString);
      prop.put("default", defaultValue.name());
      properties.put(name, prop);
      return this;
    }

    /** Adds an enum single-select property with a custom title function. */
    public <E extends Enum<E>> Builder choose(
        String name, Class<E> enumType, Function<E, String> titleFn) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(List.of(enumType.getEnumConstants()), Enum::name, titleFn);
      properties.put(name, prop);
      return this;
    }

    /** Adds an enum single-select property with a custom title function and default value. */
    public <E extends Enum<E>> Builder choose(
        String name, Class<E> enumType, Function<E, String> titleFn, E defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(List.of(enumType.getEnumConstants()), Enum::name, titleFn);
      prop.put("default", defaultValue.name());
      properties.put(name, prop);
      return this;
    }

    // --- Enum class chooseMany variants ---

    /** Adds an enum multi-select property with no defaults. */
    public <E extends Enum<E>> MultiSelectPropertyBuilder chooseMany(
        String name, Class<E> enumType) {
      return chooseManyEnum(name, enumType, Object::toString, List.of());
    }

    /** Adds an enum multi-select property with a custom title function. */
    public <E extends Enum<E>> MultiSelectPropertyBuilder chooseMany(
        String name, Class<E> enumType, Function<E, String> titleFn) {
      return chooseManyEnum(name, enumType, titleFn, List.of());
    }

    /** Adds an enum multi-select property with defaults as a List. */
    public <E extends Enum<E>> MultiSelectPropertyBuilder chooseMany(
        String name, Class<E> enumType, List<E> defaults) {
      return chooseManyEnum(name, enumType, Object::toString, defaults);
    }

    /** Adds an enum multi-select property with a custom title function and defaults as a List. */
    public <E extends Enum<E>> MultiSelectPropertyBuilder chooseMany(
        String name, Class<E> enumType, Function<E, String> titleFn, List<E> defaults) {
      return chooseManyEnum(name, enumType, titleFn, defaults);
    }

    private <E extends Enum<E>> MultiSelectPropertyBuilder chooseManyEnum(
        String name, Class<E> enumType, Function<E, String> titleFn, List<E> defaults) {
      MultiSelectPropertyBuilder builder =
          chooseMany(name, List.of(enumType.getEnumConstants()), Enum::name, titleFn);
      if (!defaults.isEmpty()) {
        ArrayNode defaultArray = objectMapper.createArrayNode();
        for (E d : defaults) {
          defaultArray.add(d.name());
        }
        ((ObjectNode) properties.get(name)).set("default", defaultArray);
      }
      return builder;
    }

    // --- Arbitrary objects choose variants ---

    /** Adds a single-select property from arbitrary items with value and title functions. */
    public <T> Builder choose(
        String name, List<T> items, Function<T, String> valueFn, Function<T, String> titleFn) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(items, valueFn, titleFn);
      properties.put(name, prop);
      return this;
    }

    /** Adds a single-select property from arbitrary items with a default value. */
    public <T> Builder choose(
        String name,
        List<T> items,
        Function<T, String> valueFn,
        Function<T, String> titleFn,
        T defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(items, valueFn, titleFn);
      prop.put("default", valueFn.apply(defaultValue));
      properties.put(name, prop);
      return this;
    }

    /** Adds a single-select property from arbitrary items. Uses {@code toString()} for title. */
    public <T> Builder choose(String name, List<T> items, Function<T, String> valueFn) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(items, valueFn, Object::toString);
      properties.put(name, prop);
      return this;
    }

    // --- Arbitrary objects chooseMany variants ---

    /** Adds a multi-select property from arbitrary items with value and title functions. */
    public <T> MultiSelectPropertyBuilder chooseMany(
        String name, List<T> items, Function<T, String> valueFn, Function<T, String> titleFn) {
      requireUniqueName(name);
      ObjectNode prop = buildArrayOfAnyOfNode(items, valueFn, titleFn);
      properties.put(name, prop);
      return new MultiSelectPropertyBuilder(prop);
    }

    /** Adds a multi-select property from arbitrary items. Uses toString() for title. */
    public <T> MultiSelectPropertyBuilder chooseMany(
        String name, List<T> items, Function<T, String> valueFn) {
      requireUniqueName(name);
      ObjectNode prop = buildArrayOfAnyOfNode(items, valueFn, Object::toString);
      properties.put(name, prop);
      return new MultiSelectPropertyBuilder(prop);
    }

    /**
     * Adds a multi-select property from arbitrary items with title function and varargs defaults.
     */
    @SafeVarargs
    public final <T> MultiSelectPropertyBuilder chooseMany(
        String name,
        List<T> items,
        Function<T, String> valueFn,
        Function<T, String> titleFn,
        T... defaults) {
      return chooseManyWithDefaults(name, items, valueFn, titleFn, Arrays.asList(defaults));
    }

    /** Adds a multi-select property from arbitrary items with defaults as a List. */
    public <T> MultiSelectPropertyBuilder chooseMany(
        String name, List<T> items, Function<T, String> valueFn, List<T> defaults) {
      return chooseManyWithDefaults(name, items, valueFn, Object::toString, defaults);
    }

    /**
     * Adds a multi-select property from arbitrary items with title function and defaults as a List.
     */
    public <T> MultiSelectPropertyBuilder chooseMany(
        String name,
        List<T> items,
        Function<T, String> valueFn,
        Function<T, String> titleFn,
        List<T> defaults) {
      return chooseManyWithDefaults(name, items, valueFn, titleFn, defaults);
    }

    private <T> MultiSelectPropertyBuilder chooseManyWithDefaults(
        String name,
        List<T> items,
        Function<T, String> valueFn,
        Function<T, String> titleFn,
        List<T> defaults) {
      MultiSelectPropertyBuilder builder = chooseMany(name, items, valueFn, titleFn);
      if (!defaults.isEmpty()) {
        ArrayNode defaultArray = objectMapper.createArrayNode();
        for (T d : defaults) {
          defaultArray.add(valueFn.apply(d));
        }
        ((ObjectNode) properties.get(name)).set("default", defaultArray);
      }
      return builder;
    }

    // --- Raw string choose variants ---

    /** Adds a single-select property from raw string values (title = value). */
    public Builder choose(String name, List<String> values) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(values, Function.identity(), Function.identity());
      properties.put(name, prop);
      return this;
    }

    /** Adds a single-select property from raw string values with a default. */
    public Builder choose(String name, List<String> values, String defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(values, Function.identity(), Function.identity());
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    /** Adds a multi-select property from raw string values with varargs defaults. */
    public MultiSelectPropertyBuilder chooseMany(
        String name, List<String> values, String... defaults) {
      requireUniqueName(name);
      ObjectNode prop = buildArrayOfAnyOfNode(values, Function.identity(), Function.identity());
      if (defaults.length > 0) {
        ArrayNode defaultArray = objectMapper.createArrayNode();
        for (String d : defaults) {
          defaultArray.add(d);
        }
        prop.set("default", defaultArray);
      }
      properties.put(name, prop);
      return new MultiSelectPropertyBuilder(prop);
    }

    /** Adds a multi-select property from raw string values with defaults as a List. */
    public MultiSelectPropertyBuilder chooseMany(
        String name, List<String> values, List<String> defaults) {
      requireUniqueName(name);
      ObjectNode prop = buildArrayOfAnyOfNode(values, Function.identity(), Function.identity());
      if (!defaults.isEmpty()) {
        ArrayNode defaultArray = objectMapper.createArrayNode();
        for (String d : defaults) {
          defaultArray.add(d);
        }
        prop.set("default", defaultArray);
      }
      properties.put(name, prop);
      return new MultiSelectPropertyBuilder(prop);
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

    private <T> ObjectNode buildOneOfNode(
        List<T> items, Function<T, String> valueFn, Function<T, String> titleFn) {
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      ArrayNode oneOf = objectMapper.createArrayNode();
      for (T item : items) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("const", valueFn.apply(item));
        option.put("title", titleFn.apply(item));
        oneOf.add(option);
      }
      prop.set("oneOf", oneOf);
      return prop;
    }

    private <T> ObjectNode buildArrayOfAnyOfNode(
        List<T> items, Function<T, String> valueFn, Function<T, String> titleFn) {
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "array");
      ObjectNode itemsNode = objectMapper.createObjectNode();
      itemsNode.put("type", "string");
      ArrayNode anyOf = objectMapper.createArrayNode();
      for (T item : items) {
        ObjectNode option = objectMapper.createObjectNode();
        option.put("const", valueFn.apply(item));
        option.put("title", titleFn.apply(item));
        anyOf.add(option);
      }
      itemsNode.set("anyOf", anyOf);
      prop.set("items", itemsNode);
      return prop;
    }

    private void requireUniqueName(String name) {
      if (properties.containsKey(name)) {
        throw new IllegalArgumentException("Duplicate property name: " + name);
      }
    }
  }
}
