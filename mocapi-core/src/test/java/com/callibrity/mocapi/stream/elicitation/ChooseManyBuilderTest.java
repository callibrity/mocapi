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

class ChooseManyBuilderTest {

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
  void fromEnumShouldProduceArrayOfAnyOf() {
    ObjectNode node = ChooseManyBuilder.fromEnum(Tag.class).build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("type").asString()).isEqualTo("string");
    assertThat(items.get("anyOf")).hasSize(3);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("JAVA");
  }

  @Test
  void fromEnumShouldUseToStringForTitle() {
    ObjectNode node = ChooseManyBuilder.fromEnum(TitledColor.class).build(objectMapper);

    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
  }

  @Test
  void fromEnumWithCustomTitleFnShouldUseIt() {
    ObjectNode node =
        ChooseManyBuilder.fromEnum(Tag.class)
            .titleFn(t -> t.name().toLowerCase())
            .build(objectMapper);

    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("java");
  }

  @Test
  void fromEnumWithDefaultsShouldIncludeDefaults() {
    ObjectNode node =
        ChooseManyBuilder.fromEnum(Tag.class)
            .defaults(List.of(Tag.JAVA, Tag.GO))
            .build(objectMapper);

    assertThat(node.get("default")).hasSize(2);
    assertThat(node.get("default").get(0).asString()).isEqualTo("JAVA");
    assertThat(node.get("default").get(1).asString()).isEqualTo("GO");
  }

  @Test
  void fromEnumWithMaxItemsShouldIncludeMaxItems() {
    ObjectNode node = ChooseManyBuilder.fromEnum(Tag.class).maxItems(2).build(objectMapper);

    assertThat(node.get("maxItems").asInt()).isEqualTo(2);
  }

  @Test
  void fromEnumWithMinItemsShouldIncludeMinItems() {
    ObjectNode node = ChooseManyBuilder.fromEnum(Tag.class).minItems(1).build(objectMapper);

    assertThat(node.get("minItems").asInt()).isEqualTo(1);
  }

  @Test
  void fromRawStringsShouldProduceArrayOfPlainEnum() {
    ObjectNode node = ChooseManyBuilder.from(List.of("java", "python", "go")).build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("type").asString()).isEqualTo("string");
    assertThat(items.has("anyOf")).isFalse();
    assertThat(items.get("enum")).hasSize(3);
    assertThat(items.get("enum").get(0).asString()).isEqualTo("java");
  }

  @Test
  void fromArbitraryItemsShouldProduceArrayOfAnyOf() {
    record Role(String code, String label) {}
    List<Role> roles = List.of(new Role("admin", "Admin"), new Role("user", "User"));

    ObjectNode node = ChooseManyBuilder.from(roles, Role::code).build(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("anyOf")).hasSize(2);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("admin");
  }

  @Test
  void fromArbitraryItemsWithTitleFnShouldUseIt() {
    record Role(String code, String label) {}
    List<Role> roles = List.of(new Role("admin", "Administrator"));

    ObjectNode node =
        ChooseManyBuilder.from(roles, Role::code).titleFn(Role::label).build(objectMapper);

    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Administrator");
  }

  @Test
  void shouldChainMultipleConstraints() {
    ObjectNode node =
        ChooseManyBuilder.fromEnum(Tag.class)
            .titleFn(t -> t.name().toLowerCase())
            .defaults(List.of(Tag.PYTHON))
            .minItems(1)
            .maxItems(3)
            .build(objectMapper);

    ObjectNode items = (ObjectNode) node.get("items");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("java");
    assertThat(node.get("default")).hasSize(1);
    assertThat(node.get("default").get(0).asString()).isEqualTo("PYTHON");
    assertThat(node.get("minItems").asInt()).isEqualTo(1);
    assertThat(node.get("maxItems").asInt()).isEqualTo(3);
  }
}
