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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolChoiceTest {

  @Test
  void from_json_auto_string_returns_auto_singleton() {
    assertThat(ToolChoice.fromJson("auto")).isSameAs(ToolChoice.Auto.INSTANCE);
  }

  @Test
  void from_json_none_string_returns_none_singleton() {
    assertThat(ToolChoice.fromJson("none")).isSameAs(ToolChoice.None.INSTANCE);
  }

  @Test
  void from_json_unknown_string_throws() {
    assertThatThrownBy(() -> ToolChoice.fromJson("sometimes"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown toolChoice string")
        .hasMessageContaining("sometimes");
  }

  @Test
  void from_json_map_with_type_tool_and_name_returns_specific() {
    Map<String, Object> payload = Map.of("type", "tool", "name", "search");

    ToolChoice choice = ToolChoice.fromJson(payload);

    assertThat(choice)
        .isInstanceOfSatisfying(
            ToolChoice.Specific.class,
            specific -> {
              assertThat(specific.type()).isEqualTo("tool");
              assertThat(specific.name()).isEqualTo("search");
            });
  }

  @Test
  void from_json_map_missing_name_throws() {
    Map<String, Object> payload = Map.of("type", "tool");
    assertThatThrownBy(() -> ToolChoice.fromJson(payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unrecognized toolChoice payload");
  }

  @Test
  void from_json_unrecognized_payload_throws() {
    Integer payload = 42;
    assertThatThrownBy(() -> ToolChoice.fromJson(payload))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unrecognized toolChoice payload");
  }

  @Test
  void auto_to_json_is_auto_literal() {
    assertThat(ToolChoice.Auto.INSTANCE.toJson()).isEqualTo("auto");
  }

  @Test
  void none_to_json_is_none_literal() {
    assertThat(ToolChoice.None.INSTANCE.toJson()).isEqualTo("none");
  }

  @Test
  void auto_factory_returns_singleton() {
    assertThat(ToolChoice.auto()).isSameAs(ToolChoice.Auto.INSTANCE);
  }

  @Test
  void none_factory_returns_singleton() {
    assertThat(ToolChoice.none()).isSameAs(ToolChoice.None.INSTANCE);
  }

  @Test
  void specific_factory_defaults_type_to_tool() {
    ToolChoice.Specific choice = (ToolChoice.Specific) ToolChoice.specific("search");
    assertThat(choice.type()).isEqualTo("tool");
    assertThat(choice.name()).isEqualTo("search");
  }
}
