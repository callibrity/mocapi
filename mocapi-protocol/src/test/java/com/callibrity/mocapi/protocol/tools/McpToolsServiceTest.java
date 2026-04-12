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
package com.callibrity.mocapi.protocol.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.protocol.CapturingTransport;
import com.callibrity.mocapi.protocol.McpTransport;
import com.callibrity.mocapi.protocol.session.McpSession;
import com.callibrity.mocapi.protocol.tools.annotation.AnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.protocol.tools.annotation.DefaultAnnotationMcpToolProviderFactory;
import com.callibrity.mocapi.protocol.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.protocol.tools.util.HelloTool;
import com.callibrity.mocapi.protocol.tools.util.InteractiveTool;
import com.callibrity.mocapi.protocol.tools.util.ThrowingTool;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class McpToolsServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final AnnotationMcpToolProviderFactory factory =
      new DefaultAnnotationMcpToolProviderFactory(
          new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7),
          new DefaultMethodInvokerFactory(
              List.of(
                  new McpToolContextScopedValueResolver(), new Jackson3ParameterResolver(mapper))));

  private McpToolsService service;

  @BeforeEach
  void setUp() {
    var helloProvider = factory.create(new HelloTool());
    var interactiveProvider = factory.create(new InteractiveTool());
    var throwingProvider = factory.create(new ThrowingTool());
    service =
        new McpToolsService(List.of(helloProvider, interactiveProvider, throwingProvider), mapper);
  }

  @Test
  void listToolsReturnsAllToolDescriptors() {
    var result = service.listTools(null);

    assertThat(result.tools()).hasSize(3);
    assertThat(result.tools().stream().map(t -> t.name()).toList())
        .containsExactly("hello-tool.say-hello", "interactive-greet", "throwing-tool.explode");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void listToolsDescriptorsHaveInputSchemas() {
    var result = service.listTools(null);

    var helloTool =
        result.tools().stream().filter(t -> t.name().equals("hello-tool.say-hello")).findFirst();
    assertThat(helloTool).isPresent();
    assertThat(helloTool.get().inputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void callSimpleToolReturnsResult() {
    var params =
        new CallToolRequestParams(
            "hello-tool.say-hello", mapper.createObjectNode().put("name", "World"), null, null);

    var result = service.callTool(params);

    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNotNull();
    assertThat(result.structuredContent().get("message").stringValue()).isEqualTo("Hello, World!");
  }

  @Test
  void callInteractiveToolWithProgressAndResult() {
    var transport = new CapturingTransport();
    var session = new McpSession("2025-11-25", null, null, LoggingLevel.DEBUG, "test-session");
    var progressToken = JsonNodeFactory.instance.textNode("tok-1");
    var meta = new RequestMeta(progressToken);
    var params =
        new CallToolRequestParams(
            "interactive-greet", mapper.createObjectNode().put("name", "Alice"), null, meta);

    var result =
        ScopedValue.where(McpTransport.CURRENT, transport)
            .where(McpSession.CURRENT, session)
            .call(() -> service.callTool(params));

    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNotNull();
    assertThat(result.structuredContent().get("message").stringValue()).isEqualTo("Hello, Alice!");

    // Verify progress and log notifications were sent through transport
    var notifications = transport.messages();
    assertThat(notifications).hasSizeGreaterThanOrEqualTo(3);

    // First message: progress 1/2
    assertThat(notifications.get(0)).isInstanceOf(JsonRpcNotification.class);
    var progress1 = (JsonRpcNotification) notifications.get(0);
    assertThat(progress1.method()).isEqualTo("notifications/progress");

    // Second message: log notification
    assertThat(notifications.get(1)).isInstanceOf(JsonRpcNotification.class);
    var logNotif = (JsonRpcNotification) notifications.get(1);
    assertThat(logNotif.method()).isEqualTo("notifications/message");

    // Third message: progress 2/2
    assertThat(notifications.get(2)).isInstanceOf(JsonRpcNotification.class);
    var progress2 = (JsonRpcNotification) notifications.get(2);
    assertThat(progress2.method()).isEqualTo("notifications/progress");
  }

  @Test
  void callMissingToolThrowsException() {
    var params = new CallToolRequestParams("nonexistent", mapper.createObjectNode(), null, null);

    assertThatThrownBy(() -> service.callTool(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Tool nonexistent not found.");
  }

  @Test
  void callToolWithInvalidInputThrowsException() {
    var params =
        new CallToolRequestParams("hello-tool.say-hello", mapper.createObjectNode(), null, null);

    assertThatThrownBy(() -> service.callTool(params)).isInstanceOf(JsonRpcException.class);
  }

  @Test
  void callThrowingToolReturnsErrorResult() {
    var params =
        new CallToolRequestParams(
            "throwing-tool.explode", mapper.createObjectNode().put("input", "test"), null, null);

    var result = service.callTool(params);

    assertThat(result.isError()).isTrue();
    assertThat(result.content()).hasSize(1);
    assertThat(result.content().getFirst()).isInstanceOf(TextContent.class);
    assertThat(((TextContent) result.content().getFirst()).text()).isEqualTo("tool went boom");
  }

  @Test
  void lookupFindsToolByName() {
    var tool = service.lookup("hello-tool.say-hello");
    assertThat(tool).isNotNull();
    assertThat(tool.descriptor().name()).isEqualTo("hello-tool.say-hello");
  }

  @Test
  void isEmptyReturnsFalseWhenToolsExist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void isEmptyReturnsTrueWhenNoTools() {
    var emptyService = new McpToolsService(List.of(), mapper);
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void toCallToolResultHandlesNullResult() {
    var result = service.toCallToolResult(null);
    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content()).hasSize(1);
    assertThat(((TextContent) result.content().getFirst()).text()).isEmpty();
  }

  @Test
  void toCallToolResultPassesThroughCallToolResult() {
    var original =
        new com.callibrity.mocapi.model.CallToolResult(
            List.of(new TextContent("test", null)), null, null);
    var result = service.toCallToolResult(original);
    assertThat(result).isSameAs(original);
  }

  @Test
  void interactiveToolOutputSchemaIsGenerated() {
    var tool = service.lookup("interactive-greet");
    assertThat(tool.descriptor().outputSchema()).isNotNull();
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void simpleToolOutputSchemaIsGenerated() {
    var tool = service.lookup("hello-tool.say-hello");
    assertThat(tool.descriptor().outputSchema()).isNotNull();
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }
}
