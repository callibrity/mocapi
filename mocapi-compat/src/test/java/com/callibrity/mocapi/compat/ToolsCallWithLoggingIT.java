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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ToolsCallWithLoggingIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void loggingToolReturnsSseWithLogNotifications() throws Exception {
    String sessionId = client.initialize();

    // Set log level to debug so all messages pass through
    ObjectNode levelParams = client.objectMapper().createObjectNode();
    levelParams.put("level", "debug");
    client.post(
        sessionId,
        "logging/setLevel",
        levelParams,
        client.objectMapper().getNodeFactory().numberNode(2));

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "test_tool_with_logging");
    params.putObject("arguments");

    MvcResult mvcResult =
        client
            .post(
                sessionId,
                "tools/call",
                params,
                client.objectMapper().getNodeFactory().numberNode(3))
            .andExpect(status().isOk())
            .andReturn();

    // Wait for async SSE stream to complete (tool finishes after ~150ms of sleep)
    mvcResult.getAsyncResult(5000);
    String body = mvcResult.getResponse().getContentAsString();

    assertThat(body).contains("notifications/message");
    assertThat(body).contains("Logging test completed successfully");
  }
}
