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

@SpringBootTest(classes = CompatApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class InitializationIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void initializeReturnsProtocolVersion() throws Exception {
    client
        .initializeResult()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.protocolVersion").isString())
        .andExpect(jsonPath("$.result.protocolVersion").isNotEmpty());
  }

  @Test
  void initializeReturnsCapabilities() throws Exception {
    client
        .initializeResult()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.capabilities").exists());
  }

  @Test
  void initializeReturnsServerInfoWithName() throws Exception {
    client
        .initializeResult()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.serverInfo").exists())
        .andExpect(jsonPath("$.result.serverInfo.name").isString())
        .andExpect(jsonPath("$.result.serverInfo.name").isNotEmpty());
  }

  @Test
  void initializeDoesNotRequireSessionId() throws Exception {
    // initialize() sends no MCP-Session-Id header — should still succeed
    client.initializeResult().andExpect(status().isOk());
  }

  @Test
  void notificationsInitializedReturns202() throws Exception {
    String sessionId = client.initialize();

    client.notify(sessionId, "notifications/initialized", null).andExpect(status().isAccepted());
  }
}
