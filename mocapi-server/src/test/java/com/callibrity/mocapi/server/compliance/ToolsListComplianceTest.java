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

import com.callibrity.mocapi.api.tools.McpTool;
import com.callibrity.mocapi.model.ServerCapabilities;
import com.callibrity.mocapi.model.Tool;
import com.callibrity.mocapi.model.ToolsCapability;
import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.McpTransport;
import com.callibrity.mocapi.server.tools.McpToolsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * MCP 2025-11-25 § Server / Tools — Listing.
 *
 * <p>Verifies tools/list returns registered tools with correct descriptors and pagination.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ToolsListComplianceTest {

  private McpServer server;
  private McpServer emptyServer;

  @BeforeEach
  void setUp() {
    var inputSchema = MAPPER.createObjectNode().put("type", "object");
    inputSchema.putObject("properties").putObject("message").put("type", "string");

    var outputSchema = MAPPER.createObjectNode().put("type", "object");

    McpTool echoTool = simpleTool("echo", "Echoes input", inputSchema, null);
    McpTool greetTool = simpleTool("greet", "Greets user", inputSchema, outputSchema);

    var toolsService =
        new McpToolsService(
            List.of(() -> List.of(echoTool, greetTool)),
            MAPPER,
            mock(com.callibrity.mocapi.server.McpResponseCorrelationService.class));

    server =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(new ToolsCapability(null), null, null, null, null),
            toolsService);

    var emptyToolsService =
        new McpToolsService(
            List.of(),
            MAPPER,
            mock(com.callibrity.mocapi.server.McpResponseCorrelationService.class));
    emptyServer =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(null, null, null, null, null),
            emptyToolsService);
  }

  @Test
  void returns_all_registered_tools() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("tools/list"), transport);

    var result = captureResult(transport);
    var tools = result.result().path("tools");
    assertThat(tools.isArray()).isTrue();
    assertThat(tools.size()).isEqualTo(2);
  }

  @Test
  void each_tool_has_name_description_and_input_schema() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("tools/list"), transport);

    var result = captureResult(transport);
    var firstTool = result.result().path("tools").get(0);
    assertThat(firstTool.has("name")).isTrue();
    assertThat(firstTool.has("description")).isTrue();
    assertThat(firstTool.has("inputSchema")).isTrue();
  }

  @Test
  void tools_with_output_schema_include_it() {
    var sessionId = initializeAndGetSessionId(server);
    var transport = mock(McpTransport.class);

    server.handleCall(withSession(sessionId, server), call("tools/list"), transport);

    var result = captureResult(transport);
    var tools = result.result().path("tools");
    var greet = findToolByName(tools, "greet");
    assertThat(greet).isNotNull();
    assertThat(greet.has("outputSchema")).isTrue();
  }

  @Test
  void pagination_with_cursor_returns_next_page() {
    var inputSchema = MAPPER.createObjectNode().put("type", "object");
    var tools = new java.util.ArrayList<McpTool>();
    for (int i = 0; i < 3; i++) {
      tools.add(simpleTool("tool-" + i, "Tool " + i, inputSchema, null));
    }

    var toolsService =
        new McpToolsService(
            List.of(() -> List.copyOf(tools)),
            MAPPER,
            mock(com.callibrity.mocapi.server.McpResponseCorrelationService.class),
            2);

    var pagedServer =
        buildServer(
            inMemorySessionStore(),
            new ServerCapabilities(new ToolsCapability(null), null, null, null, null),
            toolsService);

    var sessionId = initializeAndGetSessionId(pagedServer);

    var transport1 = mock(McpTransport.class);
    pagedServer.handleCall(withSession(sessionId, pagedServer), call("tools/list"), transport1);
    var page1 = captureResult(transport1);
    assertThat(page1.result().path("tools").size()).isEqualTo(2);
    assertThat(page1.result().has("nextCursor")).isTrue();

    var cursor = page1.result().path("nextCursor").asString();
    var transport2 = mock(McpTransport.class);
    pagedServer.handleCall(
        withSession(sessionId, pagedServer),
        call("tools/list", Map.of("cursor", cursor)),
        transport2);
    var page2 = captureResult(transport2);
    assertThat(page2.result().path("tools").size()).isEqualTo(1);
    assertThat(page2.result().has("nextCursor")).isFalse();
  }

  @Test
  void empty_tool_registry_returns_empty_list_and_no_cursor() {
    var sessionId = initializeAndGetSessionId(emptyServer);
    var transport = mock(McpTransport.class);

    emptyServer.handleCall(withSession(sessionId, emptyServer), call("tools/list"), transport);

    var result = captureResult(transport);
    assertThat(result.result().path("tools").size()).isZero();
    assertThat(result.result().has("nextCursor")).isFalse();
  }

  // --- helpers ---

  private static McpTool simpleTool(
      String name, String description, ObjectNode inputSchema, ObjectNode outputSchema) {
    return new McpTool() {
      @Override
      public Tool descriptor() {
        return new Tool(name, null, description, inputSchema, outputSchema);
      }

      @Override
      public Object call(JsonNode arguments) {
        return Map.of("echo", "ok");
      }
    };
  }

  private static JsonNode findToolByName(JsonNode tools, String name) {
    for (var tool : tools) {
      if (name.equals(tool.path("name").asString())) {
        return tool;
      }
    }
    return null;
  }
}
