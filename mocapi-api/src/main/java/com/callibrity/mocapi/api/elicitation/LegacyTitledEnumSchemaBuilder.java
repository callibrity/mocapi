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

import com.callibrity.mocapi.model.LegacyTitledEnumSchema;
import java.util.List;

/**
 * Builder for the deprecated {@link LegacyTitledEnumSchema} ({@code enum} + {@code enumNames}
 * format). Retained for conformance testing.
 *
 * @deprecated Use {@link SingleSelectEnumSchemaBuilder} instead.
 */
@Deprecated(since = "0.0.1", forRemoval = false)
public final class LegacyTitledEnumSchemaBuilder {

  private final List<String> values;
  private final List<String> displayNames;
  private String description;
  private String title;
  private boolean required = true;

  public LegacyTitledEnumSchemaBuilder(List<String> values, List<String> displayNames) {
    this.values = values;
    this.displayNames = displayNames;
  }

  public LegacyTitledEnumSchemaBuilder description(String description) {
    this.description = description;
    return this;
  }

  public LegacyTitledEnumSchemaBuilder title(String title) {
    this.title = title;
    return this;
  }

  public LegacyTitledEnumSchemaBuilder optional() {
    this.required = false;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  @SuppressWarnings("deprecation") // Deprecated builder legitimately constructs the deprecated
  // LegacyTitledEnumSchema per MCP spec
  public LegacyTitledEnumSchema build() {
    return new LegacyTitledEnumSchema(title, description, values, displayNames, null);
  }
}
