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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = CompatApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ToolInvocationIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void echoToolReturnsStructuredContent() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "echo");
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("message", "hello world");

    client
        .post(sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.structuredContent.message").value("hello world"));
  }

  @Test
  void toolResultHasMatchingId() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "echo");
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("message", "test");

    client
        .post(
            sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(99))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(99));
  }

  @Test
  void unknownToolReturnsJsonRpcError() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "nonexistent_tool");
    params.putObject("arguments");

    client
        .post(sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(4))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.code").isNumber())
        .andExpect(jsonPath("$.error.message").isString());
  }

  @Test
  void errorToolReturnsJsonRpcErrorNotHttp500() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "error");
    params.putObject("arguments");

    client
        .post(sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(5))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.error.code").isNumber())
        .andExpect(jsonPath("$.error.message").value("intentional error"));
  }
}
