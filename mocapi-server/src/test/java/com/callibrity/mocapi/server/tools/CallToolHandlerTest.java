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
package com.callibrity.mocapi.server.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.api.tools.McpToolContext;
import com.callibrity.mocapi.api.tools.McpToolParams;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.HelloTool;
import com.callibrity.mocapi.server.tools.util.InteractiveTool;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.jwcarman.methodical.param.ParameterResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CallToolHandlerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory();
  private final List<ParameterResolver<? super JsonNode>> resolvers =
      List.of(new McpToolContextResolver(), new Jackson3ParameterResolver(mapper));
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);

  private List<CallToolHandler> createHandlers(Object target) {
    return CallToolHandlers.discover(
        target, generator, invokerFactory, resolvers, List.of(), s -> s);
  }

  @Test
  void default_annotation_should_generate_correct_metadata() {
    var handlers = createHandlers(new HelloTool());
    assertThat(handlers).hasSize(1);

    var handler = handlers.getFirst();
    assertThat(handler.descriptor().name()).isEqualTo("hello-tool.say-hello");
    assertThat(handler.descriptor().title()).isEqualTo("Hello Tool - Say Hello");
    assertThat(handler.descriptor().description()).isEqualTo("Hello Tool - Say Hello");
    assertThat(handler.descriptor().inputSchema().get("type").asString()).isEqualTo("object");
    assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
    assertThat(handler.name()).isEqualTo("hello-tool.say-hello");
    assertThat(handler.method().getName()).isEqualTo("sayHello");
    assertThat(handler.bean()).isInstanceOf(HelloTool.class);
  }

  @Test
  void custom_annotation_should_return_correct_metadata() {
    var handlers = createHandlers(new CustomizedTool());
    assertThat(handlers).hasSize(1);

    var handler = handlers.getFirst();
    assertThat(handler.descriptor().name()).isEqualTo("custom-name");
    assertThat(handler.descriptor().title()).isEqualTo("Custom Title");
    assertThat(handler.descriptor().description()).isEqualTo("Custom description");
  }

  @Test
  void should_call_simple_tool_correctly() {
    var handler = createHandlers(new HelloTool()).getFirst();
    var result = handler.call(mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(result).isNotNull();
    var json = mapper.valueToTree(result);
    assertThat(json.get("message").stringValue()).isEqualTo("Hello, Mocapi!");
  }

  @Test
  void interactive_tool_should_have_output_schema() {
    var handler = createHandlers(new InteractiveTool()).getFirst();
    assertThat(handler.descriptor().outputSchema()).isNotNull();
    assertThat(handler.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void void_tool_should_have_null_output_schema() {
    var handlers = createHandlers(new BoxedVoidTool());
    assertThat(handlers).hasSize(1);
    assertThat(handlers.getFirst().descriptor().outputSchema()).isNull();
  }

  @Test
  void mcp_tool_params_with_other_non_context_param_should_throw() {
    var target = new InvalidMixedParamsTool();
    assertThatThrownBy(() -> createHandlers(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("@McpToolParams");
  }

  @Test
  void mcp_tool_params_with_context_param_only_should_succeed() {
    var handlers = createHandlers(new ValidParamsWithContextTool());
    assertThat(handlers).hasSize(1);
  }

  static class CustomizedTool {
    @McpTool(name = "custom-name", title = "Custom Title", description = "Custom description")
    public String doWork(String input) {
      return input;
    }
  }

  static class BoxedVoidTool {
    @McpTool
    public Void doNothing(String input) {
      return null;
    }
  }

  static class InvalidMixedParamsTool {
    @McpTool
    public String doWork(@McpToolParams String params, String extra) {
      return params;
    }
  }

  record SimpleParams(String value) {}

  static class ValidParamsWithContextTool {
    @McpTool
    public String doWork(@McpToolParams SimpleParams params, McpToolContext ctx) {
      return params.value();
    }
  }
}
