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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ElicitationSchemaTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void builderShouldProduceObjectType() {
    ElicitationSchema schema = ElicitationSchema.builder().build();
    ObjectNode node = schema.toObjectNode(objectMapper);

    assertThat(node.get("type").asString()).isEqualTo("object");
    assertThat(node.has("properties")).isTrue();
  }

  @Test
  void stringPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().stringProperty("name", "User's name").build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("description").asString()).isEqualTo("User's name");
    assertThat(prop.has("default")).isFalse();
  }

  @Test
  void stringPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().stringProperty("name", "User's name", "Alice").build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("default").asString()).isEqualTo("Alice");
  }

  @Test
  void integerPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().integerProperty("age", "User's age").build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("type").asString()).isEqualTo("integer");
    assertThat(prop.get("description").asString()).isEqualTo("User's age");
  }

  @Test
  void integerPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().integerProperty("age", "User's age", 25).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("default").asInt()).isEqualTo(25);
  }

  @Test
  void numberPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().numberProperty("score", "Score value").build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("type").asString()).isEqualTo("number");
    assertThat(prop.get("description").asString()).isEqualTo("Score value");
  }

  @Test
  void numberPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().numberProperty("score", "Score value", 3.14).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("default").asDouble()).isEqualTo(3.14);
  }

  @Test
  void booleanPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().booleanProperty("active", "Is active").build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("active");

    assertThat(prop.get("type").asString()).isEqualTo("boolean");
    assertThat(prop.get("description").asString()).isEqualTo("Is active");
  }

  @Test
  void booleanPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().booleanProperty("active", "Is active", true).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("active");

    assertThat(prop.get("default").asBoolean()).isTrue();
  }

  @Test
  void enumPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().enumProperty("color", List.of("red", "green", "blue")).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("color");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("enum")).hasSize(3);
    assertThat(prop.get("enum").get(0).asString()).isEqualTo("red");
    assertThat(prop.get("enum").get(1).asString()).isEqualTo("green");
    assertThat(prop.get("enum").get(2).asString()).isEqualTo("blue");
  }

  @Test
  void chooseWithItemsAndTitleFnShouldProduceOneOfSchema() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("status", statuses, Status::code, Status::label).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("status");

    assertThat(prop.has("type")).isFalse();
    assertThat(prop.get("oneOf")).hasSize(2);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("active");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Active");
    assertThat(prop.get("oneOf").get(1).get("const").asString()).isEqualTo("inactive");
    assertThat(prop.get("oneOf").get(1).get("title").asString()).isEqualTo("Gone");
  }

  @Test
  void multiSelectPropertyShouldProduceArrayOfEnumSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .multiSelectProperty("tags", List.of("java", "python", "go"))
            .build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("type").asString()).isEqualTo("string");
    assertThat(items.get("enum")).hasSize(3);
    assertThat(items.get("enum").get(0).asString()).isEqualTo("java");
  }

  @Test
  void chooseManyWithItemsAndTitleFnShouldProduceArrayOfAnyOfSchema() {
    record Role(String code, String label) {}
    List<Role> roles = List.of(new Role("admin", "Administrator"), new Role("user", "User"));
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.chooseMany("roles", roles, Role::code, Role::label);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("roles");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf")).hasSize(2);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("admin");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Administrator");
  }

  @Test
  void requiredFieldsShouldBeIncluded() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .stringProperty("name", "Name")
            .stringProperty("email", "Email")
            .required("name", "email")
            .build();
    ObjectNode node = schema.toObjectNode(objectMapper);

    assertThat(node.get("required")).hasSize(2);
    assertThat(node.get("required").get(0).asString()).isEqualTo("name");
    assertThat(node.get("required").get(1).asString()).isEqualTo("email");
  }

  @Test
  void noRequiredFieldsShouldOmitRequiredArray() {
    ElicitationSchema schema = ElicitationSchema.builder().stringProperty("name", "Name").build();
    ObjectNode node = schema.toObjectNode(objectMapper);

    assertThat(node.has("required")).isFalse();
  }

  @Test
  void duplicatePropertyNameShouldThrow() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder().stringProperty("name", "Name");

    assertThatThrownBy(() -> builder.stringProperty("name", "Name again"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate property name: name");
  }

  @Test
  void requiredCalledMultipleTimesShouldNotDuplicate() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .stringProperty("name", "Name")
            .required("name")
            .required("name")
            .build();
    ObjectNode node = schema.toObjectNode(objectMapper);

    assertThat(node.get("required")).hasSize(1);
  }

  // --- Sub-builder constraint tests ---

  @Test
  void stringSubBuilderShouldSupportTitle() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.string("name", "Name").title("Full Name");
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("title").asString()).isEqualTo("Full Name");
  }

  @Test
  void stringSubBuilderShouldSupportMinAndMaxLength() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.string("code", "Zip code").minLength(5).maxLength(5);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("code");

    assertThat(prop.get("minLength").asInt()).isEqualTo(5);
    assertThat(prop.get("maxLength").asInt()).isEqualTo(5);
  }

  @Test
  void stringSubBuilderShouldSupportPattern() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.string("code", "Zip code").pattern("^\\d{5}$");
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("code");

    assertThat(prop.get("pattern").asString()).isEqualTo("^\\d{5}$");
  }

  @Test
  void stringSubBuilderShouldSupportFormat() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.string("email", "Email address").format("email");
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("email");

    assertThat(prop.get("format").asString()).isEqualTo("email");
  }

  @Test
  void stringSubBuilderShouldChainMultipleConstraints() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.string("code", "Zip code").pattern("^\\d{5}$").maxLength(5).title("ZIP");
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("code");

    assertThat(prop.get("pattern").asString()).isEqualTo("^\\d{5}$");
    assertThat(prop.get("maxLength").asInt()).isEqualTo(5);
    assertThat(prop.get("title").asString()).isEqualTo("ZIP");
  }

  @Test
  void integerSubBuilderShouldSupportTitleAndMinMax() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.integer("age", "Age").title("Your Age").minimum(0).maximum(150);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("type").asString()).isEqualTo("integer");
    assertThat(prop.get("title").asString()).isEqualTo("Your Age");
    assertThat(prop.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(prop.get("maximum").asDouble()).isEqualTo(150.0);
  }

  @Test
  void numberSubBuilderShouldSupportMinMax() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.number("score", "Score").minimum(0.0).maximum(100.0);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("type").asString()).isEqualTo("number");
    assertThat(prop.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(prop.get("maximum").asDouble()).isEqualTo(100.0);
  }

  @Test
  void boolSubBuilderShouldSupportTitle() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.bool("active", "Is active").title("Active Status");
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("active");

    assertThat(prop.get("type").asString()).isEqualTo("boolean");
    assertThat(prop.get("title").asString()).isEqualTo("Active Status");
  }

  enum Tag {
    JAVA,
    PYTHON,
    GO
  }

  enum TitledColor {
    RED("Crimson Red"),
    GREEN("Forest Green"),
    BLUE("Ocean Blue");

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
  void chooseShouldAlwaysProduceOneOfWithConstTitle() {
    ElicitationSchema schema = ElicitationSchema.builder().choose("tag", Tag.class).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.has("type")).isFalse();
    assertThat(prop.has("enum")).isFalse();
    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("JAVA");
    assertThat(prop.get("oneOf").get(1).get("const").asString()).isEqualTo("PYTHON");
    assertThat(prop.get("oneOf").get(2).get("const").asString()).isEqualTo("GO");
  }

  @Test
  void chooseShouldUseToStringForTitle() {
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("color", TitledColor.class).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("color");

    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("RED");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
  }

  @Test
  void chooseWithDefaultShouldProduceOneOfWithDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("tag", Tag.class, Tag.JAVA).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.has("type")).isFalse();
    assertThat(prop.has("enum")).isFalse();
    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("default").asString()).isEqualTo("JAVA");
  }

  @Test
  void chooseManyShouldAlwaysProduceAnyOfWithConstTitle() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.chooseMany("tags", Tag.class);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.has("type")).isFalse();
    assertThat(items.has("enum")).isFalse();
    assertThat(items.get("anyOf")).hasSize(3);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("JAVA");
  }

  @Test
  void chooseManyShouldUseToStringForTitle() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.chooseMany("colors", TitledColor.class);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("colors");

    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf")).hasSize(3);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("RED");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
  }

  @Test
  void chooseWithItemsAndValueFnOnlyShouldUseToStringForTitle() {
    List<String> items = List.of("active", "inactive");
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("status", items, Function.identity()).build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("status");

    assertThat(prop.get("oneOf")).hasSize(2);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("active");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("active");
  }

  @Test
  void chooseManyWithItemsAndValueFnOnlyShouldUseToStringForTitle() {
    List<String> items = List.of("admin", "user");
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.chooseMany("roles", items, Function.identity());
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("roles");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode itemsNode = (ObjectNode) prop.get("items");
    assertThat(itemsNode.get("anyOf")).hasSize(2);
    assertThat(itemsNode.get("anyOf").get(0).get("const").asString()).isEqualTo("admin");
    assertThat(itemsNode.get("anyOf").get(0).get("title").asString()).isEqualTo("admin");
  }

  @Test
  void chooseManySubBuilderShouldSupportMaxItems() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.chooseMany("tags", Tag.class).maxItems(3);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    assertThat(prop.get("maxItems").asInt()).isEqualTo(3);
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf")).hasSize(3);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("JAVA");
  }

  @Test
  void chooseManySubBuilderShouldSupportMinItems() {
    ElicitationSchema.Builder builder = ElicitationSchema.builder();
    builder.chooseMany("tags", Tag.class).minItems(1).maxItems(3);
    ElicitationSchema schema = builder.build();
    ObjectNode node = schema.toObjectNode(objectMapper);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("minItems").asInt()).isEqualTo(1);
    assertThat(prop.get("maxItems").asInt()).isEqualTo(3);
  }

  @Test
  void multiplePropertyTypesShouldCoexist() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .stringProperty("name", "Name")
            .integerProperty("age", "Age")
            .numberProperty("score", "Score")
            .booleanProperty("active", "Active")
            .enumProperty("color", List.of("red", "blue"))
            .required("name", "age")
            .build();
    ObjectNode node = schema.toObjectNode(objectMapper);

    assertThat(node.get("properties")).hasSize(5);
    assertThat(node.get("required")).hasSize(2);
  }
}
