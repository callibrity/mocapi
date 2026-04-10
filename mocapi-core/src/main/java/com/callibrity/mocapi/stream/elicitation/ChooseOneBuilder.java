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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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

  public ChooseOneBuilder<T> titleFn(Function<T, String> titleFn) {
    this.titleFn = titleFn;
    this.rawStrings = false;
    return this;
  }

  public ChooseOneBuilder<T> defaultValue(T value) {
    this.defaultValue = value;
    return this;
  }

  public ObjectNode build(ObjectMapper objectMapper) {
    ObjectNode prop;
    if (rawStrings && titleFn == null) {
      prop = buildEnumNode(objectMapper);
    } else {
      Function<T, String> effectiveTitleFn = titleFn != null ? titleFn : Object::toString;
      prop = buildOneOfNode(objectMapper, effectiveTitleFn);
    }
    if (defaultValue != null) {
      prop.put("default", valueFn.apply(defaultValue));
    }
    return prop;
  }

  private ObjectNode buildEnumNode(ObjectMapper objectMapper) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "string");
    ArrayNode enumArray = objectMapper.createArrayNode();
    for (T item : items) {
      enumArray.add(valueFn.apply(item));
    }
    prop.set("enum", enumArray);
    return prop;
  }

  private ObjectNode buildOneOfNode(
      ObjectMapper objectMapper, Function<T, String> effectiveTitleFn) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "string");
    ArrayNode oneOf = objectMapper.createArrayNode();
    for (T item : items) {
      ObjectNode option = objectMapper.createObjectNode();
      option.put("const", valueFn.apply(item));
      option.put("title", effectiveTitleFn.apply(item));
      oneOf.add(option);
    }
    prop.set("oneOf", oneOf);
    return prop;
  }
}
