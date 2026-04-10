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
  private String description;
  private String title;
  private boolean required = true;

  public ChooseLegacyBuilder(List<String> values, List<String> displayNames) {
    this.values = values;
    this.displayNames = displayNames;
  }

  public ChooseLegacyBuilder description(String description) {
    this.description = description;
    return this;
  }

  public ChooseLegacyBuilder title(String title) {
    this.title = title;
    return this;
  }

  public ChooseLegacyBuilder optional() {
    this.required = false;
    return this;
  }

  public LegacyEnumPropertySchema build() {
    return new LegacyEnumPropertySchema(required, description, title, values, displayNames, null);
  }
}
