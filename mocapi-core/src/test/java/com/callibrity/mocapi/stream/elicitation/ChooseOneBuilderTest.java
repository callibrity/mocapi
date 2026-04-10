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

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ChooseOneBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  enum Tag {
    JAVA,
    PYTHON,
    GO
  }

  enum TitledColor {
    RED("Crimson Red"),
    GREEN("Forest Green");

    private final String title;

    TitledColor(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  @Test
  void fromEnumShouldProduceOneOfWithConstTitle() {
    ObjectNode node = ChooseOneBuilder.fromEnum(Tag.class).build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("string");
    assertThat(node.get("oneOf")).hasSize(3);
    assertThat(node.get("oneOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(node.get("oneOf").get(0).get("title").asString()).isEqualTo("JAVA");
  }

  @Test
  void fromEnumShouldUseToStringForTitle() {
    ObjectNode node = ChooseOneBuilder.fromEnum(TitledColor.class).build(objectMapper);

    assertThat(node.get("oneOf").get(0).get("const").asString()).isEqualTo("RED");
    assertThat(node.get("oneOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
  }

  @Test
  void fromEnumWithCustomTitleFnShouldUseIt() {
    ObjectNode node =
        ChooseOneBuilder.fromEnum(Tag.class)
            .titleFn(t -> t.name().toLowerCase())
            .build(objectMapper);

    assertThat(node.get("oneOf").get(0).get("title").asString()).isEqualTo("java");
  }

  @Test
  void fromEnumWithDefaultShouldIncludeDefault() {
    ObjectNode node =
        ChooseOneBuilder.fromEnum(Tag.class).defaultValue(Tag.PYTHON).build(objectMapper);

    assertThat(node.get("default").asString()).isEqualTo("PYTHON");
  }

  @Test
  void fromRawStringsShouldProducePlainEnum() {
    ObjectNode node = ChooseOneBuilder.from(List.of("red", "green", "blue")).build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("string");
    assertThat(node.has("oneOf")).isFalse();
    assertThat(node.get("enum")).hasSize(3);
    assertThat(node.get("enum").get(0).asString()).isEqualTo("red");
  }

  @Test
  void fromRawStringsWithDefaultShouldIncludeDefault() {
    ObjectNode node =
        ChooseOneBuilder.from(List.of("red", "green")).defaultValue("green").build(objectMapper);

    assertThat(node.get("default").asString()).isEqualTo("green");
  }

  @Test
  void fromArbitraryItemsShouldProduceOneOf() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    ObjectNode node = ChooseOneBuilder.from(statuses, Status::code).build(objectMapper);

    assertThat(node.get("oneOf")).hasSize(2);
    assertThat(node.get("oneOf").get(0).get("const").asString()).isEqualTo("a");
  }

  @Test
  void fromArbitraryItemsWithTitleFnShouldUseCustomTitle() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    ObjectNode node =
        ChooseOneBuilder.from(statuses, Status::code).titleFn(Status::label).build(objectMapper);

    assertThat(node.get("oneOf").get(0).get("title").asString()).isEqualTo("Active");
  }

  @Test
  void fromArbitraryItemsWithDefaultShouldIncludeDefault() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("a", "Active"), new Status("i", "Inactive"));

    ObjectNode node =
        ChooseOneBuilder.from(statuses, Status::code)
            .defaultValue(statuses.get(0))
            .build(objectMapper);

    assertThat(node.get("default").asString()).isEqualTo("a");
  }
}
