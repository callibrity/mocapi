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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum LoggingLevel {
  DEBUG,
  INFO,
  NOTICE,
  WARNING,
  ERROR,
  CRITICAL,
  ALERT,
  EMERGENCY;

  @JsonValue
  public String toJson() {
    return name().toLowerCase(Locale.ROOT);
  }

  @JsonCreator
  public static LoggingLevel fromJson(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
