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
package com.callibrity.mocapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Legacy titled enum schema variant using the {@code enum} + {@code enumNames} array form.
 *
 * @deprecated Use {@link TitledSingleSelectEnumSchema} or {@link TitledMultiSelectEnumSchema}
 *     instead. This variant exists for backward compatibility with pre-2025-11-25 MCP clients that
 *     use the {@code enumNames} array form. Not scheduled for removal because the MCP spec still
 *     defines this as a valid backward-compatibility variant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Deprecated(since = "0.0.1", forRemoval = false)
@SuppressWarnings("java:S1874")
public record LegacyTitledEnumSchema(
    String title,
    String description,
    @JsonProperty("enum") List<String> values,
    List<String> enumNames,
    @JsonProperty("default") String defaultValue)
    implements EnumSchema {
  @JsonProperty("type")
  public String type() {
    return "string";
  }
}
