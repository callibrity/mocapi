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
package com.callibrity.mocapi.tools.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.stream.McpStreamContextScopedValueResolver;
import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.callibrity.mocapi.tools.util.NullTool;
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
          List.of(
              new Jackson3ParameterResolver(mapper), new McpStreamContextScopedValueResolver()));
  private final AnnotationMcpToolProviderFactory factory =
      new DefaultAnnotationMcpToolProviderFactory(
          new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7), invokerFactory);

  @Test
  void nonCustomizedAnnotationShouldReturnCorrectMetadata() {
    var tools = factory.create(new HelloTool());
    assertThat(tools.getMcpTools()).hasSize(1);

    var tool = tools.getMcpTools().getFirst();
    assertThat(tool).isNotNull();
    assertThat(tool.name()).isEqualTo("hello-tool.say-hello");
    assertThat(tool.title()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.description()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.inputSchema().get("type").asString()).isEqualTo("object");
    assertThat(tool.outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void customizedAnnotationShouldReturnCorrectMetadata() {
    var tools = factory.create(new CustomizedTool());
    assertThat(tools.getMcpTools()).hasSize(1);

    var tool = tools.getMcpTools().getFirst();
    assertThat(tool).isNotNull();
    assertThat(tool.name()).isEqualTo("custom name");
    assertThat(tool.title()).isEqualTo("Custom Title");
    assertThat(tool.description()).isEqualTo("Custom description of a tool");
  }

  @Test
  void shouldCallToolCorrectly() {
    var tool = factory.create(new HelloTool()).getMcpTools().getFirst();
    var result = tool.call(mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(result).isNotNull();
    var json = mapper.valueToTree(result);
    assertThat(json.get("message").stringValue()).isEqualTo("Hello, Mocapi!");
  }

  @Test
  void nullReturnShouldReturnNull() {
    var tool = factory.create(new NullTool()).getMcpTools().getFirst();
    var result = tool.call(mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(result).isNull();
  }

  @Test
  void nonObjectReturnTypeShouldBeAllowed() {
    var tool = factory.create(new InvalidReturnTool()).getMcpTools().getFirst();
    assertThat(tool).isNotNull();
    assertThat(tool.outputSchema()).isNotNull();
  }
}
