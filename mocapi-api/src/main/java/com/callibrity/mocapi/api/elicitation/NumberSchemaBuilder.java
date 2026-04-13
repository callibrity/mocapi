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

import com.callibrity.mocapi.model.NumberSchema;

/** Builder for number-typed {@link NumberSchema} elicitation schema properties. */
public final class NumberSchemaBuilder {

  private String description;
  private String title;
  private boolean required = true;
  private Double defaultValue;
  private Number minimum;
  private Number maximum;

  public NumberSchemaBuilder description(String description) {
    this.description = description;
    return this;
  }

  public NumberSchemaBuilder title(String title) {
    this.title = title;
    return this;
  }

  public NumberSchemaBuilder optional() {
    this.required = false;
    return this;
  }

  public NumberSchemaBuilder defaultValue(double value) {
    this.defaultValue = value;
    return this;
  }

  public NumberSchemaBuilder minimum(Number min) {
    this.minimum = min;
    return this;
  }

  public NumberSchemaBuilder maximum(Number max) {
    this.maximum = max;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  public NumberSchema build() {
    return new NumberSchema("number", title, description, minimum, maximum, defaultValue);
  }
}
