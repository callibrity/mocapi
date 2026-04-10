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
import com.callibrity.mocapi.model.RequestedSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Orchestrator for constructing {@link RequestedSchema} instances. Delegates to individual schema
 * builder classes and provides a fluent API for adding properties.
 */
public final class RequestedSchemaBuilder {

  private final Map<String, PrimitiveSchemaDefinition> properties = new LinkedHashMap<>();
  private final List<String> requiredNames = new ArrayList<>();

  public RequestedSchemaBuilder() {}

  // --- String property methods ---

  public RequestedSchemaBuilder string(String name, String description) {
    StringSchemaBuilder builder = new StringSchemaBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder string(String name, String description, String defaultValue) {
    StringSchemaBuilder builder =
        new StringSchemaBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder string(
      String name, String description, Consumer<StringSchemaBuilder> customizer) {
    StringSchemaBuilder builder = new StringSchemaBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Integer property methods ---

  public RequestedSchemaBuilder integer(String name, String description) {
    IntegerSchemaBuilder builder = new IntegerSchemaBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder integer(String name, String description, int defaultValue) {
    IntegerSchemaBuilder builder =
        new IntegerSchemaBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder integer(
      String name, String description, Consumer<IntegerSchemaBuilder> customizer) {
    IntegerSchemaBuilder builder = new IntegerSchemaBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Number property methods ---

  public RequestedSchemaBuilder number(String name, String description) {
    NumberSchemaBuilder builder = new NumberSchemaBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder number(String name, String description, double defaultValue) {
    NumberSchemaBuilder builder =
        new NumberSchemaBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder number(
      String name, String description, Consumer<NumberSchemaBuilder> customizer) {
    NumberSchemaBuilder builder = new NumberSchemaBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Boolean property methods ---

  public RequestedSchemaBuilder bool(String name, String description) {
    BooleanSchemaBuilder builder = new BooleanSchemaBuilder().description(description);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder bool(String name, String description, boolean defaultValue) {
    BooleanSchemaBuilder builder =
        new BooleanSchemaBuilder().description(description).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder bool(
      String name, String description, Consumer<BooleanSchemaBuilder> customizer) {
    BooleanSchemaBuilder builder = new BooleanSchemaBuilder().description(description);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Enum class choose variants ---

  public <E extends Enum<E>> RequestedSchemaBuilder choose(String name, Class<E> enumType) {
    SingleSelectEnumSchemaBuilder<E> builder = SingleSelectEnumSchemaBuilder.fromEnum(enumType);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <E extends Enum<E>> RequestedSchemaBuilder choose(
      String name, Class<E> enumType, E defaultValue) {
    SingleSelectEnumSchemaBuilder<E> builder =
        SingleSelectEnumSchemaBuilder.fromEnum(enumType).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <E extends Enum<E>> RequestedSchemaBuilder choose(
      String name, Class<E> enumType, Consumer<SingleSelectEnumSchemaBuilder<E>> customizer) {
    SingleSelectEnumSchemaBuilder<E> builder = SingleSelectEnumSchemaBuilder.fromEnum(enumType);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Arbitrary objects choose variants ---

  public <T> RequestedSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn) {
    SingleSelectEnumSchemaBuilder<T> builder = SingleSelectEnumSchemaBuilder.from(items, valueFn);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <T> RequestedSchemaBuilder choose(
      String name, List<T> items, Function<T, String> valueFn, T defaultValue) {
    SingleSelectEnumSchemaBuilder<T> builder =
        SingleSelectEnumSchemaBuilder.from(items, valueFn).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <T> RequestedSchemaBuilder choose(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<SingleSelectEnumSchemaBuilder<T>> customizer) {
    SingleSelectEnumSchemaBuilder<T> builder = SingleSelectEnumSchemaBuilder.from(items, valueFn);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Raw string choose variants ---

  public RequestedSchemaBuilder choose(String name, List<String> values) {
    SingleSelectEnumSchemaBuilder<String> builder = SingleSelectEnumSchemaBuilder.from(values);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchemaBuilder choose(String name, List<String> values, String defaultValue) {
    SingleSelectEnumSchemaBuilder<String> builder =
        SingleSelectEnumSchemaBuilder.from(values).defaultValue(defaultValue);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Legacy choose ---

  @Deprecated
  public RequestedSchemaBuilder chooseLegacy(
      String name, List<String> values, List<String> displayNames) {
    LegacyTitledEnumSchemaBuilder builder = new LegacyTitledEnumSchemaBuilder(values, displayNames);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Enum class chooseMany variants ---

  public <E extends Enum<E>> RequestedSchemaBuilder chooseMany(String name, Class<E> enumType) {
    MultiSelectEnumSchemaBuilder<E> builder = MultiSelectEnumSchemaBuilder.fromEnum(enumType);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <E extends Enum<E>> RequestedSchemaBuilder chooseMany(
      String name, Class<E> enumType, Consumer<MultiSelectEnumSchemaBuilder<E>> customizer) {
    MultiSelectEnumSchemaBuilder<E> builder = MultiSelectEnumSchemaBuilder.fromEnum(enumType);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Arbitrary objects chooseMany variants ---

  public <T> RequestedSchemaBuilder chooseMany(
      String name, List<T> items, Function<T, String> valueFn) {
    MultiSelectEnumSchemaBuilder<T> builder = MultiSelectEnumSchemaBuilder.from(items, valueFn);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public <T> RequestedSchemaBuilder chooseMany(
      String name,
      List<T> items,
      Function<T, String> valueFn,
      Consumer<MultiSelectEnumSchemaBuilder<T>> customizer) {
    MultiSelectEnumSchemaBuilder<T> builder = MultiSelectEnumSchemaBuilder.from(items, valueFn);
    customizer.accept(builder);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  // --- Raw string chooseMany variants ---

  public RequestedSchemaBuilder chooseMany(String name, List<String> values) {
    MultiSelectEnumSchemaBuilder<String> builder = MultiSelectEnumSchemaBuilder.from(values);
    addProperty(name, builder.build(), builder.isRequired());
    return this;
  }

  public RequestedSchema build() {
    List<String> required = requiredNames.isEmpty() ? null : List.copyOf(requiredNames);
    return new RequestedSchema(new LinkedHashMap<>(properties), required);
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
