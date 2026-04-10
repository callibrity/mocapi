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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Immutable schema for an untitled multi-select property (array of enum). */
public record MultiSelectPropertySchema(
    @JsonIgnore boolean required,
    String description,
    String title,
    EnumItemsSchema items,
    @JsonProperty("default") List<String> defaultValues,
    Integer minItems,
    Integer maxItems)
    implements PropertySchema {

  @JsonProperty("type")
  @Override
  public String type() {
    return "array";
  }
}
