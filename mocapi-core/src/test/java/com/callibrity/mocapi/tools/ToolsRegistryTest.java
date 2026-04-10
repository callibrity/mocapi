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
package com.callibrity.mocapi.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.stream.McpStreamContextScopedValueResolver;
import com.callibrity.mocapi.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.annotation.DefaultAnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.tools.util.ErrorReturningTool;
import com.callibrity.mocapi.tools.util.HelloTool;
import com.callibrity.mocapi.tools.util.ThrowingTool;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import tools.jackson.databind.ObjectMapper;

class ToolsRegistryTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final MethodInvokerFactory invokerFactory =
      new DefaultMethodInvokerFactory(
          List.of(
              new Jackson3ParameterResolver(mapper), new McpStreamContextScopedValueResolver()));
  private final AnnotationMcpToolProviderFactory factory =
      new DefaultAnnotationMcpToolProviderFactory(
          new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7), invokerFactory);

  @Test
  void isEmptyShouldReturnTrueWhenNoTools() {
    var registry = new ToolsRegistry(List.of(), mapper);
    assertThat(registry.isEmpty()).isTrue();
  }

  @Test
  void isEmptyShouldReturnFalseWhenToolsExist() {
    var provider = factory.create(new HelloTool());
    var registry = new ToolsRegistry(List.of(provider), mapper);
    assertThat(registry.isEmpty()).isFalse();
  }

  @Test
  void shouldListAllTools() {

    var provider = factory.create(new HelloTool());

    var registry = new ToolsRegistry(List.of(provider), mapper);
    var response = registry.listTools(null);

    assertThat(response).isNotNull();
    assertThat(response.tools()).isNotNull();
    assertThat(response.tools()).hasSize(1);

    var tool = response.tools().getFirst();
    assertThat(tool).isNotNull();
    assertThat(tool.name()).isEqualTo("hello-tool.say-hello");
    assertThat(tool.title()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.description()).isEqualTo("Hello Tool - Say Hello");
    assertThat(tool.inputSchema().get("type").asString()).isEqualTo("object");
    assertThat(tool.outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void shouldLookupTool() {
    var provider = factory.create(new HelloTool());

    var registry = new ToolsRegistry(List.of(provider), mapper);
    var tool = registry.lookup("hello-tool.say-hello");

    assertThat(tool).isNotNull();
    assertEquals("hello-tool.say-hello", tool.descriptor().name());
  }

  @Test
  void shouldCallToolSuccessfully() {
    var provider = factory.create(new HelloTool());

    var registry = new ToolsRegistry(List.of(provider), mapper);
    var response =
        registry.callTool("hello-tool.say-hello", mapper.createObjectNode().put("name", "Mocapi"));

    assertThat(response).isNotNull();
    assertThat(response.structuredContent().get("message").stringValue())
        .isEqualTo("Hello, Mocapi!");
  }

  @Test
  void invalidInputShouldThrowException() {
    var provider = factory.create(new HelloTool());

    var registry = new ToolsRegistry(List.of(provider), mapper);
    var request = mapper.createObjectNode();
    assertThatThrownBy(() -> registry.callTool("hello-tool.say-hello", request))
        .isExactlyInstanceOf(JsonRpcException.class);
  }

  @Test
  void missingToolShouldThrowException() {
    var provider = factory.create(new HelloTool());

    var registry = new ToolsRegistry(List.of(provider), mapper);
    var request = mapper.createObjectNode();
    assertThatThrownBy(() -> registry.callTool("non-existent-tool", request))
        .isExactlyInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Tool non-existent-tool not found.");
  }

  @Test
  void runtimeExceptionFromToolShouldReturnCallToolResultWithIsErrorTrue() {
    var provider = factory.create(new ThrowingTool());
    var registry = new ToolsRegistry(List.of(provider), mapper);
    var args = mapper.createObjectNode().put("input", "test");

    var result = registry.callTool("throwing-tool.explode", args);

    assertThat(result.isError()).isTrue();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().getFirst()).isInstanceOf(TextContent.class);
    assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("tool went boom");
  }

  @Test
  void toolReturningCallToolResultWithIsErrorTrueShouldPassThrough() {
    var provider = factory.create(new ErrorReturningTool());
    var registry = new ToolsRegistry(List.of(provider), mapper);
    var args = mapper.createObjectNode().put("input", "test");

    var result = registry.callTool("error-returning-tool.fail-gracefully", args);

    assertThat(result.isError()).isTrue();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().getFirst()).isInstanceOf(TextContent.class);
    assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("handled failure");
  }
}
