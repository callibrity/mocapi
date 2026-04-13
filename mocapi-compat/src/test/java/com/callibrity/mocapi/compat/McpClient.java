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
package com.callibrity.mocapi.compat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

public class McpClient {

  private static final String MCP_ENDPOINT = "/mcp";
  private static final String PROTOCOL_VERSION = "2025-11-25";
  private static final MediaType TEXT_EVENT_STREAM = MediaType.TEXT_EVENT_STREAM;

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public McpClient(MockMvc mockMvc) {
    this.mockMvc = mockMvc;
  }

  public String initialize() throws Exception {
    String sessionId = initializeResult().andReturn().getResponse().getHeader("MCP-Session-Id");
    notify(sessionId, "notifications/initialized", null);
    return sessionId;
  }

  public ResultActions initializeResult() throws Exception {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("protocolVersion", PROTOCOL_VERSION);
    params.putObject("capabilities");
    ObjectNode clientInfo = params.putObject("clientInfo");
    clientInfo.put("name", "compat-test-client");
    clientInfo.put("version", "1.0.0");

    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", "initialize");
    request.set("params", params);
    request.put("id", 1);

    return mockMvc.perform(
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .content(objectMapper.writeValueAsString(request)));
  }

  public ResultActions post(String sessionId, String method, ObjectNode params, JsonNode id)
      throws Exception {
    return post(sessionId, method, (Object) params, id);
  }

  public ResultActions post(String sessionId, String method, Object params, JsonNode id)
      throws Exception {
    JsonNode paramsNode =
        params == null
            ? null
            : params instanceof JsonNode jn ? jn : objectMapper.valueToTree(params);

    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    if (paramsNode != null) {
      request.set("params", paramsNode);
    }
    if (id != null) {
      request.set("id", id);
    }

    return mockMvc.perform(
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("MCP-Session-Id", sessionId)
            .content(objectMapper.writeValueAsString(request)));
  }

  public ResultActions notify(String sessionId, String method, ObjectNode params) throws Exception {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    if (params != null) {
      request.set("params", params);
    }

    return mockMvc.perform(
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("MCP-Session-Id", sessionId)
            .content(objectMapper.writeValueAsString(request)));
  }

