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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.model.CallToolRequestParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class FullConversationIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void completeSessionLifecycle() throws Exception {
    // 1. Initialize — receive session ID and server capabilities
    String sessionId =
        client
            .initializeResult()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.protocolVersion").isString())
            .andExpect(jsonPath("$.result.capabilities").exists())
            .andExpect(jsonPath("$.result.serverInfo.name").isNotEmpty())
            .andReturn()
            .getResponse()
            .getHeader("MCP-Session-Id");

    assertThat(sessionId).isNotNull().isNotBlank();

    // 2. notifications/initialized — 202
    client.notify(sessionId, "notifications/initialized", null).andExpect(status().isAccepted());

    // 3. tools/list — receive tool definitions
    client
        .post(sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.tools").isArray())
        .andExpect(jsonPath("$.result.tools").isNotEmpty())
        .andExpect(jsonPath("$.result.tools[0].name").isString());

    // 4. tools/call for echo tool — receive structured content result
    var arguments = client.objectMapper().createObjectNode();
    arguments.put("message", "hello world");
    var callParams = new CallToolRequestParams("echo", arguments, null, null);

    client
        .post(
            sessionId,
            "tools/call",
            callParams,
            client.objectMapper().getNodeFactory().numberNode(3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.structuredContent.message").value("hello world"));

    // 5. ping — receive pong (empty result)
    client
        .post(sessionId, "ping", null, client.objectMapper().getNodeFactory().numberNode(4))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").isEmpty());

    // 6. DELETE — 204
    client.delete(sessionId).andExpect(status().isNoContent());

    // 7. ping with old session — 404
    String body =
        """
        {"jsonrpc":"2.0","method":"ping","id":5}""";

    client
        .postRaw("application/json, text/event-stream", sessionId, body)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value(-32600))
        .andExpect(jsonPath("$.error.message").value("Session not found or expired"));
  }
}
