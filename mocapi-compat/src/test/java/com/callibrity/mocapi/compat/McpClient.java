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

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
    return initializeResult().andReturn().getResponse().getHeader("MCP-Session-Id");
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
    ObjectNode request = objectMapper.createObjectNode();
    request.put("jsonrpc", "2.0");
    request.put("method", method);
    if (params != null) {
      request.set("params", params);
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

  public ObjectMapper objectMapper() {
    return objectMapper;
  }
}