  public ResultActions delete(String sessionId) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.delete(MCP_ENDPOINT).header("MCP-Session-Id", sessionId));
  }

  public ResultActions deleteWithoutSession() throws Exception {
    return mockMvc.perform(MockMvcRequestBuilders.delete(MCP_ENDPOINT));
  }

  public ResultActions get(String sessionId) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.get(MCP_ENDPOINT)
            .accept(TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("MCP-Session-Id", sessionId));
  }

  public ResultActions postRaw(String accept, String sessionId, String body) throws Exception {
    var builder =
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (accept != null) {
      builder = builder.header("Accept", accept);
    }
    if (sessionId != null) {
      builder = builder.header("MCP-Session-Id", sessionId);
    }
    builder = builder.header("MCP-Protocol-Version", PROTOCOL_VERSION);
    return mockMvc.perform(builder);
  }

  public ResultActions getWithLastEventId(String sessionId, String lastEventId) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.get(MCP_ENDPOINT)
            .accept(TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("MCP-Session-Id", sessionId)
            .header("Last-Event-ID", lastEventId));
  }

  public ResultActions getRaw(String accept, String sessionId) throws Exception {
    var builder = MockMvcRequestBuilders.get(MCP_ENDPOINT);
    if (accept != null) {
      builder = builder.header("Accept", accept);
    }
    if (sessionId != null) {
      builder = builder.header("MCP-Session-Id", sessionId);
    }
    builder = builder.header("MCP-Protocol-Version", PROTOCOL_VERSION);
    return mockMvc.perform(builder);
  }

  public ResultActions postWithOrigin(String sessionId, String origin, String body)
      throws Exception {
    var builder =
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("Origin", origin)
            .content(body);
    if (sessionId != null) {
      builder = builder.header("MCP-Session-Id", sessionId);
    }
    return mockMvc.perform(builder);
  }

  public ResultActions getWithOrigin(String sessionId, String origin) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.get(MCP_ENDPOINT)
            .accept(TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .header("MCP-Session-Id", sessionId)
            .header("Origin", origin));
  }

  public ResultActions deleteWithOrigin(String sessionId, String origin) throws Exception {
    return mockMvc.perform(
        MockMvcRequestBuilders.delete(MCP_ENDPOINT)
            .header("MCP-Session-Id", sessionId)
            .header("Origin", origin));
  }

  public ResultActions initializeWithProtocolVersion(String version) throws Exception {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("protocolVersion", version);
    params.putObject("capabilities");
    ObjectNode clientInfo = params.putObject("clientInfo");
    clientInfo.put("name", "compat-test-client");
    clientInfo.put("version", "1.0.0");

    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", "initialize");
    request.set("params", params);
    request.put("id", 1);

    var builder =
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", version)
            .content(objectMapper.writeValueAsString(request));
    return mockMvc.perform(builder);
  }

  public String initializeWithCapabilities(ObjectNode capabilities) throws Exception {
    String sessionId =
        initializeWithCapabilitiesResult(capabilities)
            .andReturn()
            .getResponse()
            .getHeader("MCP-Session-Id");
    notify(sessionId, "notifications/initialized", null);
    return sessionId;
  }

  public ResultActions initializeWithCapabilitiesResult(ObjectNode capabilities) throws Exception {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("protocolVersion", PROTOCOL_VERSION);
    params.set("capabilities", capabilities);
    ObjectNode clientInfo = params.putObject("clientInfo");
    clientInfo.put("name", "compat-test-client");
    clientInfo.put("version", "1.0.0");

    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", "initialize");
    request.set("params", params);
    request.put("id", 1);

    return mockMvc.perform(
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Protocol-Version", PROTOCOL_VERSION)
            .content(objectMapper.writeValueAsString(request)));
  }

  public ResultActions initializeWithoutProtocolVersion() throws Exception {
    ObjectNode params = objectMapper.createObjectNode();
    params.put("protocolVersion", PROTOCOL_VERSION);
    params.putObject("capabilities");
    ObjectNode clientInfo = params.putObject("clientInfo");
    clientInfo.put("name", "compat-test-client");
    clientInfo.put("version", "1.0.0");

    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", "initialize");
    request.set("params", params);
    request.put("id", 1);

    return mockMvc.perform(
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .content(objectMapper.writeValueAsString(request)));
  }

  public JsonNode call(String sessionId, String method, Object params, JsonNode id)
      throws Exception {
    MvcResult mvcResult = post(sessionId, method, params, id).andReturn();

    String contentType = mvcResult.getResponse().getContentType();
    if (contentType != null && contentType.contains("application/json")) {
      return objectMapper.readTree(mvcResult.getResponse().getContentAsString());
    }

    mvcResult.getAsyncResult(30000);
    MvcResult completed = mockMvc.perform(asyncDispatch(mvcResult)).andReturn();
    String body = completed.getResponse().getContentAsString();
    return lastJsonRpcResponse(body);
  }

  public String callRawSse(String sessionId, String method, Object params, JsonNode id)
      throws Exception {
    MvcResult mvcResult = post(sessionId, method, params, id).andReturn();
    mvcResult.getAsyncResult(30000);
    MvcResult completed = mockMvc.perform(asyncDispatch(mvcResult)).andReturn();
    return completed.getResponse().getContentAsString();
  }

  private JsonNode lastJsonRpcResponse(String sseBody) {
    JsonNode last = null;
    for (String line : sseBody.split("\n")) {
      if (line.startsWith("data:")) {
        String data = line.substring(5);
        if (data.isBlank()) {
          continue;
        }
        JsonNode node = objectMapper.readTree(data);
        if (node.has("id")) {
          last = node;
        }
      }
    }
    return last;
  }

  public ResultActions postWithProtocolVersion(
      String sessionId, String protocolVersion, String method) throws Exception {
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    request.put("id", 1);

    var builder =
        MockMvcRequestBuilders.post(MCP_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, TEXT_EVENT_STREAM)
            .header("MCP-Session-Id", sessionId)
            .content(objectMapper.writeValueAsString(request));
    if (protocolVersion != null) {
      builder = builder.header("MCP-Protocol-Version", protocolVersion);
    }
    return mockMvc.perform(builder);
  }

  public ObjectMapper objectMapper() {
    return objectMapper;
  }
}
