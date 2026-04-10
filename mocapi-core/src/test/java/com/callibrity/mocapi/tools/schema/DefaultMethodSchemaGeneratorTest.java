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
package com.callibrity.mocapi.tools.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.stream.McpStreamContext;
import com.callibrity.mocapi.tools.annotation.McpToolParams;
import com.callibrity.mocapi.tools.annotation.ToolMethod;
import com.callibrity.mocapi.tools.util.HelloResponse;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.github.victools.jsonschema.generator.SchemaVersion;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

class DefaultMethodSchemaGeneratorTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void testMethodSchemaGeneration() throws Exception {
    var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

    var method = HelloTool.class.getMethod("sayHello", String.class);

    var schema = generator.generateInputSchema(new HelloTool(), method);
    assertThat(schema).isNotNull();
    assertThat(schema.get("properties")).isNotNull();
  }

  @Test
  void optionalParametersShouldNotBeRequired() throws Exception {
    var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

    var method = TestTools.class.getMethod("withOptionalParameter", String.class);

    var schema = generator.generateInputSchema(new TestTools(), method);
    assertThat(schema.get("required")).isNull();
  }

  @Test
  void mixedParametersShouldBeMarkedAsRequiredCorrectly() throws Exception {
    var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

    var method = TestTools.class.getMethod("withMixedParameters", String.class, String.class);

    var schema = generator.generateInputSchema(new TestTools(), method);
    assertThat(schema.get("required")).hasSize(1);
    assertThat(schema.get("required")).containsExactly(StringNode.valueOf("name"));
  }

  @Test
  void mcpToolParamsOnRecordShouldProduceCorrectInputSchema() throws Exception {
    var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

    var method = RecordParamTools.class.getMethod("greet", GreetRequest.class);
    var schema = generator.generateInputSchema(new RecordParamTools(), method);

    assertThat(schema.get("type").asString()).isEqualTo("object");
    assertThat(schema.get("properties")).isNotNull();
    assertThat(schema.get("properties").has("name")).isTrue();
    assertThat(schema.get("properties").has("volume")).isTrue();
  }

  @Test
  void mcpToolParamsWithStreamContextShouldProduceCorrectInputSchema() throws Exception {
    var generator = new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

    var method =
        RecordParamTools.class.getMethod(
            "greetStreaming", GreetRequest.class, McpStreamContext.class);
    var schema = generator.generateInputSchema(new RecordParamTools(), method);

    assertThat(schema.get("type").asString()).isEqualTo("object");
    assertThat(schema.get("properties")).isNotNull();
    assertThat(schema.get("properties").has("name")).isTrue();
    assertThat(schema.get("properties").has("volume")).isTrue();
  }

  public record GreetRequest(String name, int volume) {}

  public static class RecordParamTools {

    @ToolMethod(name = "greet")
    public HelloResponse greet(@McpToolParams GreetRequest request) {
      return null;
    }

    @ToolMethod(name = "greet-streaming")
    public void greetStreaming(
        @McpToolParams GreetRequest request, McpStreamContext<HelloResponse> ctx) {}
  }

  public static class TestTools {

    @ToolMethod(name = "with-optional-parameter")
    public HelloResponse withOptionalParameter(@Nullable String name) {
      return null;
    }

    @ToolMethod(name = "with-mixed-parameter")
    public HelloResponse withMixedParameters(String name, @Nullable String optional) {
      return null;
    }
  }
}
