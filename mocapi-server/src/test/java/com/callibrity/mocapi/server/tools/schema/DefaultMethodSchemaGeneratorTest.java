/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpToolParams;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodSchemaGeneratorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_2020_12);

  // --- Test fixtures ---

  record SimpleParams(String name, int age) {}

  record OptionalParams(String name, Optional<String> nickname) {}

  record NullableAnnotatedParams(String name, @Nullable String nickname) {}

  record SchemaAnnotatedParams(
      String name, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String nickname) {}

  record HelloResult(String greeting) {}

  static class ToolWithRecordReturn {
    public HelloResult doWork(@McpToolParams SimpleParams params) {
      return new HelloResult("hello, " + params.name());
    }
  }

  static class ToolWithOptionalRecordParam {
    public String doWork(@McpToolParams OptionalParams params) {
      return params.name();
    }
  }

  static class ToolWithNullableAnnotatedRecordParam {
    public String doWork(@McpToolParams NullableAnnotatedParams params) {
      return params.name();
    }
  }

  static class ToolWithSchemaAnnotatedRecordParam {
    public String doWork(@McpToolParams SchemaAnnotatedParams params) {
      return params.name();
    }
  }

  record RequiredParams(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name, String optional) {}

  static class ToolWithRecordParam {
    public String doWork(@McpToolParams SimpleParams params) {
      return params.name();
    }
  }

  static class ToolWithRequiredRecordParam {
    public String doWork(@McpToolParams RequiredParams params) {
      return params.name();
    }
  }

  static class ToolWithOptionalParameters {
    public String doWork(
        @Schema(description = "required name") String name,
        @Schema(description = "optional nickname") @Nullable String nickname) {
      return nickname == null ? name : name + " (" + nickname + ")";
    }
  }

  @Nested
  class Generate_input_schema_from_record {

    @Test
    void generates_object_schema_from_record_parameter() throws Exception {
      var target = new ToolWithRecordParam();
      Method method = ToolWithRecordParam.class.getMethod("doWork", SimpleParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      assertThat(schema.has("$schema")).isTrue();
      assertThat(schema.get("type").asString()).isEqualTo("object");
      assertThat(schema.has("properties")).isTrue();

      ObjectNode properties = (ObjectNode) schema.get("properties");
      assertThat(properties.has("name")).isTrue();
      assertThat(properties.has("age")).isTrue();
    }

    @Test
    void includes_required_fields_from_validation_annotations() throws Exception {
      var target = new ToolWithRequiredRecordParam();
      Method method = ToolWithRequiredRecordParam.class.getMethod("doWork", RequiredParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      assertThat(schema.has("required")).isTrue();
      var required = schema.get("required");
      assertThat(required.isArray()).isTrue();

      boolean hasName = false;
      for (var element : required) {
        if ("name".equals(element.asString())) {
          hasName = true;
        }
      }
      assertThat(hasName).as("required array should contain 'name'").isTrue();
    }

    @Test
    void marks_every_record_component_as_required_by_default() throws Exception {
      var target = new ToolWithRecordParam();
      Method method = ToolWithRecordParam.class.getMethod("doWork", SimpleParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      var required = new ArrayList<String>();
      schema.get("required").forEach(n -> required.add(n.asString()));
      assertThat(required).containsExactlyInAnyOrder("name", "age");
    }

    @Test
    void omits_optional_record_component_from_required() throws Exception {
      var target = new ToolWithOptionalRecordParam();
      Method method = ToolWithOptionalRecordParam.class.getMethod("doWork", OptionalParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      var required = new ArrayList<String>();
      schema.get("required").forEach(n -> required.add(n.asString()));
      assertThat(required).containsExactly("name");
      var properties = (ObjectNode) schema.get("properties");
      assertThat(properties.has("nickname")).isTrue();
    }

    @Test
    void omits_nullable_annotated_record_component_from_required() throws Exception {
      var target = new ToolWithNullableAnnotatedRecordParam();
      Method method =
          ToolWithNullableAnnotatedRecordParam.class.getMethod(
              "doWork", NullableAnnotatedParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      var required = new ArrayList<String>();
      schema.get("required").forEach(n -> required.add(n.asString()));
      assertThat(required).containsExactly("name");
    }

    @Test
    void omits_schema_not_required_record_component_from_required() throws Exception {
      var target = new ToolWithSchemaAnnotatedRecordParam();
      Method method =
          ToolWithSchemaAnnotatedRecordParam.class.getMethod("doWork", SchemaAnnotatedParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      var required = new ArrayList<String>();
      schema.get("required").forEach(n -> required.add(n.asString()));
      assertThat(required).containsExactly("name");
    }

    @Test
    void removes_schema_version_from_inner_schema_and_adds_to_outer() throws Exception {
      var target = new ToolWithRecordParam();
      Method method = ToolWithRecordParam.class.getMethod("doWork", SimpleParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      assertThat(schema.get("$schema").asString()).contains("draft/2020-12");
      assertThat(schema.get("type").asString()).isEqualTo("object");
    }
  }

  @Nested
  class Generate_input_schema_from_parameters {

    @Test
    void excludes_nullable_parameter_from_required_array() throws Exception {
      var target = new ToolWithOptionalParameters();
      Method method =
          ToolWithOptionalParameters.class.getMethod("doWork", String.class, String.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      var required = new ArrayList<String>();
      schema.get("required").forEach(n -> required.add(n.asString()));
      assertThat(required).containsExactly("name");
      var properties = (ObjectNode) schema.get("properties");
      assertThat(properties.has("name")).isTrue();
      assertThat(properties.has("nickname")).isTrue();
    }
  }

  @Nested
  class Generate_output_schema {

    @Test
    void marks_every_record_component_as_required_by_default() throws Exception {
      var target = new ToolWithRecordReturn();
      Method method = ToolWithRecordReturn.class.getMethod("doWork", SimpleParams.class);

      ObjectNode schema = generator.generateOutputSchema(target, method);

      var required = new ArrayList<String>();
      schema.get("required").forEach(n -> required.add(n.asString()));
      assertThat(required).containsExactly("greeting");
    }
  }

  @Nested
  class Generate_schema {

    @Test
    void generates_schema_for_simple_class() {
      ObjectNode schema = generator.generateSchema(SimpleParams.class);

      assertThat(schema).isNotNull();
      assertThat(schema.has("properties")).isTrue();

      ObjectNode properties = (ObjectNode) schema.get("properties");
      assertThat(properties.has("name")).isTrue();
      assertThat(properties.has("age")).isTrue();
    }

    @Test
    void generates_schema_for_primitive_type() {
      ObjectNode schema = generator.generateSchema(String.class);

      assertThat(schema).isNotNull();
      assertThat(schema.has("type")).isTrue();
      assertThat(schema.get("type").asString()).isEqualTo("string");
    }
  }
}
