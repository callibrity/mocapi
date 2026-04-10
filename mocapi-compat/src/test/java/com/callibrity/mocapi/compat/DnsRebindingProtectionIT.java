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
class DnsRebindingProtectionIT {

  private static final String INIT_BODY =
      """
      {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}""";

  private static final String BAD_ORIGIN = "http://evil.example.com";

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void foreignOriginIsRejected() throws Exception {
    client.postWithOrigin(null, BAD_ORIGIN, INIT_BODY).andExpect(status().isForbidden());
  }

  @Test
  void postWithNoOriginIsAccepted() throws Exception {
    client.initializeResult().andExpect(status().isOk());
  }

  @Test
  void getWithInvalidOriginReturns403() throws Exception {
    String sessionId = client.initialize();

    client.getWithOrigin(sessionId, BAD_ORIGIN).andExpect(status().isForbidden());
  }

  @Test
  void deleteWithInvalidOriginReturns403() throws Exception {
    String sessionId = client.initialize();

    client.deleteWithOrigin(sessionId, BAD_ORIGIN).andExpect(status().isForbidden());
  }

  @Test
  void getWithNoOriginIsAccepted() throws Exception {
    String sessionId = client.initialize();

    client.get(sessionId).andExpect(status().isOk());
  }

  @Test
  void deleteWithNoOriginIsAccepted() throws Exception {
    String sessionId = client.initialize();

    client.delete(sessionId).andExpect(status().isNoContent());
  }
}
