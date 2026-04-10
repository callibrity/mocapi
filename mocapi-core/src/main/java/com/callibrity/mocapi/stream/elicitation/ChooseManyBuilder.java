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

import com.callibrity.mocapi.model.EnumItemsSchema;
import com.callibrity.mocapi.model.EnumOption;
import com.callibrity.mocapi.model.PrimitiveSchemaDefinition;
import com.callibrity.mocapi.model.TitledEnumItemsSchema;
import com.callibrity.mocapi.model.TitledMultiSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledMultiSelectEnumSchema;
import java.util.List;
import java.util.function.Function;

/**
 * Builder for multi-select (choose many) elicitation schema properties.
 *
 * @param <T> the type of the selectable items
 */
public final class ChooseManyBuilder<T> {

  private final List<T> items;
  private final Function<T, String> valueFn;
  private Function<T, String> titleFn;
  private List<T> defaults;
  private Integer minItems;
  private Integer maxItems;
  private boolean rawStrings;
  private String description;
  private String title;
  private boolean required = true;

  private ChooseManyBuilder(List<T> items, Function<T, String> valueFn, boolean rawStrings) {
    this.items = items;
    this.valueFn = valueFn;
    this.rawStrings = rawStrings;
  }

  /**
   * Creates a builder from an enum type. Uses {@code name()} for values, {@code toString()} for
   * titles.
   */
  public static <E extends Enum<E>> ChooseManyBuilder<E> fromEnum(Class<E> enumType) {
    return new ChooseManyBuilder<>(List.of(enumType.getEnumConstants()), Enum::name, false);
  }

  /** Creates a builder from arbitrary items with an explicit value function. */
  public static <T> ChooseManyBuilder<T> from(List<T> items, Function<T, String> valueFn) {
    return new ChooseManyBuilder<>(items, valueFn, false);
  }

  /** Creates a builder from raw string values (produces plain enum array format). */
  public static ChooseManyBuilder<String> from(List<String> values) {
    return new ChooseManyBuilder<>(values, Function.identity(), true);
  }

  public ChooseManyBuilder<T> description(String description) {
    this.description = description;
    return this;
  }

  public ChooseManyBuilder<T> title(String title) {
    this.title = title;
    return this;
  }

  public ChooseManyBuilder<T> optional() {
    this.required = false;
    return this;
  }

  public ChooseManyBuilder<T> titleFn(Function<T, String> titleFn) {
    this.titleFn = titleFn;
    this.rawStrings = false;
    return this;
  }

  public ChooseManyBuilder<T> defaults(List<T> values) {
    this.defaults = values;
    return this;
  }

  public ChooseManyBuilder<T> minItems(int min) {
    this.minItems = min;
    return this;
  }

  public ChooseManyBuilder<T> maxItems(int max) {
    this.maxItems = max;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  public PrimitiveSchemaDefinition build() {
    List<String> defaultValues = null;
    if (defaults != null && !defaults.isEmpty()) {
      defaultValues = defaults.stream().map(valueFn).toList();
    }
    if (rawStrings && titleFn == null) {
      List<String> values = items.stream().map(valueFn).toList();
      EnumItemsSchema itemsSchema = new EnumItemsSchema(values);
      return new UntitledMultiSelectEnumSchema(
          title, description, minItems, maxItems, itemsSchema, defaultValues);
    }
    Function<T, String> effectiveTitleFn = titleFn != null ? titleFn : Object::toString;
    List<EnumOption> options =
        items.stream()
            .map(item -> new EnumOption(valueFn.apply(item), effectiveTitleFn.apply(item)))
            .toList();
    TitledEnumItemsSchema itemsSchema = new TitledEnumItemsSchema(options);
    return new TitledMultiSelectEnumSchema(
        title, description, minItems, maxItems, itemsSchema, defaultValues);
  }
}
