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

/** Builder for boolean-typed elicitation schema properties. */
public final class BooleanPropertyBuilder {

  private final String description;
  private String title;
  private Boolean defaultValue;

  public BooleanPropertyBuilder(String description) {
    this.description = description;
  }

  public BooleanPropertyBuilder title(String title) {
    this.title = title;
    return this;
  }

  public BooleanPropertyBuilder defaultValue(boolean value) {
    this.defaultValue = value;
    return this;
  }

  public ObjectNode build(ObjectMapper objectMapper) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "boolean");
    prop.put("description", description);
    if (title != null) {
      prop.put("title", title);
    }
    if (defaultValue != null) {
      prop.put("default", defaultValue);
    }
    return prop;
  }
}
