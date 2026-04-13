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
package com.callibrity.mocapi.server.compliance;

import static com.callibrity.mocapi.server.compliance.ComplianceTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.callibrity.mocapi.model.CallToolResult;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.TextContent;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpResponseCorrelationService;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.tools.McpTool;
import com.callibrity.mocapi.server.tools.McpToolsService;
import com.callibrity.ripcurl.core.JsonRpcProtocol;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MCP 2025-11-25 § Server / Tools — Calling.
 *
 * <p>Verifies tools/call: simple results, void tools, direct CallToolResult return, error handling,
 * structured content, and unknown tool behavior.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolsCallComplianceTest {

  private McpServer server;

  @BeforeEach
  void setUp() {
    var inputSchema = MAPPER.createObjectNode().put("type", "object");
    inputSchema.putObject("properties").putObject("input").put("type", "string");

    McpTool simpleTool = makeTool("simple", inputSchema, args -> Map.of("value", "hello"));

    McpTool voidTool =
        makeTool(
            "void-tool",
            inputSchema,
            args -> {
              return null;
            });

    McpTool directResultTool =
        makeTool(
            "direct-result",
            inputSchema,
            args -> new CallToolResult(List.of(new TextContent("direct", null)), null, null));

    McpTool throwingJsonRpcTool =
        makeTool(
            "throws-jsonrpc",
            inputSchema,
            args -> {
              throw new JsonRpcException(JsonRpcProtocol.INTERNAL_ERROR, "protocol failure");
            });

    McpTool throwingOtherTool =
        makeTool(
            "throws-other",
            inputSchema,
            args -> {
              throw new RuntimeException("tool went boom");
            });

    McpTool structuredTool =
        makeTool("structured", inputSchema, args -> Map.of("key", "structured-value"));

    var toolsService =
        new McpToolsService(
            List.of(
                () ->
                    List.of(
                        simpleTool,
                        voidTool,
                        directResultTool,
                        throwingJsonRpcTool,
                        throwingOtherTool,
                        structuredTool)),
            MAPPER,
            mock(McpResponseCorrelationService.class));

    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(new ToolsCapability(null), null, null, null, null),
            toolsService);
  }

  @Test
  void simple_tool_returns_content_array_with_result() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "simple", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    var content = result.result().path("content");
    assertThat(content.isArray()).isTrue();
    assertThat(content.size()).isGreaterThan(0);
  }

  @Test
  void void_tool_returns_empty_content() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "void-tool", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    var content = result.result().path("content");
    assertThat(content.isArray()).isTrue();
  }

  @Test
  void tool_returning_call_tool_result_directly_is_passed_through() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "direct-result", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    var firstContent = result.result().path("content").get(0);
    assertThat(firstContent.path("text").asString()).isEqualTo("direct");
  }

  @Test
  void tool_throwing_json_rpc_exception_returns_is_error_result_not_protocol_error() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "throws-jsonrpc", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().path("isError").booleanValue()).isTrue();
    var text = result.result().path("content").get(0).path("text").asString();
    assertThat(text).isEqualTo("protocol failure");
  }

  @Test
  void tool_throwing_other_exception_returns_is_error_result() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "throws-other", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().path("isError").booleanValue()).isTrue();
    var text = result.result().path("content").get(0).path("text").asString();
    assertThat(text).isEqualTo("tool went boom");
  }

  @Test
  void unknown_tool_name_returns_invalid_params_error() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "nonexistent", "arguments", Map.of())),
        transport);

    var error = captureError(transport);
    assertThat(error.error().code()).isEqualTo(JsonRpcProtocol.INVALID_PARAMS);
  }

  @Test
  void tool_with_structured_content_includes_it_in_result() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "structured", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().has("structuredContent")).isTrue();
    assertThat(result.result().path("structuredContent").path("key").asString())
        .isEqualTo("structured-value");
  }

  @Test
  void tool_result_includes_both_content_and_structured_content() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(
        withSession(sessionId),
        call("tools/call", Map.of("name", "structured", "arguments", Map.of("input", "test"))),
        transport);

    var result = captureResult(transport);
    assertThat(result.result().has("content")).isTrue();
    assertThat(result.result().path("content").isArray()).isTrue();
    assertThat(result.result().has("structuredContent")).isTrue();
    assertThat(result.result().path("structuredContent").isObject()).isTrue();
  }

  // --- helpers ---

  private static McpTool makeTool(
      String name, ObjectNode inputSchema, java.util.function.Function<JsonNode, Object> fn) {
    return new McpTool() {
      @Override
      public Tool descriptor() {
        return new Tool(name, null, name, inputSchema, null);
      }

      @Override
      public Object call(JsonNode arguments) {
        return fn.apply(arguments);
      }
    };
  }
}
