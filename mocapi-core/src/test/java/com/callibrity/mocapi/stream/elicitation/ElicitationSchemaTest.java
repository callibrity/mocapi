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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.RequestedSchema;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ElicitationSchemaTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ObjectNode toNode(ElicitationSchema schema) {
    return (ObjectNode) objectMapper.valueToTree(schema.toRequestedSchema());
  }

  @Test
  void builderShouldProduceObjectType() {
    ElicitationSchema schema = ElicitationSchema.builder().build();
    ObjectNode node = toNode(schema);

    assertThat(node.get("type").asString()).isEqualTo("object");
    assertThat(node.has("properties")).isTrue();
  }

  @Test
  void stringPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema = ElicitationSchema.builder().string("name", "User's name").build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("description").asString()).isEqualTo("User's name");
    assertThat(prop.has("default")).isFalse();
  }

  @Test
  void stringPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("name", "User's name", "Alice").build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("default").asString()).isEqualTo("Alice");
  }

  @Test
  void integerPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema = ElicitationSchema.builder().integer("age", "User's age").build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("type").asString()).isEqualTo("integer");
    assertThat(prop.get("description").asString()).isEqualTo("User's age");
  }

  @Test
  void integerPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema = ElicitationSchema.builder().integer("age", "User's age", 25).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("default").asInt()).isEqualTo(25);
  }

  @Test
  void numberPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema = ElicitationSchema.builder().number("score", "Score value").build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("type").asString()).isEqualTo("number");
    assertThat(prop.get("description").asString()).isEqualTo("Score value");
  }

  @Test
  void numberPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().number("score", "Score value", 3.14).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("default").asDouble()).isEqualTo(3.14);
  }

  @Test
  void booleanPropertyShouldProduceCorrectSchema() {
    ElicitationSchema schema = ElicitationSchema.builder().bool("active", "Is active").build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("active");

    assertThat(prop.get("type").asString()).isEqualTo("boolean");
    assertThat(prop.get("description").asString()).isEqualTo("Is active");
  }

  @Test
  void booleanPropertyWithDefaultShouldIncludeDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().bool("active", "Is active", true).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("active");

    assertThat(prop.get("default").asBoolean()).isTrue();
  }

  @Test
  void chooseWithRawStringsShouldProducePlainEnumSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("color", List.of("red", "green", "blue")).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("color");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.has("oneOf")).isFalse();
    assertThat(prop.get("enum")).hasSize(3);
    assertThat(prop.get("enum").get(0).asString()).isEqualTo("red");
    assertThat(prop.get("enum").get(1).asString()).isEqualTo("green");
    assertThat(prop.get("enum").get(2).asString()).isEqualTo("blue");
  }

  @Test
  void chooseWithItemsAndTitleCustomizerShouldProduceOneOfSchema() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
    Consumer<ChooseOneBuilder<Status>> customizer = c -> c.titleFn(Status::label);
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("status", statuses, Status::code, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("status");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("oneOf")).hasSize(2);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("active");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Active");
    assertThat(prop.get("oneOf").get(1).get("const").asString()).isEqualTo("inactive");
    assertThat(prop.get("oneOf").get(1).get("title").asString()).isEqualTo("Gone");
  }

  @Test
  void chooseManyWithRawStringsShouldProduceArrayOfPlainEnumSchema() {
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("tags", List.of("java", "python", "go")).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("type").asString()).isEqualTo("string");
    assertThat(items.has("anyOf")).isFalse();
    assertThat(items.get("enum")).hasSize(3);
    assertThat(items.get("enum").get(0).asString()).isEqualTo("java");
    assertThat(items.get("enum").get(1).asString()).isEqualTo("python");
    assertThat(items.get("enum").get(2).asString()).isEqualTo("go");
  }

  @Test
  void chooseManyWithItemsAndTitleCustomizerShouldProduceArrayOfAnyOfSchema() {
    record Role(String code, String label) {}
    List<Role> roles = List.of(new Role("admin", "Administrator"), new Role("user", "User"));
    Consumer<ChooseManyBuilder<Role>> customizer = c -> c.titleFn(Role::label);
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("roles", roles, Role::code, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("roles");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf")).hasSize(2);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("admin");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Administrator");
  }

  @Test
  void requiredFieldsShouldBeIncludedByDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("name", "Name").string("email", "Email").build();
    ObjectNode node = toNode(schema);

    assertThat(node.get("required")).hasSize(2);
    assertThat(node.get("required").get(0).asString()).isEqualTo("name");
    assertThat(node.get("required").get(1).asString()).isEqualTo("email");
  }

  @Test
  void optionalFieldsShouldBeExcludedFromRequired() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .string("name", "Name")
            .string("nickname", "Nickname", s -> s.optional())
            .build();
    ObjectNode node = toNode(schema);

    assertThat(node.get("required")).hasSize(1);
    assertThat(node.get("required").get(0).asString()).isEqualTo("name");
  }

  @Test
  void allOptionalFieldsShouldOmitRequiredArray() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("name", "Name", s -> s.optional()).build();
    ObjectNode node = toNode(schema);

    assertThat(node.has("required")).isFalse();
  }

  @Test
  void duplicatePropertyNameShouldThrow() {
    ElicitationSchemaBuilder builder = ElicitationSchema.builder().string("name", "Name");

    assertThatThrownBy(() -> builder.string("name", "Name again"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate property: name");
  }

  // --- Customizer constraint tests ---

  @Test
  void stringCustomizerShouldSupportTitle() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("name", "Name", s -> s.title("Full Name")).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("title").asString()).isEqualTo("Full Name");
  }

  @Test
  void stringCustomizerShouldSupportMinAndMaxLength() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .string("code", "Zip code", s -> s.minLength(5).maxLength(5))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("code");

    assertThat(prop.get("minLength").asInt()).isEqualTo(5);
    assertThat(prop.get("maxLength").asInt()).isEqualTo(5);
  }

  @Test
  void stringCustomizerShouldSupportEmailShorthand() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("email", "Email address", s -> s.email()).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("email");

    assertThat(prop.get("format").asString()).isEqualTo("email");
  }

  @Test
  void stringCustomizerShouldSupportUriShorthand() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("url", "Website URL", s -> s.uri()).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("url");

    assertThat(prop.get("format").asString()).isEqualTo("uri");
  }

  @Test
  void stringCustomizerShouldSupportDateShorthand() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("dob", "Date of birth", s -> s.date()).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("dob");

    assertThat(prop.get("format").asString()).isEqualTo("date");
  }

  @Test
  void stringCustomizerShouldSupportDateTimeShorthand() {
    ElicitationSchema schema =
        ElicitationSchema.builder().string("ts", "Timestamp", s -> s.dateTime()).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("ts");

    assertThat(prop.get("format").asString()).isEqualTo("date-time");
  }

  @Test
  void stringCustomizerShouldChainMultipleConstraints() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .string("code", "Zip code", s -> s.maxLength(5).title("ZIP"))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("code");

    assertThat(prop.get("maxLength").asInt()).isEqualTo(5);
    assertThat(prop.get("title").asString()).isEqualTo("ZIP");
  }

  @Test
  void stringCustomizerShouldSupportDefaultValue() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .string("name", "Name", s -> s.defaultValue("John Doe").maxLength(100))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.get("default").asString()).isEqualTo("John Doe");
    assertThat(prop.get("maxLength").asInt()).isEqualTo(100);
  }

  @Test
  void integerCustomizerShouldSupportTitleAndMinMax() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .integer("age", "Age", n -> n.title("Your Age").minimum(0).maximum(150))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("type").asString()).isEqualTo("integer");
    assertThat(prop.get("title").asString()).isEqualTo("Your Age");
    assertThat(prop.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(prop.get("maximum").asDouble()).isEqualTo(150.0);
  }

  @Test
  void integerCustomizerShouldSupportDefaultValue() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .integer("age", "Age", n -> n.defaultValue(30).minimum(0).maximum(150))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("age");

    assertThat(prop.get("default").asInt()).isEqualTo(30);
  }

  @Test
  void numberCustomizerShouldSupportMinMax() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .number("score", "Score", n -> n.minimum(0.0).maximum(100.0))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("type").asString()).isEqualTo("number");
    assertThat(prop.get("minimum").asDouble()).isEqualTo(0.0);
    assertThat(prop.get("maximum").asDouble()).isEqualTo(100.0);
  }

  @Test
  void numberCustomizerShouldSupportDefaultValue() {
    ElicitationSchema schema =
        ElicitationSchema.builder().number("score", "Score", n -> n.defaultValue(95.5)).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("score");

    assertThat(prop.get("default").asDouble()).isEqualTo(95.5);
  }

  @Test
  void boolCustomizerShouldSupportTitle() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .bool("active", "Is active", b -> b.title("Active Status"))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("active");

    assertThat(prop.get("type").asString()).isEqualTo("boolean");
    assertThat(prop.get("title").asString()).isEqualTo("Active Status");
  }

  @Test
  void boolCustomizerShouldSupportDefaultValue() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .bool("verified", "Verified?", b -> b.defaultValue(true))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("verified");

    assertThat(prop.get("default").asBoolean()).isTrue();
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
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.get("type").asString()).isEqualTo("string");
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
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("color");

    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("RED");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
  }

  @Test
  void chooseWithDefaultShouldProduceOneOfWithDefault() {
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("tag", Tag.class, Tag.JAVA).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.has("enum")).isFalse();
    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("default").asString()).isEqualTo("JAVA");
  }

  @Test
  void chooseManyShouldAlwaysProduceAnyOfWithConstTitle() {
    ElicitationSchema schema = ElicitationSchema.builder().chooseMany("tags", Tag.class).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.has("enum")).isFalse();
    assertThat(items.get("anyOf")).hasSize(3);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("JAVA");
  }

  @Test
  void chooseManyShouldUseToStringForTitle() {
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("colors", TitledColor.class).build();
    ObjectNode node = toNode(schema);
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
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("status");

    assertThat(prop.get("oneOf")).hasSize(2);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("active");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("active");
  }

  @Test
  void chooseManyWithItemsAndValueFnOnlyShouldUseToStringForTitle() {
    List<String> items = List.of("admin", "user");
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("roles", items, Function.identity()).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("roles");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    ObjectNode itemsNode = (ObjectNode) prop.get("items");
    assertThat(itemsNode.get("anyOf")).hasSize(2);
    assertThat(itemsNode.get("anyOf").get(0).get("const").asString()).isEqualTo("admin");
    assertThat(itemsNode.get("anyOf").get(0).get("title").asString()).isEqualTo("admin");
  }

  @Test
  void chooseManyCustomizerShouldSupportMaxItems() {
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("tags", Tag.class, c -> c.maxItems(3)).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    assertThat(prop.get("maxItems").asInt()).isEqualTo(3);
    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf")).hasSize(3);
    assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("JAVA");
  }

  @Test
  void chooseManyCustomizerShouldSupportMinItems() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .chooseMany("tags", Tag.class, c -> c.minItems(1).maxItems(3))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("minItems").asInt()).isEqualTo(1);
    assertThat(prop.get("maxItems").asInt()).isEqualTo(3);
  }

  @Test
  void chooseEnumWithTitleCustomizerShouldUseCustomTitles() {
    Consumer<ChooseOneBuilder<Tag>> customizer = c -> c.titleFn(t -> t.name().toLowerCase());
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("tag", Tag.class, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("JAVA");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("java");
  }

  @Test
  void chooseEnumWithTitleAndDefaultCustomizerShouldWork() {
    Consumer<ChooseOneBuilder<Tag>> customizer =
        c -> c.titleFn(t -> t.name().toLowerCase()).defaultValue(Tag.PYTHON);
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("tag", Tag.class, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("oneOf")).hasSize(3);
    assertThat(prop.get("default").asString()).isEqualTo("PYTHON");
    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("java");
  }

  @Test
  void chooseManyEnumWithDefaultsCustomizerShouldIncludeDefaults() {
    Consumer<ChooseManyBuilder<Tag>> customizer = c -> c.defaults(List.of(Tag.JAVA, Tag.GO));
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("tags", Tag.class, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    assertThat(prop.get("default")).hasSize(2);
    assertThat(prop.get("default").get(0).asString()).isEqualTo("JAVA");
    assertThat(prop.get("default").get(1).asString()).isEqualTo("GO");
  }

  @Test
  void chooseManyEnumWithTitleAndDefaultsCustomizerShouldWork() {
    Consumer<ChooseManyBuilder<Tag>> customizer =
        c -> c.titleFn(t -> t.name().toLowerCase()).defaults(List.of(Tag.PYTHON));
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("tags", Tag.class, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("java");
    assertThat(prop.get("default")).hasSize(1);
    assertThat(prop.get("default").get(0).asString()).isEqualTo("PYTHON");
  }

  @Test
  void chooseManyEnumWithTitleCustomizerShouldUseCustomTitles() {
    Consumer<ChooseManyBuilder<Tag>> customizer = c -> c.titleFn(t -> t.name().toLowerCase());
    ElicitationSchema schema =
        ElicitationSchema.builder().chooseMany("tags", Tag.class, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("java");
  }

  @Test
  void chooseWithArbitraryItemsAndDefaultShouldWork() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
    Status defaultStatus = statuses.get(0);
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("status", statuses, Status::code, defaultStatus).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("status");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("oneOf")).hasSize(2);
    assertThat(prop.get("default").asString()).isEqualTo("active");
  }

  @Test
  void chooseWithRawStringsAndDefaultShouldWork() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .choose("color", List.of("red", "green", "blue"), "green")
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("color");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.has("oneOf")).isFalse();
    assertThat(prop.get("enum")).hasSize(3);
    assertThat(prop.get("default").asString()).isEqualTo("green");
  }

  @Test
  void chooseLegacyShouldProduceEnumWithEnumNames() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .chooseLegacy(
                "level",
                List.of("opt1", "opt2", "opt3"),
                List.of("Option One", "Option Two", "Option Three"))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("level");

    assertThat(prop.get("type").asString()).isEqualTo("string");
    assertThat(prop.get("enum")).hasSize(3);
    assertThat(prop.get("enum").get(0).asString()).isEqualTo("opt1");
    assertThat(prop.get("enum").get(1).asString()).isEqualTo("opt2");
    assertThat(prop.get("enum").get(2).asString()).isEqualTo("opt3");
    assertThat(prop.get("enumNames")).hasSize(3);
    assertThat(prop.get("enumNames").get(0).asString()).isEqualTo("Option One");
    assertThat(prop.get("enumNames").get(1).asString()).isEqualTo("Option Two");
    assertThat(prop.get("enumNames").get(2).asString()).isEqualTo("Option Three");
  }

  @Test
  void chooseManyWithRawStringsAndDefaultsCustomizerShouldWork() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .chooseMany(
                "tags",
                List.of("java", "python", "go"),
                Function.identity(),
                c -> c.defaults(List.of("java", "go")))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("type").asString()).isEqualTo("array");
    assertThat(prop.get("default")).hasSize(2);
    assertThat(prop.get("default").get(0).asString()).isEqualTo("java");
    assertThat(prop.get("default").get(1).asString()).isEqualTo("go");
  }

  @Test
  void chooseManyWithRawStringsAndListDefaultsCustomizerShouldWork() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .chooseMany(
                "tags",
                List.of("java", "python", "go"),
                Function.identity(),
                c -> c.defaults(List.of("python")))
            .build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    assertThat(prop.get("default")).hasSize(1);
    assertThat(prop.get("default").get(0).asString()).isEqualTo("python");
  }

  @Test
  void chooseSingleSelectShouldIncludeTypeString() {
    ElicitationSchema schema = ElicitationSchema.builder().choose("tag", Tag.class).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

    assertThat(prop.get("type").asString()).isEqualTo("string");
  }

  @Test
  void chooseManyItemsShouldIncludeTypeString() {
    ElicitationSchema schema = ElicitationSchema.builder().chooseMany("tags", Tag.class).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

    ObjectNode items = (ObjectNode) prop.get("items");
    assertThat(items.get("type").asString()).isEqualTo("string");
  }

  @Test
  void multiplePropertyTypesShouldCoexist() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .string("name", "Name")
            .integer("age", "Age")
            .number("score", "Score")
            .bool("active", "Active")
            .choose("color", List.of("red", "blue"))
            .build();
    ObjectNode node = toNode(schema);

    assertThat(node.get("properties")).hasSize(5);
    assertThat(node.get("required")).hasSize(5);
  }

  @Test
  void chooseWithArbitraryItemsTitleAndDefaultCustomizerShouldWork() {
    record Status(String code, String label) {}
    List<Status> statuses = List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
    Consumer<ChooseOneBuilder<Status>> customizer =
        c -> c.titleFn(Status::label).defaultValue(statuses.get(0));
    ElicitationSchema schema =
        ElicitationSchema.builder().choose("status", statuses, Status::code, customizer).build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("status");

    assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Active");
    assertThat(prop.get("default").asString()).isEqualTo("active");
  }

  @Test
  void requiredFieldShouldNotAppearInSerializedPropertyJson() {
    ElicitationSchema schema = ElicitationSchema.builder().string("name", "Name").build();
    ObjectNode node = toNode(schema);
    ObjectNode prop = (ObjectNode) node.get("properties").get("name");

    assertThat(prop.has("required")).isFalse();
  }

  @Test
  void toRequestedSchemaShouldReturnTypedRecord() {
    ElicitationSchema schema =
        ElicitationSchema.builder()
            .string("name", "Name")
            .integer("age", "Age", a -> a.optional())
            .build();
    RequestedSchema requestedSchema = schema.toRequestedSchema();

    assertThat(requestedSchema.properties()).hasSize(2);
    assertThat(requestedSchema.required()).containsExactly("name");
    assertThat(requestedSchema.type()).isEqualTo("object");
  }
}
