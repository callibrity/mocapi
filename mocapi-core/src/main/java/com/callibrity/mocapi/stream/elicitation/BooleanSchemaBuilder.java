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

import com.callibrity.mocapi.model.BooleanSchema;

/** Builder for {@link BooleanSchema} elicitation schema properties. */
public final class BooleanSchemaBuilder {

  private String description;
  private String title;
  private boolean required = true;
  private Boolean defaultValue;

  public BooleanSchemaBuilder() {}

  public BooleanSchemaBuilder description(String description) {
    this.description = description;
    return this;
  }

  public BooleanSchemaBuilder title(String title) {
    this.title = title;
    return this;
  }

  public BooleanSchemaBuilder optional() {
    this.required = false;
    return this;
  }

  public BooleanSchemaBuilder defaultValue(boolean value) {
    this.defaultValue = value;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  public BooleanSchema build() {
    return new BooleanSchema(title, description, defaultValue);
  }
}
