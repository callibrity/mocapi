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

import com.callibrity.mocapi.model.StringFormat;
import com.callibrity.mocapi.model.StringSchema;

/** Builder for {@link StringSchema} elicitation schema properties. */
public final class StringSchemaBuilder {

  private String description;
  private String title;
  private boolean required = true;
  private String defaultValue;
  private Integer minLength;
  private Integer maxLength;
  private StringFormat format;

  public StringSchemaBuilder() {}

  public StringSchemaBuilder description(String description) {
    this.description = description;
    return this;
  }

  public StringSchemaBuilder title(String title) {
    this.title = title;
    return this;
  }

  public StringSchemaBuilder optional() {
    this.required = false;
    return this;
  }

  public StringSchemaBuilder defaultValue(String value) {
    this.defaultValue = value;
    return this;
  }

  public StringSchemaBuilder minLength(int min) {
    this.minLength = min;
    return this;
  }

  public StringSchemaBuilder maxLength(int max) {
    this.maxLength = max;
    return this;
  }

  /** Shorthand for email format. */
  public StringSchemaBuilder email() {
    this.format = StringFormat.EMAIL;
    return this;
  }

  /** Shorthand for URI format. */
  public StringSchemaBuilder uri() {
    this.format = StringFormat.URI;
    return this;
  }

  /** Shorthand for date format. */
  public StringSchemaBuilder date() {
    this.format = StringFormat.DATE;
    return this;
  }

  /** Shorthand for date-time format. */
  public StringSchemaBuilder dateTime() {
    this.format = StringFormat.DATE_TIME;
    return this;
  }

  boolean isRequired() {
    return required;
  }

  public StringSchema build() {
    return new StringSchema(title, description, minLength, maxLength, format, defaultValue);
  }
}
