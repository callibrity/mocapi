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

import java.util.List;
import java.util.function.Function;

/**
 * Builder for single-select (choose one) elicitation schema properties.
 *
 * @param <T> the type of the selectable items
 */
public final class ChooseOneBuilder<T> {

  private final List<T> items;
  private final Function<T, String> valueFn;
  private Function<T, String> titleFn;
  private T defaultValue;
  private boolean rawStrings;
  private String description;
  private String title;
  private boolean required = true;

  private ChooseOneBuilder(List<T> items, Function<T, String> valueFn, boolean rawStrings) {
    this.items = items;
    this.valueFn = valueFn;
    this.rawStrings = rawStrings;
  }

  /**
   * Creates a builder from an enum type. Uses {@code name()} for values, {@code toString()} for
   * titles.
   */
  public static <E extends Enum<E>> ChooseOneBuilder<E> fromEnum(Class<E> enumType) {
    return new ChooseOneBuilder<>(List.of(enumType.getEnumConstants()), Enum::name, false);
  }

  /** Creates a builder from arbitrary items with an explicit value function. */
  public static <T> ChooseOneBuilder<T> from(List<T> items, Function<T, String> valueFn) {
    return new ChooseOneBuilder<>(items, valueFn, false);
  }

  /** Creates a builder from raw string values (produces plain enum array format). */
  public static ChooseOneBuilder<String> from(List<String> values) {
    return new ChooseOneBuilder<>(values, Function.identity(), true);
  }

  public ChooseOneBuilder<T> description(String description) {
    this.description = description;
    return this;
  }

  public ChooseOneBuilder<T> title(String title) {
    this.title = title;
    return this;
  }

  public ChooseOneBuilder<T> optional() {
    this.required = false;
    return this;
  }

  public ChooseOneBuilder<T> titleFn(Function<T, String> titleFn) {
    this.titleFn = titleFn;
    this.rawStrings = false;
    return this;
  }

  public ChooseOneBuilder<T> defaultValue(T value) {
    this.defaultValue = value;
    return this;
  }

  public PropertySchema build() {
    String defaultStr = defaultValue != null ? valueFn.apply(defaultValue) : null;
    if (rawStrings && titleFn == null) {
      List<String> values = items.stream().map(valueFn).toList();
      return new EnumPropertySchema(required, description, title, values, defaultStr);
    }
    Function<T, String> effectiveTitleFn = titleFn != null ? titleFn : Object::toString;
    List<EnumOption> options =
        items.stream()
            .map(item -> new EnumOption(valueFn.apply(item), effectiveTitleFn.apply(item)))
            .toList();
    return new TitledEnumPropertySchema(required, description, title, options, defaultStr);
  }
}
