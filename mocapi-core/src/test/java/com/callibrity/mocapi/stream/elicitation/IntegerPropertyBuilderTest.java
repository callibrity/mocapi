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

class IntegerPropertyBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldBuildMinimalIntegerProperty() {
    ObjectNode node = new IntegerPropertyBuilder("Age").build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("integer");
    assertThat(node.get("description").asString()).isEqualTo("Age");
    assertThat(node.has("default")).isFalse();
  }

  @Test
  void shouldIncludeTitle() {
    ObjectNode node = new IntegerPropertyBuilder("Age").title("Your Age").build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Your Age");
  }

  @Test
  void shouldIncludeDefaultValue() {
    ObjectNode node = new IntegerPropertyBuilder("Age").defaultValue(30).build(objectMapper);

    assertThat(node.get("default").asInt()).isEqualTo(30);
  }

  @Test
  void shouldIncludeMinimumAndMaximum() {
    ObjectNode node = new IntegerPropertyBuilder("Age").minimum(0).maximum(150).build(objectMapper);

    assertThat(node.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(node.get("maximum").asDouble()).isEqualTo(150.0);
  }

  @Test
  void shouldChainAllConstraints() {
    ObjectNode node =
        new IntegerPropertyBuilder("Age")
            .title("Your Age")
            .defaultValue(25)
            .minimum(0)
            .maximum(150)
            .build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Your Age");
    assertThat(node.get("default").asInt()).isEqualTo(25);
    assertThat(node.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(node.get("maximum").asDouble()).isEqualTo(150.0);
  }
}
