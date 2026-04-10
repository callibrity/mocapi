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

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ProtocolVersionNegotiationIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void acceptsValidProtocolVersion() throws Exception {
    client
        .initializeWithProtocolVersion("2025-11-25")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.protocolVersion").isString());
  }

  @Test
  void rejectsUnrecognizedProtocolVersion() throws Exception {
    client
        .initializeWithProtocolVersion("9999-01-01")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value(-32600))
        .andExpect(jsonPath("$.error.message").value("Invalid MCP-Protocol-Version: 9999-01-01"));
  }

  @Test
  void missingProtocolVersionDefaultsToCurrent() throws Exception {
    client
        .initializeWithoutProtocolVersion()
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.protocolVersion").isString());
  }
}
