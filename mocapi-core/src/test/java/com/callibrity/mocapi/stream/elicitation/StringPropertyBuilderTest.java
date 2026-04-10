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

class StringPropertyBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldBuildMinimalStringProperty() {
    ObjectNode node = new StringPropertyBuilder("A description").build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("string");
    assertThat(node.get("description").asString()).isEqualTo("A description");
    assertThat(node.has("title")).isFalse();
    assertThat(node.has("default")).isFalse();
  }

  @Test
  void shouldIncludeTitle() {
    ObjectNode node = new StringPropertyBuilder("desc").title("Full Name").build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("Full Name");
  }

  @Test
  void shouldIncludeDefaultValue() {
    ObjectNode node = new StringPropertyBuilder("desc").defaultValue("hello").build(objectMapper);

    assertThat(node.get("default").asString()).isEqualTo("hello");
  }

  @Test
  void shouldIncludeMinAndMaxLength() {
    ObjectNode node =
        new StringPropertyBuilder("desc").minLength(1).maxLength(255).build(objectMapper);

    assertThat(node.get("minLength").asInt()).isEqualTo(1);
    assertThat(node.get("maxLength").asInt()).isEqualTo(255);
  }

  @Test
  void shouldIncludePattern() {
    ObjectNode node = new StringPropertyBuilder("desc").pattern("^[a-z]+$").build(objectMapper);

    assertThat(node.get("pattern").asString()).isEqualTo("^[a-z]+$");
  }

  @Test
  void shouldSetEmailFormat() {
    ObjectNode node = new StringPropertyBuilder("desc").email().build(objectMapper);

    assertThat(node.get("format").asString()).isEqualTo("email");
  }

  @Test
  void shouldSetUriFormat() {
    ObjectNode node = new StringPropertyBuilder("desc").uri().build(objectMapper);

    assertThat(node.get("format").asString()).isEqualTo("uri");
  }

  @Test
  void shouldSetDateFormat() {
    ObjectNode node = new StringPropertyBuilder("desc").date().build(objectMapper);

    assertThat(node.get("format").asString()).isEqualTo("date");
  }

  @Test
  void shouldSetDateTimeFormat() {
    ObjectNode node = new StringPropertyBuilder("desc").dateTime().build(objectMapper);

    assertThat(node.get("format").asString()).isEqualTo("date-time");
  }

  @Test
  void shouldChainMultipleConstraints() {
    ObjectNode node =
        new StringPropertyBuilder("desc")
            .title("ZIP")
            .pattern("^\\d{5}$")
            .maxLength(5)
            .defaultValue("12345")
            .build(objectMapper);

    assertThat(node.get("title").asString()).isEqualTo("ZIP");
    assertThat(node.get("pattern").asString()).isEqualTo("^\\d{5}$");
    assertThat(node.get("maxLength").asInt()).isEqualTo(5);
    assertThat(node.get("default").asString()).isEqualTo("12345");
  }
}
