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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class BooleanPropertyBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldBuildMinimalBooleanProperty() {
    ObjectNode node = new BooleanPropertyBuilder("Is active").build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("boolean");
    assertThat(node.get("description").asString()).isEqualTo("Is active");
    assertThat(node.has("default")).isFalse();
  }

  @Test
  void shouldIncludeTitle() {
    ObjectNode node =
        new BooleanPropertyBuilder("Is active").title("Active Status").build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Active Status");
  }

  @Test
  void shouldIncludeDefaultValueTrue() {
    ObjectNode node =
        new BooleanPropertyBuilder("Is active").defaultValue(true).build(objectMapper);

    assertThat(node.get("default").asBoolean()).isTrue();
  }

  @Test
  void shouldIncludeDefaultValueFalse() {
    ObjectNode node =
        new BooleanPropertyBuilder("Is active").defaultValue(false).build(objectMapper);

    assertThat(node.get("default").asBoolean()).isFalse();
  }

  @Test
  void shouldChainTitleAndDefault() {
    ObjectNode node =
        new BooleanPropertyBuilder("Is active")
            .title("Active Status")
            .defaultValue(true)
            .build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Active Status");
    assertThat(node.get("default").asBoolean()).isTrue();
  }
}
