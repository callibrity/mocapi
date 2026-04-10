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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Orchestrator for constructing {@link ElicitationSchema} instances. Delegates to individual
 * property builder classes and provides a fluent API for adding properties.
 */
public final class ElicitationSchemaBuilder {

  private final Map<String, ObjectNode> properties = new LinkedHashMap<>();
  private final List<String> required = new ArrayList<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  ElicitationSchemaBuilder() {}

  // --- String property methods ---

  public ElicitationSchemaBuilder string(String name, String description) {
    requireUniqueName(name);
    properties.put(name, new StringPropertyBuilder(description).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder string(String name, String description, String defaultValue) {
    requireUniqueName(name);
    properties.put(
        name,
        new StringPropertyBuilder(description).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder string(
      String name, String description, Consumer<StringPropertyBuilder> customizer) {
    requireUniqueName(name);
    StringPropertyBuilder builder = new StringPropertyBuilder(description);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Integer property methods ---

  public ElicitationSchemaBuilder integer(String name, String description) {
    requireUniqueName(name);
    properties.put(name, new IntegerPropertyBuilder(description).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder integer(String name, String description, int defaultValue) {
    requireUniqueName(name);
    properties.put(
        name,
        new IntegerPropertyBuilder(description).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder integer(
      String name, String description, Consumer<IntegerPropertyBuilder> customizer) {
    requireUniqueName(name);
    IntegerPropertyBuilder builder = new IntegerPropertyBuilder(description);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Number property methods ---

  public ElicitationSchemaBuilder number(String name, String description) {
    requireUniqueName(name);
    properties.put(name, new NumberPropertyBuilder(description).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder number(String name, String description, double defaultValue) {
    requireUniqueName(name);
    properties.put(
        name,
        new NumberPropertyBuilder(description).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder number(
      String name, String description, Consumer<NumberPropertyBuilder> customizer) {
    requireUniqueName(name);
    NumberPropertyBuilder builder = new NumberPropertyBuilder(description);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Boolean property methods ---

  public ElicitationSchemaBuilder bool(String name, String description) {
    requireUniqueName(name);
    properties.put(name, new BooleanPropertyBuilder(description).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder bool(String name, String description, boolean defaultValue) {
    requireUniqueName(name);
    properties.put(
        name,
        new BooleanPropertyBuilder(description).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder bool(
      String name, String description, Consumer<BooleanPropertyBuilder> customizer) {
    requireUniqueName(name);
    BooleanPropertyBuilder builder = new BooleanPropertyBuilder(description);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Enum class choose variants ---

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(String name, Class<E> enumType) {
    requireUniqueName(name);
    properties.put(name, ChooseOneBuilder.fromEnum(enumType).build(objectMapper));
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(
      String name, Class<E> enumType, E defaultValue) {
    requireUniqueName(name);
    properties.put(
        name, ChooseOneBuilder.fromEnum(enumType).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(
      String name, Class<E> enumType, Consumer<ChooseOneBuilder<E>> customizer) {
    requireUniqueName(name);
    ChooseOneBuilder<E> builder = ChooseOneBuilder.fromEnum(enumType);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Arbitrary objects choose variants ---

  public <T> ElicitationSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn) {
    requireUniqueName(name);
    properties.put(name, ChooseOneBuilder.from(items, valueFn).build(objectMapper));
    return this;
  }

  public <T> ElicitationSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn, T defaultValue) {
    requireUniqueName(name);
    properties.put(
        name, ChooseOneBuilder.from(items, valueFn).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  public <T> ElicitationSchemaBuilder choose(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<ChooseOneBuilder<T>> customizer) {
    requireUniqueName(name);
    ChooseOneBuilder<T> builder = ChooseOneBuilder.from(items, valueFn);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Raw string choose variants ---

  public ElicitationSchemaBuilder choose(String name, List<String> values) {
    requireUniqueName(name);
    properties.put(name, ChooseOneBuilder.from(values).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder choose(String name, List<String> values, String defaultValue) {
    requireUniqueName(name);
    properties.put(
        name, ChooseOneBuilder.from(values).defaultValue(defaultValue).build(objectMapper));
    return this;
  }

  // --- Legacy choose ---

  @Deprecated
  public ElicitationSchemaBuilder chooseLegacy(
      String name, List<String> values, List<String> displayNames) {
    requireUniqueName(name);
    properties.put(name, new ChooseLegacyBuilder(values, displayNames).build(objectMapper));
    return this;
  }

  // --- Enum class chooseMany variants ---

  public <E extends Enum<E>> ElicitationSchemaBuilder chooseMany(String name, Class<E> enumType) {
    requireUniqueName(name);
    properties.put(name, ChooseManyBuilder.fromEnum(enumType).build(objectMapper));
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder chooseMany(
      String name, Class<E> enumType, Consumer<ChooseManyBuilder<E>> customizer) {
    requireUniqueName(name);
    ChooseManyBuilder<E> builder = ChooseManyBuilder.fromEnum(enumType);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Arbitrary objects chooseMany variants ---

  public <T> ElicitationSchemaBuilder chooseMany(
      String name, List<T> items, Function<T, String> valueFn) {
    requireUniqueName(name);
    properties.put(name, ChooseManyBuilder.from(items, valueFn).build(objectMapper));
    return this;
  }

  public <T> ElicitationSchemaBuilder chooseMany(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<ChooseManyBuilder<T>> customizer) {
    requireUniqueName(name);
    ChooseManyBuilder<T> builder = ChooseManyBuilder.from(items, valueFn);
    customizer.accept(builder);
    properties.put(name, builder.build(objectMapper));
    return this;
  }

  // --- Raw string chooseMany variants ---

  public ElicitationSchemaBuilder chooseMany(String name, List<String> values) {
    requireUniqueName(name);
    properties.put(name, ChooseManyBuilder.from(values).build(objectMapper));
    return this;
  }

  public ElicitationSchemaBuilder required(String... names) {
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
