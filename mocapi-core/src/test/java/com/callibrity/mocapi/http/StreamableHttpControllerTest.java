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
package com.callibrity.mocapi.http;

import static com.callibrity.mocapi.JsonRpcProtocol.VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.callibrity.mocapi.server.InitializeResponse;
import com.callibrity.mocapi.server.ServerCapabilities;
import com.callibrity.mocapi.server.ServerInfo;
import com.callibrity.mocapi.session.ClientCapabilities;
import com.callibrity.mocapi.session.ClientInfo;
import com.callibrity.mocapi.session.InMemoryMcpSessionStore;
import com.callibrity.mocapi.session.McpSession;
import com.callibrity.mocapi.session.McpSessionMethods;
import com.callibrity.mocapi.session.McpSessionService;
import com.callibrity.mocapi.tools.McpToolMethods;
import com.callibrity.mocapi.tools.ToolMethodInvoker;
import com.callibrity.mocapi.tools.ToolsRegistry;
import com.callibrity.ripcurl.core.JsonRpcDispatcher;
import com.callibrity.ripcurl.core.JsonRpcResponse;
import com.callibrity.ripcurl.core.annotation.AnnotationJsonRpcMethod;
import com.callibrity.ripcurl.core.def.DefaultJsonRpcDispatcher;
import com.callibrity.ripcurl.core.exception.JsonRpcException;
import com.callibrity.ripcurl.core.spi.JsonRpcMethodProvider;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.core.OdysseyStream;
import org.jwcarman.odyssey.core.OdysseyStreamRegistry;
import org.jwcarman.substrate.core.MailboxFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class StreamableHttpControllerTest {

  private static final String POST_ACCEPT = "application/json, text/event-stream";
  private static final Duration SESSION_TIMEOUT = Duration.ofHours(1);

  private StreamableHttpController controller;
  private InMemoryMcpSessionStore sessionStore;
  private McpSessionService sessionService;
  private ObjectMapper objectMapper;
  private ToolsRegistry toolsCapability;
  private OdysseyStreamRegistry registry;

  @BeforeEach
  void setUp() {
    InitializeResponse initializeResponse =
        new InitializeResponse(
            InitializeResponse.PROTOCOL_VERSION,
            new ServerCapabilities(null, null),
            new ServerInfo("test", null, "1.0", null, null, null),
            null);
    registry = mock(OdysseyStreamRegistry.class);
    when(registry.channel(anyString())).thenReturn(mock(OdysseyStream.class));

    OdysseyStream ephemeralStream = mock(OdysseyStream.class);
    when(ephemeralStream.subscribe()).thenReturn(new SseEmitter());
    when(registry.ephemeral()).thenReturn(ephemeralStream);

    sessionStore = new InMemoryMcpSessionStore();
    objectMapper = new ObjectMapper();
    toolsCapability = mock(ToolsRegistry.class);

    byte[] masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    sessionService = new McpSessionService(sessionStore, masterKey, SESSION_TIMEOUT);

    McpSessionMethods serverMethods = new McpSessionMethods(initializeResponse);
    ToolMethodInvoker toolMethodInvoker = mock(ToolMethodInvoker.class);
    McpToolMethods toolMethods =
        new McpToolMethods(toolsCapability, objectMapper, toolMethodInvoker);

    // Wire up the mock to delegate to toolsCapability for non-streamable tools
    when(toolMethodInvoker.invoke(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              String name = invocation.getArgument(0);
              ObjectNode args = invocation.getArgument(1);
              return toolsCapability.callTool(name, args);
            });

    JsonRpcMethodProvider serverProvider =
        () ->
            List.copyOf(
                AnnotationJsonRpcMethod.createMethods(objectMapper, serverMethods, List.of()));
    JsonRpcMethodProvider toolProvider =
        () ->
            List.copyOf(
                AnnotationJsonRpcMethod.createMethods(objectMapper, toolMethods, List.of()));
    JsonRpcDispatcher dispatcher =
        new DefaultJsonRpcDispatcher(List.of(serverProvider, toolProvider));

    MailboxFactory mailboxFactory = mock(MailboxFactory.class);
    McpRequestValidator validator = new McpRequestValidator(List.of("localhost"));
    controller =
        new StreamableHttpController(
            dispatcher, validator, sessionService, registry, objectMapper, mailboxFactory);
  }

  @AfterEach
  void tearDown() {
    sessionStore.shutdown();
  }

  private String createSession() {
    return sessionService.create(
        new McpSession(
            "2025-11-25",
            new ClientCapabilities(null, null, null, null, null),
            new ClientInfo("test", null, "1.0", null, null, null)));
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
    void shouldReturn400ForInvalidProtocolVersion() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, "invalid-version", null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn403ForInvalidOrigin() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, null, POST_ACCEPT, "http://evil.example.com");
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn404WhenSessionNotFound() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
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
      request.put("jsonrpc", VERSION);
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
    void initializeShouldStoreClientData() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "initialize");
      request.put("id", 1);

      ObjectNode params = request.putObject("params");
      params.put("protocolVersion", "2025-11-25");
      params.putObject("capabilities");
      ObjectNode clientInfo = params.putObject("clientInfo");
      clientInfo.put("name", "test-client");
      clientInfo.put("version", "1.0");

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      String sessionId = response.getHeaders().getFirst("MCP-Session-Id");

      var session = sessionService.find(sessionId);
      assertThat(session).isPresent();
      assertThat(session.get().protocolVersion()).isEqualTo("2025-11-25");
      assertThat(session.get().clientInfo().name()).isEqualTo("test-client");
    }

    @Test
    void pingShouldReturnJson() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void toolsListShouldReturnJson() {
      when(toolsCapability.listTools(any()))
          .thenReturn(new ToolsRegistry.ListToolsResponse(List.of(), null));

      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "tools/list");
      request.put("id", 1);

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void toolsCallShouldReturnJson() {
      when(toolsCapability.callTool(eq("test-tool"), any()))
          .thenReturn(
              new ToolsRegistry.CallToolResponse(List.of(), null, objectMapper.createObjectNode()));

      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "tools/call");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("name", "test-tool");
      params.putObject("arguments");

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertJsonRpcResponse(response);
    }

    @Test
    void unknownMethodShouldReturnJsonErrorResponse() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "unknown/method");
      request.put("id", 1);

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertErrorCode(response, -32601);
    }
  }

  @Nested
  class PostErrorHandling {

    @Test
    void jsonRpcExceptionShouldReturnJsonErrorResponse() {
      when(toolsCapability.callTool(any(), any()))
          .thenThrow(new JsonRpcException(JsonRpcException.INVALID_PARAMS, "Tool not found."));

      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "tools/call");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("name", "nonexistent");
      params.putObject("arguments");

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertErrorCode(response, -32602);
    }

    @Test
    void runtimeExceptionShouldReturnJsonInternalError() {
      when(toolsCapability.callTool(any(), any())).thenThrow(new RuntimeException("unexpected"));

      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "tools/call");
      request.put("id", 1);
      ObjectNode params = request.putObject("params");
      params.put("name", "broken");
      params.putObject("arguments");

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
      assertErrorCode(response, -32603);
    }
  }

  @Nested
  class PostProtocolVersions {

    @Test
    void shouldAcceptAllValidProtocolVersions() {
      String sessionId = createSession();
      for (String version : List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", VERSION);
        request.put("method", "ping");
        request.put("id", 1);

        var response = controller.handlePost(request, version, sessionId, POST_ACCEPT, null);
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
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response =
          controller.handlePost(request, null, sessionId, POST_ACCEPT, "http://localhost:8080");
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldAcceptNullOrigin() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldRejectInvalidOriginUri() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
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
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", "string-id");

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptNumberId() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 42);

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldAcceptNullId() {
      String sessionId = createSession();
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.putNull("id");

      var response = controller.handlePost(request, null, sessionId, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
  }

  @Nested
  class PostSessionIdEnforcement {

    @Test
    void shouldReturn400WhenSessionIdMissingForNonInitializeMethod() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
      request.put("method", "ping");
      request.put("id", 1);

      var response = controller.handlePost(request, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
      assertErrorCode(response, -32600);
      assertErrorMessage(response, "MCP-Session-Id header is required");
    }

    @Test
    void shouldAllowInitializeWithoutSessionId() {
      ObjectNode request = objectMapper.createObjectNode();
      request.put("jsonrpc", VERSION);
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
    }

    @Test
    void shouldReturn400WhenNotificationMissingSessionId() {
      ObjectNode notification = objectMapper.createObjectNode();
      notification.put("jsonrpc", VERSION);
      notification.put("method", "notifications/initialized");

      var response = controller.handlePost(notification, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400WhenJsonRpcResponseMissingSessionId() {
      ObjectNode jsonRpcResponse = objectMapper.createObjectNode();
      jsonRpcResponse.put("jsonrpc", VERSION);
      jsonRpcResponse.put("id", 1);
      jsonRpcResponse.putObject("result");

      var response = controller.handlePost(jsonRpcResponse, null, null, POST_ACCEPT, null);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
  }

  private static void assertErrorCode(
      org.springframework.http.ResponseEntity<Object> response, int expectedCode) {
    assertThat(response.getBody()).isInstanceOf(JsonNode.class);
    JsonNode body = (JsonNode) response.getBody();
    assertThat(body.has("error")).isTrue();
    assertThat(body.get("error").get("code").asInt()).isEqualTo(expectedCode);
  }

  private static void assertErrorMessage(
      org.springframework.http.ResponseEntity<Object> response, String expectedMessage) {
    assertThat(response.getBody()).isInstanceOf(JsonNode.class);
    JsonNode body = (JsonNode) response.getBody();
    assertThat(body.has("error")).isTrue();
    assertThat(body.get("error").get("message").asString()).isEqualTo(expectedMessage);
  }

  private static void assertJsonRpcResponse(
      org.springframework.http.ResponseEntity<Object> response) {
    assertThat(response.getBody()).isInstanceOf(JsonRpcResponse.class);
    JsonRpcResponse body = (JsonRpcResponse) response.getBody();
    assertThat(body.jsonrpc()).isEqualTo(VERSION);
  }
}
