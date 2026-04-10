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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builder for the deprecated {@code enum} + {@code enumNames} single-select format. Retained for
 * conformance testing.
 *
 * @deprecated Use {@link ChooseOneBuilder} instead.
 */
@Deprecated
public final class ChooseLegacyBuilder {

  private final List<String> values;
  private final List<String> displayNames;

  public ChooseLegacyBuilder(List<String> values, List<String> displayNames) {
    this.values = values;
    this.displayNames = displayNames;
  }

  public ObjectNode build(ObjectMapper objectMapper) {
    ObjectNode prop = objectMapper.createObjectNode();
    prop.put("type", "string");
    ArrayNode enumArray = objectMapper.createArrayNode();
    for (String value : values) {
      enumArray.add(value);
    }
    prop.set("enum", enumArray);
    ArrayNode enumNames = objectMapper.createArrayNode();
    for (String dn : displayNames) {
      enumNames.add(dn);
    }
    prop.set("enumNames", enumNames);
    return prop;
  }
}
