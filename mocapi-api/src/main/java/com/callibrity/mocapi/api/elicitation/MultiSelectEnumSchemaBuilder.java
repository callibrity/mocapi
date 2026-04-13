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

import com.callibrity.mocapi.model.EnumItemsSchema;
import com.callibrity.mocapi.model.EnumOption;
import com.callibrity.mocapi.model.MultiSelectEnumSchema;
import com.callibrity.mocapi.model.TitledEnumItemsSchema;
import com.callibrity.mocapi.model.TitledMultiSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledMultiSelectEnumSchema;
import java.util.List;
import java.util.function.Function;

/**
 * Builder for {@link MultiSelectEnumSchema} elicitation schema properties. Defaults to untitled;
 * call {@link #titled(Function)} to produce a titled variant.
 *
 * @param <T> the type of the selectable items
 */
public final class MultiSelectEnumSchemaBuilder<T> {

  private final List<T> items;
  private final Function<T, String> valueFn;
  private Function<T, String> titleFn;
  private List<T> defaults;
  private Integer minItems;
  private Integer maxItems;
  private String description;
  private String title;
  private boolean required = true;

  private MultiSelectEnumSchemaBuilder(List<T> items, Function<T, String> valueFn) {
    this.items = items;
    this.valueFn = valueFn;
  }

  /** Creates a builder from an enum type. Uses {@code name()} for values. */
  public static <E extends Enum<E>> MultiSelectEnumSchemaBuilder<E> fromEnum(Class<E> enumType) {
    return new MultiSelectEnumSchemaBuilder<>(List.of(enumType.getEnumConstants()), Enum::name);
  }

  /** Creates a builder from arbitrary items with an explicit value function. */
  public static <T> MultiSelectEnumSchemaBuilder<T> from(
      List<T> items, Function<T, String> valueFn) {
    return new MultiSelectEnumSchemaBuilder<>(items, valueFn);
  }

  /** Creates a builder from raw string values. */
  public static MultiSelectEnumSchemaBuilder<String> from(List<String> values) {
    return new MultiSelectEnumSchemaBuilder<>(values, Function.identity());
  }

  public MultiSelectEnumSchemaBuilder<T> description(String description) {
    this.description = description;
    return this;
  }

  public MultiSelectEnumSchemaBuilder<T> title(String title) {
    this.title = title;
    return this;
  }

  public MultiSelectEnumSchemaBuilder<T> optional() {
    this.required = false;
    return this;
  }

  /**
   * Switches this builder into titled mode. The title function is applied per item at build time.
   *
   * @param titleFn function to derive a display title from each item
   * @return this builder
   */
  public MultiSelectEnumSchemaBuilder<T> titled(Function<T, String> titleFn) {
    this.titleFn = titleFn;
    return this;
  }

  public MultiSelectEnumSchemaBuilder<T> defaults(List<T> values) {
    this.defaults = values;
    return this;
  }

  public MultiSelectEnumSchemaBuilder<T> minItems(int min) {
    this.minItems = min;
    return this;
  }

  public MultiSelectEnumSchemaBuilder<T> maxItems(int max) {
    this.maxItems = max;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  public MultiSelectEnumSchema build() {
    List<String> defaultValues = null;
    if (defaults != null && !defaults.isEmpty()) {
      defaultValues = defaults.stream().map(valueFn).toList();
    }
    if (titleFn == null) {
      List<String> values = items.stream().map(valueFn).toList();
      EnumItemsSchema itemsSchema = new EnumItemsSchema(values);
      return new UntitledMultiSelectEnumSchema(
          title, description, minItems, maxItems, itemsSchema, defaultValues);
    }
    List<EnumOption> options =
        items.stream()
            .map(item -> new EnumOption(valueFn.apply(item), titleFn.apply(item)))
            .toList();
    TitledEnumItemsSchema itemsSchema = new TitledEnumItemsSchema(options);
    return new TitledMultiSelectEnumSchema(
        title, description, minItems, maxItems, itemsSchema, defaultValues);
  }
}
