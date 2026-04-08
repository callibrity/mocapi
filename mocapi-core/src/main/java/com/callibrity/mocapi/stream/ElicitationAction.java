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
package com.callibrity.mocapi.stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The action taken by the client in response to an elicitation request. */
public enum ElicitationAction {
  ACCEPT("accept"),
  DECLINE("decline"),
  CANCEL("cancel");

  private final String value;

  ElicitationAction(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ElicitationAction fromValue(String value) {
    for (ElicitationAction action : values()) {
      if (action.value.equals(value)) {
        return action;
      }
    }
    throw new IllegalArgumentException("Unknown elicitation action: " + value);
  }
}
