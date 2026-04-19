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

/**
 * Hint to the client about which server contexts to include when sampling. Per MCP spec: {@code
 * "none"}, {@code "thisServer"}, or {@code "allServers"}.
 */
public enum IncludeContext {
  NONE("none"),
  THIS_SERVER("thisServer"),
  ALL_SERVERS("allServers");

  private final String wire;

  IncludeContext(String wire) {
    this.wire = wire;
  }

  @JsonValue
  public String toJson() {
    return wire;
  }

  @JsonCreator
  public static IncludeContext fromJson(String value) {
    for (IncludeContext v : values()) {
      if (v.wire.equals(value)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unknown includeContext: " + value);
  }
}
