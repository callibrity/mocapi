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
import java.util.function.Consumer;
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

  // --- Constraint interfaces ---

  /** Constraints for string properties. */
  public interface StringConstraints {
    StringConstraints title(String title);

    StringConstraints defaultValue(String value);

    StringConstraints minLength(int min);

    StringConstraints maxLength(int max);

    StringConstraints pattern(String regex);

    /** Shorthand for {@code format("email")}. */
    StringConstraints email();

    /** Shorthand for {@code format("uri")}. */
    StringConstraints uri();

    /** Shorthand for {@code format("date")}. */
    StringConstraints date();

    /** Shorthand for {@code format("date-time")}. */
    StringConstraints dateTime();
  }

  /** Constraints for integer and number properties. */
  public interface NumericConstraints {
    NumericConstraints title(String title);

    NumericConstraints defaultValue(Number value);

    NumericConstraints minimum(Number min);

    NumericConstraints maximum(Number max);
  }

  /** Constraints for boolean properties. */
  public interface BooleanConstraints {
    BooleanConstraints title(String title);

    BooleanConstraints defaultValue(boolean value);
  }

  /** Constraints for single-select (choose) properties. */
  public interface ChoiceConstraints<T> {
    ChoiceConstraints<T> title(Function<T, String> titleFn);

    ChoiceConstraints<T> defaultValue(T value);
  }

  /** Constraints for multi-select (chooseMany) properties. */
  public interface MultiChoiceConstraints<T> {
    MultiChoiceConstraints<T> title(Function<T, String> titleFn);

    MultiChoiceConstraints<T> defaults(List<T> values);

    MultiChoiceConstraints<T> minItems(int min);

    MultiChoiceConstraints<T> maxItems(int max);
  }

  /** Builder for constructing {@link ElicitationSchema} instances. */
  public static final class Builder {

    private final Map<String, ObjectNode> properties = new LinkedHashMap<>();
    private final List<String> required = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Builder() {}

    // --- Constraint implementations ---

    private static final class StringConstraintsImpl implements StringConstraints {
      private final ObjectNode prop;

      StringConstraintsImpl(ObjectNode prop) {
        this.prop = prop;
      }

      @Override
      public StringConstraints title(String title) {
        prop.put("title", title);
        return this;
      }

      @Override
      public StringConstraints defaultValue(String value) {
        prop.put("default", value);
        return this;
      }

      @Override
      public StringConstraints minLength(int min) {
        prop.put("minLength", min);
        return this;
      }

      @Override
      public StringConstraints maxLength(int max) {
        prop.put("maxLength", max);
        return this;
      }

      @Override
      public StringConstraints pattern(String regex) {
        prop.put("pattern", regex);
        return this;
      }

      @Override
      public StringConstraints email() {
        prop.put("format", "email");
        return this;
      }

      @Override
      public StringConstraints uri() {
        prop.put("format", "uri");
        return this;
      }

      @Override
      public StringConstraints date() {
        prop.put("format", "date");
        return this;
      }

      @Override
      public StringConstraints dateTime() {
        prop.put("format", "date-time");
        return this;
      }
    }

    private static final class NumericConstraintsImpl implements NumericConstraints {
      private final ObjectNode prop;
      private final boolean isInteger;

      NumericConstraintsImpl(ObjectNode prop, boolean isInteger) {
        this.prop = prop;
        this.isInteger = isInteger;
      }

      @Override
      public NumericConstraints title(String title) {
        prop.put("title", title);
        return this;
      }

      @Override
      public NumericConstraints defaultValue(Number value) {
        if (isInteger) {
          prop.put("default", value.intValue());
        } else {
          prop.put("default", value.doubleValue());
        }
        return this;
      }

      @Override
      public NumericConstraints minimum(Number min) {
        prop.put("minimum", min.doubleValue());
        return this;
      }

      @Override
      public NumericConstraints maximum(Number max) {
        prop.put("maximum", max.doubleValue());
        return this;
      }
    }

    private static final class BooleanConstraintsImpl implements BooleanConstraints {
      private final ObjectNode prop;

      BooleanConstraintsImpl(ObjectNode prop) {
        this.prop = prop;
      }

      @Override
      public BooleanConstraints title(String title) {
        prop.put("title", title);
        return this;
      }

      @Override
      public BooleanConstraints defaultValue(boolean value) {
        prop.put("default", value);
        return this;
      }
    }

    private static final class ChoiceConstraintsImpl<T> implements ChoiceConstraints<T> {
      private Function<T, String> titleFn;
      private T defaultVal;

      @Override
      public ChoiceConstraints<T> title(Function<T, String> titleFn) {
        this.titleFn = titleFn;
        return this;
      }

      @Override
      public ChoiceConstraints<T> defaultValue(T value) {
        this.defaultVal = value;
        return this;
      }
    }

    private static final class MultiChoiceConstraintsImpl<T> implements MultiChoiceConstraints<T> {
      private Function<T, String> titleFn;
      private List<T> defaultVals;
      private Integer minItemsVal;
      private Integer maxItemsVal;

      @Override
      public MultiChoiceConstraints<T> title(Function<T, String> titleFn) {
        this.titleFn = titleFn;
        return this;
      }

      @Override
      public MultiChoiceConstraints<T> defaults(List<T> values) {
        this.defaultVals = values;
        return this;
      }

      @Override
      public MultiChoiceConstraints<T> minItems(int min) {
        this.minItemsVal = min;
        return this;
      }

      @Override
      public MultiChoiceConstraints<T> maxItems(int max) {
        this.maxItemsVal = max;
        return this;
      }
    }

    // --- String property methods ---

    public Builder string(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder string(String name, String description, String defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder string(String name, String description, Consumer<StringConstraints> customizer) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "string");
      prop.put("description", description);
      properties.put(name, prop);
      customizer.accept(new StringConstraintsImpl(prop));
      return this;
    }

    // --- Integer property methods ---

    public Builder integer(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder integer(String name, String description, int defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder integer(
        String name, String description, Consumer<NumericConstraints> customizer) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "integer");
      prop.put("description", description);
      properties.put(name, prop);
      customizer.accept(new NumericConstraintsImpl(prop, true));
      return this;
    }

    // --- Number property methods ---

    public Builder number(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder number(String name, String description, double defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder number(
        String name, String description, Consumer<NumericConstraints> customizer) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "number");
      prop.put("description", description);
      properties.put(name, prop);
      customizer.accept(new NumericConstraintsImpl(prop, false));
      return this;
    }

    // --- Boolean property methods ---

    public Builder bool(String name, String description) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      properties.put(name, prop);
      return this;
    }

    public Builder bool(String name, String description, boolean defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      prop.put("default", defaultValue);
      properties.put(name, prop);
      return this;
    }

    public Builder bool(String name, String description, Consumer<BooleanConstraints> customizer) {
      requireUniqueName(name);
      ObjectNode prop = objectMapper.createObjectNode();
      prop.put("type", "boolean");
      prop.put("description", description);
      properties.put(name, prop);
      customizer.accept(new BooleanConstraintsImpl(prop));
      return this;
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

    /** Adds an enum single-select property with a customizer for title and default. */
    public <E extends Enum<E>> Builder choose(
        String name, Class<E> enumType, Consumer<ChoiceConstraints<E>> customizer) {
      requireUniqueName(name);
      ChoiceConstraintsImpl<E> constraints = new ChoiceConstraintsImpl<>();
      customizer.accept(constraints);
      Function<E, String> titleFn =
          constraints.titleFn != null ? constraints.titleFn : Object::toString;
      ObjectNode prop = buildOneOfNode(List.of(enumType.getEnumConstants()), Enum::name, titleFn);
      if (constraints.defaultVal != null) {
        prop.put("default", constraints.defaultVal.name());
      }
      properties.put(name, prop);
      return this;
    }

    // --- Arbitrary objects choose variants ---

    /** Adds a single-select property from arbitrary items. Uses {@code toString()} for title. */
    public <T> Builder choose(String name, List<T> items, Function<T, String> valueFn) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(items, valueFn, Object::toString);
      properties.put(name, prop);
      return this;
    }

    /** Adds a single-select property from arbitrary items with a default value. */
    public <T> Builder choose(
        String name, List<T> items, Function<T, String> valueFn, T defaultValue) {
      requireUniqueName(name);
      ObjectNode prop = buildOneOfNode(items, valueFn, Object::toString);
      prop.put("default", valueFn.apply(defaultValue));
      properties.put(name, prop);
      return this;
    }

    /** Adds a single-select property from arbitrary items with a customizer. */
    public <T> Builder choose(
        String name,
        List<T> items,
        Function<T, String> valueFn,
        Consumer<ChoiceConstraints<T>> customizer) {
      requireUniqueName(name);
      ChoiceConstraintsImpl<T> constraints = new ChoiceConstraintsImpl<>();
      customizer.accept(constraints);
      Function<T, String> titleFn =
          constraints.titleFn != null ? constraints.titleFn : Object::toString;
      ObjectNode prop = buildOneOfNode(items, valueFn, titleFn);
      if (constraints.defaultVal != null) {
        prop.put("default", valueFn.apply(constraints.defaultVal));
      }
      properties.put(name, prop);
      return this;
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

    // --- Enum class chooseMany variants ---

    /** Adds an enum multi-select property with no defaults. */
    public <E extends Enum<E>> Builder chooseMany(String name, Class<E> enumType) {
      requireUniqueName(name);
      ObjectNode prop =
          buildArrayOfAnyOfNode(List.of(enumType.getEnumConstants()), Enum::name, Object::toString);
      properties.put(name, prop);
      return this;
    }

    /** Adds an enum multi-select property with a customizer. */
    public <E extends Enum<E>> Builder chooseMany(
        String name, Class<E> enumType, Consumer<MultiChoiceConstraints<E>> customizer) {
      requireUniqueName(name);
      MultiChoiceConstraintsImpl<E> constraints = new MultiChoiceConstraintsImpl<>();
      customizer.accept(constraints);
      Function<E, String> titleFn =
          constraints.titleFn != null ? constraints.titleFn : Object::toString;
      ObjectNode prop =
          buildArrayOfAnyOfNode(List.of(enumType.getEnumConstants()), Enum::name, titleFn);
      applyMultiChoiceConstraints(prop, constraints, Enum::name);
      properties.put(name, prop);
      return this;
    }

    // --- Arbitrary objects chooseMany variants ---

    /** Adds a multi-select property from arbitrary items. Uses toString() for title. */
    public <T> Builder chooseMany(String name, List<T> items, Function<T, String> valueFn) {
      requireUniqueName(name);
      ObjectNode prop = buildArrayOfAnyOfNode(items, valueFn, Object::toString);
      properties.put(name, prop);
      return this;
    }

    /** Adds a multi-select property from arbitrary items with a customizer. */
    public <T> Builder chooseMany(
        String name,
        List<T> items,
        Function<T, String> valueFn,
        Consumer<MultiChoiceConstraints<T>> customizer) {
      requireUniqueName(name);
      MultiChoiceConstraintsImpl<T> constraints = new MultiChoiceConstraintsImpl<>();
      customizer.accept(constraints);
      Function<T, String> titleFn =
          constraints.titleFn != null ? constraints.titleFn : Object::toString;
      ObjectNode prop = buildArrayOfAnyOfNode(items, valueFn, titleFn);
      applyMultiChoiceConstraints(prop, constraints, valueFn);
      properties.put(name, prop);
      return this;
    }

    // --- Raw string chooseMany variants ---

    /** Adds a multi-select property from raw string values. */
    public Builder chooseMany(String name, List<String> values) {
      requireUniqueName(name);
      ObjectNode prop = buildArrayOfAnyOfNode(values, Function.identity(), Function.identity());
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

    private <T> void applyMultiChoiceConstraints(
        ObjectNode prop, MultiChoiceConstraintsImpl<T> constraints, Function<T, String> valueFn) {
      if (constraints.defaultVals != null && !constraints.defaultVals.isEmpty()) {
        ArrayNode defaultArray = objectMapper.createArrayNode();
        for (T d : constraints.defaultVals) {
          defaultArray.add(valueFn.apply(d));
        }
        prop.set("default", defaultArray);
      }
      if (constraints.minItemsVal != null) {
        prop.put("minItems", constraints.minItemsVal);
      }
      if (constraints.maxItemsVal != null) {
        prop.put("maxItems", constraints.maxItemsVal);
      }
    }

    private void requireUniqueName(String name) {
      if (properties.containsKey(name)) {
        throw new IllegalArgumentException("Duplicate property name: " + name);
      }
    }
  }
}
