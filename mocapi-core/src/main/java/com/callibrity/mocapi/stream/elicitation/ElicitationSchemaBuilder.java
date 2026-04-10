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

import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import java.util.ArrayList;
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

  private final Map<String, PrimitiveSchemaDefinition> properties = new LinkedHashMap<>();
  private final List<String> requiredNames = new ArrayList<>();

  ElicitationSchemaBuilder() {}

  // --- String property methods ---

  public ElicitationSchemaBuilder string(String name, String description) {
    StringPropertyBuilder builder = new StringPropertyBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder string(String name, String description, String defaultValue) {
    StringPropertyBuilder builder =
        new StringPropertyBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder string(
      String name, String description, Consumer<StringPropertyBuilder> customizer) {
    StringPropertyBuilder builder = new StringPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Integer property methods ---

  public ElicitationSchemaBuilder integer(String name, String description) {
    IntegerPropertyBuilder builder = new IntegerPropertyBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder integer(String name, String description, int defaultValue) {
    IntegerPropertyBuilder builder =
        new IntegerPropertyBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder integer(
      String name, String description, Consumer<IntegerPropertyBuilder> customizer) {
    IntegerPropertyBuilder builder = new IntegerPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Number property methods ---

  public ElicitationSchemaBuilder number(String name, String description) {
    NumberPropertyBuilder builder = new NumberPropertyBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder number(String name, String description, double defaultValue) {
    NumberPropertyBuilder builder =
        new NumberPropertyBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder number(
      String name, String description, Consumer<NumberPropertyBuilder> customizer) {
    NumberPropertyBuilder builder = new NumberPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Boolean property methods ---

  public ElicitationSchemaBuilder bool(String name, String description) {
    BooleanPropertyBuilder builder = new BooleanPropertyBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder bool(String name, String description, boolean defaultValue) {
    BooleanPropertyBuilder builder =
        new BooleanPropertyBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder bool(
      String name, String description, Consumer<BooleanPropertyBuilder> customizer) {
    BooleanPropertyBuilder builder = new BooleanPropertyBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Enum class choose variants ---

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(String name, Class<E> enumType) {
    ChooseOneBuilder<E> builder = ChooseOneBuilder.fromEnum(enumType);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(
      String name, Class<E> enumType, E defaultValue) {
    ChooseOneBuilder<E> builder = ChooseOneBuilder.fromEnum(enumType).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder choose(
      String name, Class<E> enumType, Consumer<ChooseOneBuilder<E>> customizer) {
    ChooseOneBuilder<E> builder = ChooseOneBuilder.fromEnum(enumType);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Arbitrary objects choose variants ---

  public <T> ElicitationSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn) {
    ChooseOneBuilder<T> builder = ChooseOneBuilder.from(items, valueFn);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <T> ElicitationSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn, T defaultValue) {
    ChooseOneBuilder<T> builder = ChooseOneBuilder.from(items, valueFn).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <T> ElicitationSchemaBuilder choose(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<ChooseOneBuilder<T>> customizer) {
    ChooseOneBuilder<T> builder = ChooseOneBuilder.from(items, valueFn);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Raw string choose variants ---

  public ElicitationSchemaBuilder choose(String name, List<String> values) {
    ChooseOneBuilder<String> builder = ChooseOneBuilder.from(values);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchemaBuilder choose(String name, List<String> values, String defaultValue) {
    ChooseOneBuilder<String> builder = ChooseOneBuilder.from(values).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Legacy choose ---

  @Deprecated
  public ElicitationSchemaBuilder chooseLegacy(
      String name, List<String> values, List<String> displayNames) {
    ChooseLegacyBuilder builder = new ChooseLegacyBuilder(values, displayNames);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Enum class chooseMany variants ---

  public <E extends Enum<E>> ElicitationSchemaBuilder chooseMany(String name, Class<E> enumType) {
    ChooseManyBuilder<E> builder = ChooseManyBuilder.fromEnum(enumType);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <E extends Enum<E>> ElicitationSchemaBuilder chooseMany(
      String name, Class<E> enumType, Consumer<ChooseManyBuilder<E>> customizer) {
    ChooseManyBuilder<E> builder = ChooseManyBuilder.fromEnum(enumType);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Arbitrary objects chooseMany variants ---

  public <T> ElicitationSchemaBuilder chooseMany(
      String name, List<T> items, Function<T, String> valueFn) {
    ChooseManyBuilder<T> builder = ChooseManyBuilder.from(items, valueFn);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <T> ElicitationSchemaBuilder chooseMany(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<ChooseManyBuilder<T>> customizer) {
    ChooseManyBuilder<T> builder = ChooseManyBuilder.from(items, valueFn);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Raw string chooseMany variants ---

  public ElicitationSchemaBuilder chooseMany(String name, List<String> values) {
    ChooseManyBuilder<String> builder = ChooseManyBuilder.from(values);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public ElicitationSchema build() {
    return new ElicitationSchema(new LinkedHashMap<>(properties), new ArrayList<>(requiredNames));
  }

  private void addProperty(String name, PrimitiveSchemaDefinition schema, boolean required) {
    if (properties.put(name, schema) != null) {
      throw new IllegalArgumentException("Duplicate property: " + name);
    }
    if (required) {
      requiredNames.add(name);
    }
  }
}
