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
package com.callibrity.mocapi.api.elicitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.RequestedSchema;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RequestedSchemaBuilderTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ObjectNode toNode(RequestedSchemaBuilder builder) {
    return (ObjectNode) objectMapper.valueToTree(builder.build());
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

  @Nested
  class Object_schema {
    @Test
    void builder_should_produce_object_type() {
      ObjectNode node = toNode(new RequestedSchemaBuilder());

      assertThat(node.get("type").asString()).isEqualTo("object");
      assertThat(node.has("properties")).isTrue();
    }

    @Test
    void multiple_property_types_should_coexist() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .string("name", "Name")
                  .integer("age", "Age")
                  .number("score", "Score")
                  .bool("active", "Active")
                  .choose("color", List.of("red", "blue")));

      assertThat(node.get("properties")).hasSize(5);
      assertThat(node.get("required")).hasSize(5);
    }

    @Test
    void duplicate_property_name_should_throw() {
      RequestedSchemaBuilder builder = new RequestedSchemaBuilder().string("name", "Name");

      assertThatThrownBy(() -> builder.string("name", "Name again"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Duplicate property: name");
    }

    @Test
    void build_should_return_typed_record() {
      RequestedSchema requestedSchema =
          new RequestedSchemaBuilder()
              .string("name", "Name")
              .integer("age", "Age", a -> a.optional())
              .build();

      assertThat(requestedSchema.properties()).hasSize(2);
      assertThat(requestedSchema.required()).containsExactly("name");
      assertThat(requestedSchema.type()).isEqualTo("object");
    }

    @Test
    void required_field_should_not_appear_in_serialized_property_json() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().string("name", "Name"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("name");

      assertThat(prop.has("required")).isFalse();
    }
  }

  @Nested
  class Required_and_optional {
    @Test
    void required_fields_should_be_included_by_default() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("name", "Name").string("email", "Email"));

      assertThat(node.get("required")).hasSize(2);
      assertThat(node.get("required").get(0).asString()).isEqualTo("name");
      assertThat(node.get("required").get(1).asString()).isEqualTo("email");
    }

    @Test
    void optional_fields_should_be_excluded_from_required() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .string("name", "Name")
                  .string("nickname", "Nickname", s -> s.optional()));

      assertThat(node.get("required")).hasSize(1);
      assertThat(node.get("required").get(0).asString()).isEqualTo("name");
    }

    @Test
    void all_optional_fields_should_omit_required_array() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("name", "Name", s -> s.optional()));

      assertThat(node.has("required")).isFalse();
    }
  }

  @Nested
  class String_properties {
    @Test
    void string_property_should_produce_correct_schema() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().string("name", "User's name"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("name");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("description").asString()).isEqualTo("User's name");
      assertThat(prop.has("default")).isFalse();
    }

    @Test
    void string_property_with_default_should_include_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().string("name", "User's name", "Alice"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("name");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("default").asString()).isEqualTo("Alice");
    }

    @Test
    void string_customizer_should_support_title() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("name", "Name", s -> s.title("Full Name")));
      ObjectNode prop = (ObjectNode) node.get("properties").get("name");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("title").asString()).isEqualTo("Full Name");
    }

    @Test
    void string_customizer_should_support_min_and_max_length() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .string("code", "Zip code", s -> s.minLength(5).maxLength(5)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("code");

      assertThat(prop.get("minLength").asInt()).isEqualTo(5);
      assertThat(prop.get("maxLength").asInt()).isEqualTo(5);
    }

    @Test
    void string_customizer_should_support_email_shorthand() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("email", "Email address", s -> s.email()));
      ObjectNode prop = (ObjectNode) node.get("properties").get("email");

      assertThat(prop.get("format").asString()).isEqualTo("email");
    }

    @Test
    void string_customizer_should_support_uri_shorthand() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("url", "Website URL", s -> s.uri()));
      ObjectNode prop = (ObjectNode) node.get("properties").get("url");

      assertThat(prop.get("format").asString()).isEqualTo("uri");
    }

    @Test
    void string_customizer_should_support_date_shorthand() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("dob", "Date of birth", s -> s.date()));
      ObjectNode prop = (ObjectNode) node.get("properties").get("dob");

      assertThat(prop.get("format").asString()).isEqualTo("date");
    }

    @Test
    void string_customizer_should_support_date_time_shorthand() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().string("ts", "Timestamp", s -> s.dateTime()));
      ObjectNode prop = (ObjectNode) node.get("properties").get("ts");

      assertThat(prop.get("format").asString()).isEqualTo("date-time");
    }

    @Test
    void string_customizer_should_chain_multiple_constraints() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .string("code", "Zip code", s -> s.maxLength(5).title("ZIP")));
      ObjectNode prop = (ObjectNode) node.get("properties").get("code");

      assertThat(prop.get("maxLength").asInt()).isEqualTo(5);
      assertThat(prop.get("title").asString()).isEqualTo("ZIP");
    }

    @Test
    void string_customizer_should_support_default_value() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .string("name", "Name", s -> s.defaultValue("John Doe").maxLength(100)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("name");

      assertThat(prop.get("default").asString()).isEqualTo("John Doe");
      assertThat(prop.get("maxLength").asInt()).isEqualTo(100);
    }
  }

  @Nested
  class Integer_properties {
    @Test
    void integer_property_should_produce_correct_schema() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().integer("age", "User's age"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("age");

      assertThat(prop.get("type").asString()).isEqualTo("integer");
      assertThat(prop.get("description").asString()).isEqualTo("User's age");
    }

    @Test
    void integer_property_with_default_should_include_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().integer("age", "User's age", 25));
      ObjectNode prop = (ObjectNode) node.get("properties").get("age");

      assertThat(prop.get("default").asInt()).isEqualTo(25);
    }

    @Test
    void integer_customizer_should_support_title_and_min_max() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .integer("age", "Age", n -> n.title("Your Age").minimum(0).maximum(150)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("age");

      assertThat(prop.get("type").asString()).isEqualTo("integer");
      assertThat(prop.get("title").asString()).isEqualTo("Your Age");
      assertThat(prop.get("minimum").asDouble()).isEqualTo(0.0);
      assertThat(prop.get("maximum").asDouble()).isEqualTo(150.0);
    }

    @Test
    void integer_customizer_should_support_default_value() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .integer("age", "Age", n -> n.defaultValue(30).minimum(0).maximum(150)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("age");

      assertThat(prop.get("default").asInt()).isEqualTo(30);
    }
  }

  @Nested
  class Number_properties {
    @Test
    void number_property_should_produce_correct_schema() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().number("score", "Score value"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("score");

      assertThat(prop.get("type").asString()).isEqualTo("number");
      assertThat(prop.get("description").asString()).isEqualTo("Score value");
    }

    @Test
    void number_property_with_default_should_include_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().number("score", "Score value", 3.14));
      ObjectNode prop = (ObjectNode) node.get("properties").get("score");

      assertThat(prop.get("default").asDouble()).isEqualTo(3.14);
    }

    @Test
    void number_customizer_should_support_min_max() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .number("score", "Score", n -> n.minimum(0.0).maximum(100.0)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("score");

      assertThat(prop.get("type").asString()).isEqualTo("number");
      assertThat(prop.get("minimum").asDouble()).isEqualTo(0.0);
      assertThat(prop.get("maximum").asDouble()).isEqualTo(100.0);
    }

    @Test
    void number_customizer_should_support_default_value() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().number("score", "Score", n -> n.defaultValue(95.5)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("score");

      assertThat(prop.get("default").asDouble()).isEqualTo(95.5);
    }
  }

  @Nested
  class Boolean_properties {
    @Test
    void boolean_property_should_produce_correct_schema() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().bool("active", "Is active"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("active");

      assertThat(prop.get("type").asString()).isEqualTo("boolean");
      assertThat(prop.get("description").asString()).isEqualTo("Is active");
    }

    @Test
    void boolean_property_with_default_should_include_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().bool("active", "Is active", true));
      ObjectNode prop = (ObjectNode) node.get("properties").get("active");

      assertThat(prop.get("default").asBoolean()).isTrue();
    }

    @Test
    void bool_customizer_should_support_title() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .bool("active", "Is active", b -> b.title("Active Status")));
      ObjectNode prop = (ObjectNode) node.get("properties").get("active");

      assertThat(prop.get("type").asString()).isEqualTo("boolean");
      assertThat(prop.get("title").asString()).isEqualTo("Active Status");
    }

    @Test
    void bool_customizer_should_support_default_value() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .bool("verified", "Verified?", b -> b.defaultValue(true)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("verified");

      assertThat(prop.get("default").asBoolean()).isTrue();
    }
  }

  @Nested
  class Single_select_choose {
    @Test
    void choose_with_raw_strings_should_produce_plain_enum_schema() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().choose("color", List.of("red", "green", "blue")));
      ObjectNode prop = (ObjectNode) node.get("properties").get("color");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.has("oneOf")).isFalse();
      assertThat(prop.get("enum")).hasSize(3);
      assertThat(prop.get("enum").get(0).asString()).isEqualTo("red");
      assertThat(prop.get("enum").get(1).asString()).isEqualTo("green");
      assertThat(prop.get("enum").get(2).asString()).isEqualTo("blue");
    }

    @Test
    void choose_with_items_and_titled_customizer_should_produce_one_of_schema() {
      record Status(String code, String label) {}
      List<Status> statuses =
          List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
      Consumer<SingleSelectEnumSchemaBuilder<Status>> customizer = c -> c.titled(Status::label);
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().choose("status", statuses, Status::code, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("status");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("oneOf")).hasSize(2);
      assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("active");
      assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Active");
      assertThat(prop.get("oneOf").get(1).get("const").asString()).isEqualTo("inactive");
      assertThat(prop.get("oneOf").get(1).get("title").asString()).isEqualTo("Gone");
    }

    @Test
    void choose_should_produce_plain_enum_by_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().choose("tag", Tag.class));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.has("oneOf")).isFalse();
      assertThat(prop.get("enum")).hasSize(3);
      assertThat(prop.get("enum").get(0).asString()).isEqualTo("JAVA");
      assertThat(prop.get("enum").get(1).asString()).isEqualTo("PYTHON");
      assertThat(prop.get("enum").get(2).asString()).isEqualTo("GO");
    }

    @Test
    void choose_with_titled_customizer_should_produce_one_of() {
      Consumer<SingleSelectEnumSchemaBuilder<TitledColor>> customizer =
          c -> c.titled(TitledColor::toString);
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().choose("color", TitledColor.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("color");

      assertThat(prop.get("oneOf")).hasSize(3);
      assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("RED");
      assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
    }

    @Test
    void choose_with_default_should_produce_enum_with_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().choose("tag", Tag.class, Tag.JAVA));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.has("oneOf")).isFalse();
      assertThat(prop.get("enum")).hasSize(3);
      assertThat(prop.get("default").asString()).isEqualTo("JAVA");
    }

    @Test
    void choose_with_items_and_value_fn_only_should_produce_untitled() {
      List<String> items = List.of("active", "inactive");
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().choose("status", items, Function.identity()));
      ObjectNode prop = (ObjectNode) node.get("properties").get("status");

      assertThat(prop.has("oneOf")).isFalse();
      assertThat(prop.get("enum")).hasSize(2);
      assertThat(prop.get("enum").get(0).asString()).isEqualTo("active");
    }

    @Test
    void choose_enum_with_titled_customizer_should_use_custom_titles() {
      Consumer<SingleSelectEnumSchemaBuilder<Tag>> customizer =
          c -> c.titled(t -> t.name().toLowerCase());
      ObjectNode node = toNode(new RequestedSchemaBuilder().choose("tag", Tag.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("oneOf")).hasSize(3);
      assertThat(prop.get("oneOf").get(0).get("const").asString()).isEqualTo("JAVA");
      assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("java");
    }

    @Test
    void choose_enum_with_titled_and_default_customizer_should_work() {
      Consumer<SingleSelectEnumSchemaBuilder<Tag>> customizer =
          c -> c.titled(t -> t.name().toLowerCase()).defaultValue(Tag.PYTHON);
      ObjectNode node = toNode(new RequestedSchemaBuilder().choose("tag", Tag.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("oneOf")).hasSize(3);
      assertThat(prop.get("default").asString()).isEqualTo("PYTHON");
      assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("java");
    }

    @Test
    void choose_with_arbitrary_items_and_default_should_work() {
      record Status(String code, String label) {}
      List<Status> statuses =
          List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
      Status defaultStatus = statuses.get(0);
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder().choose("status", statuses, Status::code, defaultStatus));
      ObjectNode prop = (ObjectNode) node.get("properties").get("status");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.has("oneOf")).isFalse();
      assertThat(prop.get("enum")).hasSize(2);
      assertThat(prop.get("default").asString()).isEqualTo("active");
    }

    @Test
    void choose_with_raw_strings_and_default_should_work() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .choose("color", List.of("red", "green", "blue"), "green"));
      ObjectNode prop = (ObjectNode) node.get("properties").get("color");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.has("oneOf")).isFalse();
      assertThat(prop.get("enum")).hasSize(3);
      assertThat(prop.get("default").asString()).isEqualTo("green");
    }

    @Test
    void choose_with_arbitrary_items_titled_and_default_customizer_should_work() {
      record Status(String code, String label) {}
      List<Status> statuses =
          List.of(new Status("active", "Active"), new Status("inactive", "Gone"));
      Consumer<SingleSelectEnumSchemaBuilder<Status>> customizer =
          c -> c.titled(Status::label).defaultValue(statuses.get(0));
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().choose("status", statuses, Status::code, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("status");

      assertThat(prop.get("oneOf").get(0).get("title").asString()).isEqualTo("Active");
      assertThat(prop.get("default").asString()).isEqualTo("active");
    }

    @Test
    void choose_single_select_should_include_type_string() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().choose("tag", Tag.class));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tag");

      assertThat(prop.get("type").asString()).isEqualTo("string");
    }
  }

  @Nested
  class Multi_select_choose_many {
    @Test
    void choose_many_with_raw_strings_should_produce_array_of_plain_enum_schema() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("tags", List.of("java", "python", "go")));
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
    void choose_many_with_items_and_titled_customizer_should_produce_array_of_any_of_schema() {
      record Role(String code, String label) {}
      List<Role> roles = List.of(new Role("admin", "Administrator"), new Role("user", "User"));
      Consumer<MultiSelectEnumSchemaBuilder<Role>> customizer = c -> c.titled(Role::label);
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("roles", roles, Role::code, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("roles");

      assertThat(prop.get("type").asString()).isEqualTo("array");
      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.get("anyOf")).hasSize(2);
      assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("admin");
      assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Administrator");
    }

    @Test
    void choose_many_should_produce_plain_enum_by_default() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().chooseMany("tags", Tag.class));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      assertThat(prop.get("type").asString()).isEqualTo("array");
      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.has("anyOf")).isFalse();
      assertThat(items.get("enum")).hasSize(3);
      assertThat(items.get("enum").get(0).asString()).isEqualTo("JAVA");
    }

    @Test
    void choose_many_with_titled_customizer_should_produce_any_of() {
      Consumer<MultiSelectEnumSchemaBuilder<TitledColor>> customizer =
          c -> c.titled(TitledColor::toString);
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("colors", TitledColor.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("colors");

      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.get("anyOf")).hasSize(3);
      assertThat(items.get("anyOf").get(0).get("const").asString()).isEqualTo("RED");
      assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("Crimson Red");
    }

    @Test
    void choose_many_with_items_and_value_fn_only_should_produce_untitled() {
      List<String> items = List.of("admin", "user");
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("roles", items, Function.identity()));
      ObjectNode prop = (ObjectNode) node.get("properties").get("roles");

      assertThat(prop.get("type").asString()).isEqualTo("array");
      ObjectNode itemsNode = (ObjectNode) prop.get("items");
      assertThat(itemsNode.has("anyOf")).isFalse();
      assertThat(itemsNode.get("enum")).hasSize(2);
      assertThat(itemsNode.get("enum").get(0).asString()).isEqualTo("admin");
    }

    @Test
    void choose_many_customizer_should_support_max_items() {
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("tags", Tag.class, c -> c.maxItems(3)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      assertThat(prop.get("type").asString()).isEqualTo("array");
      assertThat(prop.get("maxItems").asInt()).isEqualTo(3);
      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.get("enum")).hasSize(3);
    }

    @Test
    void choose_many_customizer_should_support_min_items() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .chooseMany("tags", Tag.class, c -> c.minItems(1).maxItems(3)));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      assertThat(prop.get("minItems").asInt()).isEqualTo(1);
      assertThat(prop.get("maxItems").asInt()).isEqualTo(3);
    }

    @Test
    void choose_many_enum_with_defaults_customizer_should_include_defaults() {
      Consumer<MultiSelectEnumSchemaBuilder<Tag>> customizer =
          c -> c.defaults(List.of(Tag.JAVA, Tag.GO));
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("tags", Tag.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      assertThat(prop.get("type").asString()).isEqualTo("array");
      assertThat(prop.get("default")).hasSize(2);
      assertThat(prop.get("default").get(0).asString()).isEqualTo("JAVA");
      assertThat(prop.get("default").get(1).asString()).isEqualTo("GO");
    }

    @Test
    void choose_many_enum_with_titled_and_defaults_customizer_should_work() {
      Consumer<MultiSelectEnumSchemaBuilder<Tag>> customizer =
          c -> c.titled(t -> t.name().toLowerCase()).defaults(List.of(Tag.PYTHON));
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("tags", Tag.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("java");
      assertThat(prop.get("default")).hasSize(1);
      assertThat(prop.get("default").get(0).asString()).isEqualTo("PYTHON");
    }

    @Test
    void choose_many_enum_with_titled_customizer_should_use_custom_titles() {
      Consumer<MultiSelectEnumSchemaBuilder<Tag>> customizer =
          c -> c.titled(t -> t.name().toLowerCase());
      ObjectNode node =
          toNode(new RequestedSchemaBuilder().chooseMany("tags", Tag.class, customizer));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.get("anyOf").get(0).get("title").asString()).isEqualTo("java");
    }

    @Test
    void choose_many_with_raw_strings_and_defaults_customizer_should_work() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .chooseMany(
                      "tags",
                      List.of("java", "python", "go"),
                      Function.identity(),
                      c -> c.defaults(List.of("java", "go"))));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      assertThat(prop.get("type").asString()).isEqualTo("array");
      assertThat(prop.get("default")).hasSize(2);
      assertThat(prop.get("default").get(0).asString()).isEqualTo("java");
      assertThat(prop.get("default").get(1).asString()).isEqualTo("go");
    }

    @Test
    void choose_many_with_raw_strings_and_list_defaults_customizer_should_work() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .chooseMany(
                      "tags",
                      List.of("java", "python", "go"),
                      Function.identity(),
                      c -> c.defaults(List.of("python"))));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      assertThat(prop.get("default")).hasSize(1);
      assertThat(prop.get("default").get(0).asString()).isEqualTo("python");
    }

    @Test
    void choose_many_items_should_include_type_string() {
      ObjectNode node = toNode(new RequestedSchemaBuilder().chooseMany("tags", Tag.class));
      ObjectNode prop = (ObjectNode) node.get("properties").get("tags");

      ObjectNode items = (ObjectNode) prop.get("items");
      assertThat(items.get("type").asString()).isEqualTo("string");
    }
  }

  @Nested
  class Legacy_enum {
    @Test
    @SuppressWarnings(
        "deprecation") // Tests deprecated chooseLegacy() per MCP spec backward compatibility
    void choose_legacy_should_produce_enum_with_enum_names() {
      ObjectNode node =
          toNode(
              new RequestedSchemaBuilder()
                  .chooseLegacy(
                      "level",
                      List.of("opt1", "opt2", "opt3"),
                      List.of("Option One", "Option Two", "Option Three")));
      ObjectNode prop = (ObjectNode) node.get("properties").get("level");

      assertThat(prop.get("type").asString()).isEqualTo("string");
      assertThat(prop.get("enum")).hasSize(3);
      assertThat(prop.get("enum").get(0).asString()).isEqualTo("opt1");
      assertThat(prop.get("enumNames")).hasSize(3);
      assertThat(prop.get("enumNames").get(0).asString()).isEqualTo("Option One");
    }
  }
}
