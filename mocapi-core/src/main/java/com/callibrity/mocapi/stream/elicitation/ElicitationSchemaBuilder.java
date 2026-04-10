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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Orchestrator for constructing {@link ElicitationSchema} instances. Delegates to individual
 * property builder classes and provides a fluent API for adding properties.
 */
public final class ElicitationSchemaBuilder {

  private final Map<String, PropertySchema> properties = new LinkedHashMap<>();

  ElicitationSchemaBuilder() {}

  // --- String property methods ---

  public ElicitationSchemaBuilder string(String name, String description) {
    addProperty(name, new StringPropertyBuilder().description(description).build());
    return this;
  }

  public ElicitationSchemaBuilder string(String name, String description, String defaultValue) {
    addProperty(
        name,
        new StringPropertyBuilder().description(description).defaultValue(defaultValue).build());
    return this;
  }

  public ElicitationSchemaBuilder string(
      String name, String description, Consumer<StringPropertyBuilder> customizer) {
    StringPropertyBuilder builder = new StringPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Integer property methods ---

  public ElicitationSchemaBuilder integer(String name, String description) {
    addProperty(name, new IntegerPropertyBuilder().description(description).build());
    return this;
  }

  public ElicitationSchemaBuilder integer(String name, String description, int defaultValue) {
    addProperty(
        name,
        new IntegerPropertyBuilder().description(description).defaultValue(defaultValue).build());
    return this;
  }

  public ElicitationSchemaBuilder integer(
      String name, String description, Consumer<IntegerPropertyBuilder> customizer) {
    IntegerPropertyBuilder builder = new IntegerPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Number property methods ---

  public ElicitationSchemaBuilder number(String name, String description) {
    addProperty(name, new NumberPropertyBuilder().description(description).build());
    return this;
  }

  public ElicitationSchemaBuilder number(String name, String description, double defaultValue) {
    addProperty(
        name,
        new NumberPropertyBuilder().description(description).defaultValue(defaultValue).build());
    return this;
  }

  public ElicitationSchemaBuilder number(
      String name, String description, Consumer<NumberPropertyBuilder> customizer) {
    NumberPropertyBuilder builder = new NumberPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Boolean property methods ---

  public ElicitationSchemaBuilder bool(String name, String description) {
    addProperty(name, new BooleanPropertyBuilder().description(description).build());
    return this;
  }

  public ElicitationSchemaBuilder bool(String name, String description, boolean defaultValue) {
    addProperty(
        name,
        new BooleanPropertyBuilder().description(description).defaultValue(defaultValue).build());
    return this;
  }

  public ElicitationSchemaBuilder bool(
      String name, String description, Consumer<BooleanPropertyBuilder> customizer) {
    BooleanPropertyBuilder builder = new BooleanPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Enum class choose variants ---

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(String name, Class<E> enumType) {
    addProperty(name, ChooseOneBuilder.fromEnum(enumType).build());
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(
      String name, Class<E> enumType, E defaultValue) {
    addProperty(name, ChooseOneBuilder.fromEnum(enumType).defaultValue(defaultValue).build());
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(
      String name, Class<E> enumType, Consumer<ChooseOneBuilder<E>> customizer) {
    ChooseOneBuilder<E> builder = ChooseOneBuilder.fromEnum(enumType);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Arbitrary objects choose variants ---

  public <T> ElicitationSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn) {
    addProperty(name, ChooseOneBuilder.from(items, valueFn).build());
    return this;
  }

  public <T> ElicitationSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn, T defaultValue) {
    addProperty(name, ChooseOneBuilder.from(items, valueFn).defaultValue(defaultValue).build());
    return this;
  }

  public <T> ElicitationSchemaBuilder choose(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<ChooseOneBuilder<T>> customizer) {
    ChooseOneBuilder<T> builder = ChooseOneBuilder.from(items, valueFn);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Raw string choose variants ---

  public ElicitationSchemaBuilder choose(String name, List<String> values) {
    addProperty(name, ChooseOneBuilder.from(values).build());
    return this;
  }

  public ElicitationSchemaBuilder choose(String name, List<String> values, String defaultValue) {
    addProperty(name, ChooseOneBuilder.from(values).defaultValue(defaultValue).build());
    return this;
  }

  // --- Legacy choose ---

  @Deprecated
  public ElicitationSchemaBuilder chooseLegacy(
      String name, List<String> values, List<String> displayNames) {
    addProperty(name, new ChooseLegacyBuilder(values, displayNames).build());
    return this;
  }

  // --- Enum class chooseMany variants ---

  public <E extends Enum<E>> ElicitationSchemaBuilder chooseMany(String name, Class<E> enumType) {
    addProperty(name, ChooseManyBuilder.fromEnum(enumType).build());
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder chooseMany(
      String name, Class<E> enumType, Consumer<ChooseManyBuilder<E>> customizer) {
    ChooseManyBuilder<E> builder = ChooseManyBuilder.fromEnum(enumType);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Arbitrary objects chooseMany variants ---

  public <T> ElicitationSchemaBuilder chooseMany(
      String name, List<T> items, Function<T, String> valueFn) {
    addProperty(name, ChooseManyBuilder.from(items, valueFn).build());
    return this;
  }

  public <T> ElicitationSchemaBuilder chooseMany(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<ChooseManyBuilder<T>> customizer) {
    ChooseManyBuilder<T> builder = ChooseManyBuilder.from(items, valueFn);
    customizer.accept(builder);
    addProperty(name, builder.build());
    return this;
  }

  // --- Raw string chooseMany variants ---

  public ElicitationSchemaBuilder chooseMany(String name, List<String> values) {
    addProperty(name, ChooseManyBuilder.from(values).build());
    return this;
  }

  public ElicitationSchema build() {
    return new ElicitationSchema(new LinkedHashMap<>(properties));
  }

  private void addProperty(String name, PropertySchema schema) {
    if (properties.put(name, schema) != null) {
      throw new IllegalArgumentException("Duplicate property: " + name);
    }
  }
}
