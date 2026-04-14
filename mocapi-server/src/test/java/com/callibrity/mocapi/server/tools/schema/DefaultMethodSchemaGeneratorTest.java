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
package com.callibrity.mocapi.server.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.api.tools.McpToolParams;
import com.github.victools.jsonschema.generator.SchemaVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class DefaultMethodSchemaGeneratorTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

  // --- Test fixtures ---

  record SimpleParams(String name, int age) {}

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
  class GenerateInputSchemaFromRecord {

    @Test
    void generatesObjectSchemaFromRecordParameter() throws Exception {
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
    void includesRequiredFieldsFromValidationAnnotations() throws Exception {
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
    void removesSchemaVersionFromInnerSchemaAndAddsToOuter() throws Exception {
      var target = new ToolWithRecordParam();
      Method method = ToolWithRecordParam.class.getMethod("doWork", SimpleParams.class);

      ObjectNode schema = generator.generateInputSchema(target, method);

      assertThat(schema.get("$schema").asString()).contains("draft-07");
      assertThat(schema.get("type").asString()).isEqualTo("object");
    }
  }

  @Nested
  class GenerateInputSchemaFromParameters {

    @Test
    void excludesNullableParameterFromRequiredArray() throws Exception {
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
  class GenerateSchema {

    @Test
    void generatesSchemaForSimpleClass() {
      ObjectNode schema = generator.generateSchema(SimpleParams.class);

      assertThat(schema).isNotNull();
      assertThat(schema.has("properties")).isTrue();

      ObjectNode properties = (ObjectNode) schema.get("properties");
      assertThat(properties.has("name")).isTrue();
      assertThat(properties.has("age")).isTrue();
    }

    @Test
    void generatesSchemaForPrimitiveType() {
      ObjectNode schema = generator.generateSchema(String.class);

      assertThat(schema).isNotNull();
      assertThat(schema.has("type")).isTrue();
      assertThat(schema.get("type").asString()).isEqualTo("string");
    }
  }
}
