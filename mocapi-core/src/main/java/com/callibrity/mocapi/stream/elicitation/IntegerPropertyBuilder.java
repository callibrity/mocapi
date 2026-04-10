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

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Builder for integer-typed elicitation schema properties. */
public final class IntegerPropertyBuilder {

  private final String description;
  private String title;
  private Integer defaultValue;
  private Number minimum;
  private Number maximum;

  public IntegerPropertyBuilder(String description) {
    this.description = description;
  }

  public IntegerPropertyBuilder title(String title) {
    this.title = title;
    return this;
  }

  public IntegerPropertyBuilder defaultValue(int value) {
    this.defaultValue = value;
    return this;
  }

  public IntegerPropertyBuilder minimum(Number min) {
    this.minimum = min;
    return this;
  }

  public IntegerPropertyBuilder maximum(Number max) {
    this.maximum = max;
    return this;
  }

  public ObjectNode build(ObjectMapper objectMapper) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "integer");
    prop.put("description", description);
    if (title != null) {
      prop.put("title", title);
    }
    if (defaultValue != null) {
      prop.put("default", defaultValue);
    }
    if (minimum != null) {
      prop.put("minimum", minimum.doubleValue());
    }
    if (maximum != null) {
      prop.put("maximum", maximum.doubleValue());
    }
    return prop;
  }
}
