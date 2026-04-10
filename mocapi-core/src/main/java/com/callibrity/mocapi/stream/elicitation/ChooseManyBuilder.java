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

  public ObjectNode build(ObjectMapper objectMapper) {
    ObjectNode prop;
    if (rawStrings && titleFn == null) {
      prop = buildArrayOfEnumNode(objectMapper);
    } else {
      Function<T, String> effectiveTitleFn = titleFn != null ? titleFn : Object::toString;
      prop = buildArrayOfAnyOfNode(objectMapper, effectiveTitleFn);
    }
    applyConstraints(prop, objectMapper);
    return prop;
  }

  private ObjectNode buildArrayOfEnumNode(ObjectMapper objectMapper) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "array");
    ObjectNode itemsNode = objectMapper.createObjectNode();
    itemsNode.put("type", "string");
    ArrayNode enumArray = objectMapper.createArrayNode();
    for (T item : items) {
      enumArray.add(valueFn.apply(item));
    }
    itemsNode.set("enum", enumArray);
    prop.set("items", itemsNode);
    return prop;
  }

  private ObjectNode buildArrayOfAnyOfNode(
      ObjectMapper objectMapper, Function<T, String> effectiveTitleFn) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "array");
    ObjectNode itemsNode = objectMapper.createObjectNode();
    itemsNode.put("type", "string");
    ArrayNode anyOf = objectMapper.createArrayNode();
    for (T item : items) {
      ObjectNode option = objectMapper.createObjectNode();
      option.put("const", valueFn.apply(item));
      option.put("title", effectiveTitleFn.apply(item));
      anyOf.add(option);
    }
    itemsNode.set("anyOf", anyOf);
    prop.set("items", itemsNode);
    return prop;
  }

  private void applyConstraints(ObjectNode prop, ObjectMapper objectMapper) {
    if (defaults != null && !defaults.isEmpty()) {
      ArrayNode defaultArray = objectMapper.createArrayNode();
      for (T d : defaults) {
        defaultArray.add(valueFn.apply(d));
      }
      prop.set("default", defaultArray);
    }
    if (minItems != null) {
      prop.put("minItems", minItems);
    }
    if (maxItems != null) {
      prop.put("maxItems", maxItems);
    }
  }
}
