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
package com.callibrity.mocapi.protocol.tools.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.protocol.tools.McpToolContextScopedValueResolver;
import com.callibrity.mocapi.protocol.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.protocol.tools.util.HelloTool;
import com.callibrity.mocapi.protocol.tools.util.InteractiveTool;
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
          List.of(new McpToolContextScopedValueResolver(), new Jackson3ParameterResolver(mapper)));
  private final AnnotationMcpToolProviderFactory factory =
      new DefaultAnnotationMcpToolProviderFactory(
          new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7), invokerFactory);

  @Test
  void defaultAnnotationShouldGenerateCorrectMetadata() {
    var tools = factory.create(new HelloTool());
    assertThat(tools.getMcpTools()).hasSize(1);

    var tool = tools.getMcpTools().getFirst();
    assertThat(tool.descriptor().name()).isEqualTo("hello-tool.say-hello");
    assertThat(tool.descriptor().title()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.descriptor().description()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.descriptor().inputSchema().get("type").asString()).isEqualTo("object");
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void customAnnotationShouldReturnCorrectMetadata() {
    var tools = factory.create(new CustomizedTool());
    assertThat(tools.getMcpTools()).hasSize(1);

    var tool = tools.getMcpTools().getFirst();
    assertThat(tool.descriptor().name()).isEqualTo("custom-name");
    assertThat(tool.descriptor().title()).isEqualTo("Custom Title");
    assertThat(tool.descriptor().description()).isEqualTo("Custom description");
  }

  @Test
  void shouldCallSimpleToolCorrectly() {
    var tool = factory.create(new HelloTool()).getMcpTools().getFirst();
    var result = tool.call(mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(result).isNotNull();
    var json = mapper.valueToTree(result);
    assertThat(json.get("message").stringValue()).isEqualTo("Hello, Mocapi!");
  }

  @Test
  void simpleToolShouldNotBeInteractive() {
    var tool = factory.create(new HelloTool()).getMcpTools().getFirst();
    assertThat(tool.isInteractive()).isFalse();
  }

  @Test
  void interactiveToolShouldBeInteractive() {
    var tool = factory.create(new InteractiveTool()).getMcpTools().getFirst();
    assertThat(tool.isInteractive()).isTrue();
  }

  @Test
  void interactiveToolShouldHaveOutputSchema() {
    var tool = factory.create(new InteractiveTool()).getMcpTools().getFirst();
    assertThat(tool.descriptor().outputSchema()).isNotNull();
  }

  static class CustomizedTool {
    @ToolMethod(name = "custom-name", title = "Custom Title", description = "Custom description")
    public String doWork(String input) {
      return input;
    }
  }
}
