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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Sampling {@code toolChoice} value. Per the MCP spec this is polymorphic — either one of the bare
 * strings {@code "auto"} / {@code "none"}, or an object {@code {"type":"tool","name":"x"}}
 * selecting a specific tool. This sealed type captures the three variants; Jackson serialization
 * handles the on-wire shape.
 */
public sealed interface ToolChoice permits ToolChoice.Auto, ToolChoice.None, ToolChoice.Specific {

  /** Client lets the model decide whether (and which) tool to call. */
  static ToolChoice auto() {
    return Auto.INSTANCE;
  }

  /** Client forbids the model from calling any tool on this sampling turn. */
  static ToolChoice none() {
    return None.INSTANCE;
  }

  /** Client forces the model to call exactly one named tool. */
  static ToolChoice specific(String name) {
    return new Specific(name);
  }

  /**
   * Parses the raw JSON shape ({@code "auto"}, {@code "none"}, or {@code {...}}) back to an
   * instance. Used by Jackson.
   */
  @JsonCreator
  static ToolChoice fromJson(Object json) {
    if (json instanceof String s) {
      return switch (s) {
        case "auto" -> auto();
        case "none" -> none();
        default -> throw new IllegalArgumentException("Unknown toolChoice string: " + s);
      };
    }
    if (json instanceof java.util.Map<?, ?> m && "tool".equals(m.get("type"))) {
      Object nameValue = m.get("name");
      if (nameValue instanceof String name) {
        return specific(name);
      }
    }
    throw new IllegalArgumentException("Unrecognized toolChoice payload: " + json);
  }

  // MCP protocol sentinel value. The "auto" tool-choice is a well-known string constant in
  // the MCP 2025-11-25 spec; using a singleton avoids redundant allocations while preserving
  // pattern-match compatibility with the sealed-interface shape.
  @SuppressWarnings("java:S6548")
  final class Auto implements ToolChoice {
    static final Auto INSTANCE = new Auto();

    private Auto() {}

    @JsonValue
    public String toJson() {
      return "auto";
    }
  }

  // MCP protocol sentinel value. See Auto above for rationale.
  @SuppressWarnings("java:S6548")
  final class None implements ToolChoice {
    static final None INSTANCE = new None();

    private None() {}

    @JsonValue
    public String toJson() {
      return "none";
    }
  }

  record Specific(@JsonProperty("type") String type, @JsonProperty("name") String name)
      implements ToolChoice {
    public Specific(String name) {
      this("tool", name);
    }
  }
}
