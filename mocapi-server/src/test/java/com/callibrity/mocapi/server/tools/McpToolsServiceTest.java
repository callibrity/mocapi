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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.LoggingLevel;
import com.callibrity.mocapi.model.RequestMeta;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.session.McpSession;
import com.callibrity.mocapi.server.tools.schema.DefaultMethodSchemaGenerator;
import com.callibrity.mocapi.server.tools.util.HelloTool;
import com.callibrity.mocapi.server.tools.util.InteractiveTool;
import com.callibrity.mocapi.server.tools.util.ThrowingTool;
import com.callibrity.mocapi.server.tools.util.TimeoutTool;
import com.callibrity.mocapi.server.tools.util.VoidTool;
import com.callibrity.ripcurl.core.JsonRpcMessage;
import com.callibrity.ripcurl.core.JsonRpcNotification;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.github.victools.jsonschema.generator.SchemaVersion;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.jwcarman.methodical.param.ParameterResolver;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class McpToolsServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final DefaultMethodSchemaGenerator generator =
      new DefaultMethodSchemaGenerator(mapper, SchemaVersion.DRAFT_7);
  private final MethodInvokerFactory invokerFactory = new DefaultMethodInvokerFactory();
  private final List<ParameterResolver<? super JsonNode>> resolvers =
      List.of(new McpToolContextResolver(), new Jackson3ParameterResolver(mapper));

  @Mock private McpResponseCorrelationService correlationService;

  private McpToolsService service;

  private List<CallToolHandler> createHandlers(Object target) {
    return MethodUtils.getMethodsListWithAnnotation(target.getClass(), McpTool.class).stream()
        .map(
            m ->
                CallToolHandlers.build(
                    target, m, generator, invokerFactory, resolvers, List.of(), List.of(), s -> s))
        .toList();
  }

  @BeforeEach
  void setUp() {
    var handlers = new ArrayList<CallToolHandler>();
    handlers.addAll(createHandlers(new HelloTool()));
    handlers.addAll(createHandlers(new InteractiveTool()));
    handlers.addAll(createHandlers(new ThrowingTool()));
    handlers.addAll(createHandlers(new VoidTool()));
    service = new McpToolsService(List.copyOf(handlers), mapper, correlationService);
  }

  @Test
  void list_tools_returns_all_tool_descriptors() {
    var result = service.listTools(null);

    assertThat(result.tools()).hasSize(4);
    assertThat(result.tools().stream().map(Tool::name).toList())
        .containsExactly(
            "fire-and-forget",
            "hello-tool.say-hello",
            "interactive-greet",
            "throwing-tool.explode");
    assertThat(result.nextCursor()).isNull();
  }

  @Test
  void list_tools_descriptors_have_input_schemas() {
    var result = service.listTools(null);

    var helloTool =
        result.tools().stream().filter(t -> t.name().equals("hello-tool.say-hello")).findFirst();
    assertThat(helloTool).isPresent();
    assertThat(helloTool.get().inputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void call_simple_tool_returns_result() {
    var params =
        new CallToolRequestParams(
            "hello-tool.say-hello", mapper.createObjectNode().put("name", "World"), null, null);

    var result = service.callTool(params);

    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNotNull();
    assertThat(result.structuredContent().get("message").stringValue()).isEqualTo("Hello, World!");
  }

  @Test
  void call_void_tool_returns_empty_result() {
    var params =
        new CallToolRequestParams(
            "fire-and-forget", mapper.createObjectNode().put("message", "hello"), null, null);

    var result = service.callTool(params);

    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content()).hasSize(1);
    assertThat(((TextContent) result.content().getFirst()).text()).isEmpty();
  }

  @Test
  void call_interactive_tool_with_progress_and_result() {
    var transport = mock(McpTransport.class);
    var session = new McpSession("test-session", "2025-11-25", null, null, LoggingLevel.DEBUG);
    var progressToken = JsonNodeFactory.instance.stringNode("tok-1");
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

    var captor = ArgumentCaptor.forClass(JsonRpcMessage.class);
    verify(transport, atLeast(3)).send(captor.capture());
    var notifications = captor.getAllValues();

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
  void call_missing_tool_throws_exception() {
    var params = new CallToolRequestParams("nonexistent", mapper.createObjectNode(), null, null);

    assertThatThrownBy(() -> service.callTool(params))
        .isInstanceOf(JsonRpcException.class)
        .hasMessageContaining("Tool nonexistent not found.");
  }

  @Test
  void call_tool_with_invalid_input_returns_error_result() {
    // Input-schema validation now runs as an innermost MethodInterceptor per handler; the
    // JsonRpcException it throws is caught by invokeTool and surfaced as
    // CallToolResult.isError=true
    // so the calling LLM can self-correct, matching the MCP spec's "input validation errors belong
    // in the result body" guidance.
    var params =
        new CallToolRequestParams("hello-tool.say-hello", mapper.createObjectNode(), null, null);

    var result = service.callTool(params);

    assertThat(result.isError()).isTrue();
    assertThat(result.content()).hasSize(1);
    assertThat(((TextContent) result.content().getFirst()).text()).isNotBlank();
  }

  @Test
  void call_throwing_tool_returns_error_result() {
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
  void lookup_finds_tool_by_name() {
    var tool = service.lookup("hello-tool.say-hello");
    assertThat(tool).isNotNull();
    assertThat(tool.descriptor().name()).isEqualTo("hello-tool.say-hello");
  }

  @Test
  void is_empty_returns_false_when_tools_exist() {
    assertThat(service.isEmpty()).isFalse();
  }

  @Test
  void is_empty_returns_true_when_no_tools() {
    var emptyService = new McpToolsService(List.of(), mapper, correlationService);
    assertThat(emptyService.isEmpty()).isTrue();
  }

  @Test
  void call_tool_timeout_returns_error_result_with_descriptive_message() {
    var timeoutHandlers = createHandlers(new TimeoutTool());
    var timeoutService = new McpToolsService(timeoutHandlers, mapper, correlationService);
    var params =
        new CallToolRequestParams(
            "timeout-tool.simulate-timeout",
            mapper.createObjectNode().put("input", "x"),
            null,
            null);

    var result = timeoutService.callTool(params);

    assertThat(result.isError()).isTrue();
    assertThat(result.content()).hasSize(1);
    assertThat(((TextContent) result.content().getFirst()).text())
        .contains("Timed out waiting for client response");
  }

  @Test
  void to_call_tool_result_handles_null_result() {
    var result = service.toCallToolResult(null);
    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content()).hasSize(1);
    assertThat(((TextContent) result.content().getFirst()).text()).isEmpty();
  }

  @Test
  void to_call_tool_result_passes_through_call_tool_result() {
    var original =
        new com.callibrity.mocapi.model.CallToolResult(
            List.of(new TextContent("test", null)), null, null);
    var result = service.toCallToolResult(original);
    assertThat(result).isSameAs(original);
  }

  @Test
  void interactive_tool_output_schema_is_generated() {
    var tool = service.lookup("interactive-greet");
    assertThat(tool.descriptor().outputSchema()).isNotNull();
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void simple_tool_output_schema_is_generated() {
    var tool = service.lookup("hello-tool.say-hello");
    assertThat(tool.descriptor().outputSchema()).isNotNull();
    assertThat(tool.descriptor().outputSchema().get("type").asString()).isEqualTo("object");
  }

  @Test
  void void_tool_has_no_output_schema() {
    var tool = service.lookup("fire-and-forget");
    assertThat(tool.descriptor().outputSchema()).isNull();
  }

  @Test
  void call_tool_with_null_arguments_falls_to_empty_object() {
    var params = new CallToolRequestParams("fire-and-forget", null, null, null);

    // Null arguments are replaced with an empty ObjectNode, which then fails schema validation
    // in the input-schema interceptor because the tool requires a "message" property — proving the
    // null-to-empty fallback executed.
    var result = service.callTool(params);

    assertThat(result.isError()).isTrue();
    assertThat(((TextContent) result.content().getFirst()).text()).containsIgnoringCase("required");
  }

  @Test
  void to_call_tool_result_with_non_object_result_has_null_structured_content() {
    var result = service.toCallToolResult("just a string");

    assertThat(result.isError()).isNull();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content()).hasSize(1);
    assertThat(((TextContent) result.content().getFirst()).text()).contains("just a string");
  }

  @Test
  void to_error_call_tool_result_uses_to_string_when_message_is_null() {
    var exception = new RuntimeException((String) null);
    var result = McpToolsService.toErrorCallToolResult(exception);

    assertThat(result.isError()).isTrue();
    assertThat(result.content()).hasSize(1);
    String text = ((TextContent) result.content().getFirst()).text();
    assertThat(text).isEqualTo(exception.toString());
  }
}
