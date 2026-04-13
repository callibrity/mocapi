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

import com.callibrity.mocapi.model.EnumOption;
import com.callibrity.mocapi.model.SingleSelectEnumSchema;
import com.callibrity.mocapi.model.TitledSingleSelectEnumSchema;
import com.callibrity.mocapi.model.UntitledSingleSelectEnumSchema;
import java.util.List;
import java.util.function.Function;

/**
 * Builder for {@link SingleSelectEnumSchema} elicitation schema properties. Defaults to untitled;
 * call {@link #titled(Function)} to produce a titled variant.
 *
 * @param <T> the type of the selectable items
 */
public final class SingleSelectEnumSchemaBuilder<T> {

  private final List<T> items;
  private final Function<T, String> valueFn;
  private Function<T, String> titleFn;
  private T defaultValue;
  private String description;
  private String title;
  private boolean required = true;

  private SingleSelectEnumSchemaBuilder(List<T> items, Function<T, String> valueFn) {
    this.items = items;
    this.valueFn = valueFn;
  }

  /** Creates a builder from an enum type. Uses {@code name()} for values. */
  public static <E extends Enum<E>> SingleSelectEnumSchemaBuilder<E> fromEnum(Class<E> enumType) {
    return new SingleSelectEnumSchemaBuilder<>(List.of(enumType.getEnumConstants()), Enum::name);
  }

  /** Creates a builder from arbitrary items with an explicit value function. */
  public static <T> SingleSelectEnumSchemaBuilder<T> from(
      List<T> items, Function<T, String> valueFn) {
    return new SingleSelectEnumSchemaBuilder<>(items, valueFn);
  }

  /** Creates a builder from raw string values. */
  public static SingleSelectEnumSchemaBuilder<String> from(List<String> values) {
    return new SingleSelectEnumSchemaBuilder<>(values, Function.identity());
  }

  public SingleSelectEnumSchemaBuilder<T> description(String description) {
    this.description = description;
    return this;
  }

  public SingleSelectEnumSchemaBuilder<T> title(String title) {
    this.title = title;
    return this;
  }

  public SingleSelectEnumSchemaBuilder<T> optional() {
    this.required = false;
    return this;
  }

  /**
   * Switches this builder into titled mode. The title function is applied per item at build time.
   *
   * @param titleFn function to derive a display title from each item
   * @return this builder
   */
  public SingleSelectEnumSchemaBuilder<T> titled(Function<T, String> titleFn) {
    this.titleFn = titleFn;
    return this;
  }

  public SingleSelectEnumSchemaBuilder<T> defaultValue(T value) {
    this.defaultValue = value;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  public SingleSelectEnumSchema build() {
    String defaultStr = defaultValue != null ? valueFn.apply(defaultValue) : null;
    if (titleFn == null) {
      List<String> values = items.stream().map(valueFn).toList();
      return new UntitledSingleSelectEnumSchema(title, description, values, defaultStr);
    }
    List<EnumOption> options =
        items.stream()
            .map(item -> new EnumOption(valueFn.apply(item), titleFn.apply(item)))
            .toList();
    return new TitledSingleSelectEnumSchema(title, description, options, defaultStr);
  }
}
