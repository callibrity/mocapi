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

class NumberPropertyBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldBuildMinimalNumberProperty() {
    ObjectNode node = new NumberPropertyBuilder("Score").build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("number");
    assertThat(node.get("description").asString()).isEqualTo("Score");
    assertThat(node.has("default")).isFalse();
  }

  @Test
  void shouldIncludeTitle() {
    ObjectNode node = new NumberPropertyBuilder("Score").title("Your Score").build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Your Score");
  }

  @Test
  void shouldIncludeDefaultValue() {
    ObjectNode node = new NumberPropertyBuilder("Score").defaultValue(95.5).build(objectMapper);

    assertThat(node.get("default").asDouble()).isEqualTo(95.5);
  }

  @Test
  void shouldIncludeMinimumAndMaximum() {
    ObjectNode node =
        new NumberPropertyBuilder("Score").minimum(0.0).maximum(100.0).build(objectMapper);

    assertThat(node.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(node.get("maximum").asDouble()).isEqualTo(100.0);
  }

  @Test
  void shouldChainAllConstraints() {
    ObjectNode node =
        new NumberPropertyBuilder("Score")
            .title("Your Score")
            .defaultValue(75.0)
            .minimum(0.0)
            .maximum(100.0)
            .build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Your Score");
    assertThat(node.get("default").asDouble()).isEqualTo(75.0);
    assertThat(node.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(node.get("maximum").asDouble()).isEqualTo(100.0);
  }
}
