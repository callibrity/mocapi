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
package com.callibrity.mocapi.server.tools.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.api.tools.ToolMethod;
import com.callibrity.mocapi.server.tools.McpToolContextResolver;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.HelloTool;
import com.callibrity.mocapi.server.tools.util.InteractiveTool;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import tools.jackson.databind.ObjectMapper;

class AnnotationMcpToolTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MethodInvokerFactory invokerFactory =
      new DefaultMethodInvokerFactory(
          List.of(new McpToolContextResolver(), new Jackson3ParameterResolver(mapper)));
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

  private List<AnnotationMcpTool> createTools(Object target) {
    return AnnotationMcpTool.createTools(generator, invokerFactory, target);
  }

  @Test
  void defaultAnnotationShouldGenerateCorrectMetadata() {
    var tools = createTools(new HelloTool());
    assertThat(tools).hasSize(1);

    var tool = tools.getFirst();
    assertThat(tool.descriptor().name()).isEqualTo("hello-tool.say-hello");
    assertThat(tool.descriptor().title()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.descriptor().description()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.descriptor().inputSchema().get("type").asString()).isEqualTo("object");
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void customAnnotationShouldReturnCorrectMetadata() {
    var tools = createTools(new CustomizedTool());
    assertThat(tools).hasSize(1);

    var tool = tools.getFirst();
    assertThat(tool.descriptor().name()).isEqualTo("custom-name");
    assertThat(tool.descriptor().title()).isEqualTo("Custom Title");
    assertThat(tool.descriptor().description()).isEqualTo("Custom description");
  }

  @Test
  void shouldCallSimpleToolCorrectly() {
    var tool = createTools(new HelloTool()).getFirst();
    var result = tool.call(mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(result).isNotNull();
    var json = mapper.valueToTree(result);
    assertThat(json.get("message").stringValue()).isEqualTo("Hello, Mocapi!");
  }

  @Test
  void interactiveToolShouldHaveOutputSchema() {
    var tool = createTools(new InteractiveTool()).getFirst();
    assertThat(tool.descriptor().outputSchema()).isNotNull();
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void voidToolShouldHaveNullOutputSchema() {
    var tools = createTools(new BoxedVoidTool());
    assertThat(tools).hasSize(1);
    assertThat(tools.getFirst().descriptor().outputSchema()).isNull();
  }

  @Test
  void mcpToolParamsWithOtherNonContextParamShouldThrow() {
    assertThatThrownBy(() -> createTools(new InvalidMixedParamsTool()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("@McpToolParams");
  }

  @Test
  void mcpToolParamsWithContextParamOnlyShouldSucceed() {
    var tools = createTools(new ValidParamsWithContextTool());
    assertThat(tools).hasSize(1);
  }

  static class CustomizedTool {
    @ToolMethod(name = "custom-name", title = "Custom Title", description = "Custom description")
    public String doWork(String input) {
      return input;
    }
  }

  static class BoxedVoidTool {
    @ToolMethod
    public Void doNothing(String input) {
      return null;
    }
  }

  static class InvalidMixedParamsTool {
    @ToolMethod
    public String doWork(@McpToolParams String params, String extra) {
      return params;
    }
  }

  record SimpleParams(String value) {}

  static class ValidParamsWithContextTool {
    @ToolMethod
    public String doWork(@McpToolParams SimpleParams params, McpToolContext ctx) {
      return params.value();
    }
  }
}
