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
package com.callibrity.mocapi.autoconfigure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.McpServer;
import com.callibrity.mocapi.server.exception.McpInvalidParamsException;
import com.callibrity.mocapi.tools.McpToolsCapability;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class McpStreamingControllerTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";

  private McpStreamingController controller;
  private McpSessionManager sessionManager;
  private ObjectMapper objectMapper;
  private McpToolsCapability toolsCapability;
  private OdysseyStreamRegistry registry;

  @BeforeEach
  void setUp() {
    McpServer mcpServer = new McpServer(List.of(), null, null);
    registry = mock(OdysseyStreamRegistry.class);
    when(registry.channel(anyString())).thenReturn(mock(OdysseyStream.class));

    OdysseyStream ephemeralStream = mock(OdysseyStream.class);
    when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.ephemeral()).thenReturn(ephemeralStream);

    sessionManager = new McpSessionManager(registry);
    objectMapper = new ObjectMapper();
    toolsCapability = mock(McpToolsCapability.class);
    controller =
        new McpStreamingController(
            mcpServer,
            sessionManager,
            registry,
            objectMapper,
            List.of("localhost"),
            toolsCapability);
  }

  @Nested
  class PostRequestValidation {

    @Test
    void shouldReturn400ForInvalidJsonRpcVersion() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "1.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertErrorCode(response, -32600);
    }

    @Test
    void shouldReturn400ForMissingJsonRpcField() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400ForInvalidIdType() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.putArray("id");

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertErrorCode(response, -32600);
    }

    @Test
    void shouldReturn400ForInvalidProtocolVersion() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, "invalid-version", null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn403ForInvalidOrigin() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, null, POST_ACCEPT, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn404WhenSessionNotFound() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "tools/list");
      request.put("id", 1);

      var response = controller.handlePost(request, null, "nonexistent-session", POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
  }

  @Nested
  class PostMethodDispatch {

    @Test
    void initializeShouldReturnJsonWithSessionHeader() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "initialize");
      request.put("id", 1);

      ObjectNode params = request.putObject("params");
      params.put("protocolVersion", "2025-11-25");
      params.putObject("capabilities");
      ObjectNode clientInfo = params.putObject("clientInfo");
      clientInfo.put("name", "test-client");
      clientInfo.put("version", "1.0");

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getFirst("MCP-Session-Id")).isNotNull();
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void pingShouldReturnJson() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void toolsListShouldReturnJson() {
      when(toolsCapability.listTools(any()))
          .thenReturn(new McpToolsCapability.ListToolsResponse(List.of(), null));

      McpSession session = sessionManager.createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "tools/list");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void toolsCallShouldReturnJson() {
      when(toolsCapability.callTool(eq("test-tool"), any()))
          .thenReturn(
              new McpToolsCapability.CallToolResponse(List.of(), objectMapper.createObjectNode()));

      McpSession session = sessionManager.createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "tools/call");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("name", "test-tool");
      params.putObject("arguments");

      var response =
          controller.handlePost(request, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void unknownMethodShouldReturnJsonErrorResponse() {
      McpSession session = sessionManager.createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "unknown/method");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertErrorCode(response, -32601);
    }
  }

  @Nested
  class PostErrorHandling {

    @Test
    void mcpExceptionShouldReturnJsonErrorResponse() {
      when(toolsCapability.callTool(any(), any()))
          .thenThrow(new McpInvalidParamsException("Tool not found."));

      McpSession session = sessionManager.createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "tools/call");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("name", "nonexistent");
      params.putObject("arguments");

      var response =
          controller.handlePost(request, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertErrorCode(response, -32602);
    }

    @Test
    void runtimeExceptionShouldReturnJsonInternalError() {
      when(toolsCapability.callTool(any(), any())).thenThrow(new RuntimeException("unexpected"));

      McpSession session = sessionManager.createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "tools/call");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("name", "broken");
      params.putObject("arguments");

      var response =
          controller.handlePost(request, null, session.getSessionId(), POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertErrorCode(response, -32603);
    }
  }

  @Nested
  class PostProtocolVersions {

    @Test
    void shouldAcceptAllValidProtocolVersions() {
      for (String version : List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "ping");
        request.put("id", 1);

        var response = controller.handlePost(request, version, null, POST_ACCEPT, null);
        assertThat(response.getStatusCode())
            .as("Protocol version %s should be accepted", version)
            .isNotEqualTo(HttpStatus.BAD_REQUEST);
      }
    }
  }

  @Nested
  class PostOriginValidation {

    @Test
    void shouldAcceptValidOrigin() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, null, POST_ACCEPT, "http://localhost:8080");
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAcceptNullOrigin() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectInvalidOriginUri() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, "not a valid uri {{{");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
  }

  @Nested
  class PostIdTypes {

    @Test
    void shouldAcceptStringId() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", "string-id");

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptNumberId() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.put("id", 42);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptNullId() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", "2.0");
      request.put("method", "ping");
      request.putNull("id");

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  private static void assertErrorCode(
      org.springframework.http.ResponseEntity<Object> response, int expectedCode) {
    assertThat(response.getBody()).isInstanceOf(JsonNode.class);
    JsonNode body = (JsonNode) response.getBody();
    assertThat(body.has("error")).isTrue();
    assertThat(body.get("error").get("code").asInt()).isEqualTo(expectedCode);
  }

  private static void assertJsonRpcResponse(
      org.springframework.http.ResponseEntity<Object> response) {
    assertThat(response.getBody()).isInstanceOf(JsonNode.class);
    JsonNode body = (JsonNode) response.getBody();
    assertThat(body.get("jsonrpc").asString()).isEqualTo("2.0");
  }
}
