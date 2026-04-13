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
import tools.jackson.databind.JsonNode;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class SessionManagementIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void initializeReturnsSessionIdHeader() throws Exception {
    String sessionId = client.initialize();
    assertThat(sessionId).isNotNull().isNotBlank();
  }

  @Test
  void postWithSessionIdSucceeds() throws Exception {
    String sessionId = client.initialize();

    JsonNode response =
        client.call(sessionId, "ping", null, client.objectMapper().getNodeFactory().numberNode(2));

    assertThat(response.get("result").isEmpty()).isTrue();
  }

  @Test
  void postWithoutSessionIdOnNonInitializeReturns400() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"ping","id":2}""";

    client
        .postRaw("application/json, text/event-stream", null, body)
        .andExpect(status().isBadRequest());
  }

  @Test
  void postWithUnknownSessionIdReturns404() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"ping","id":2}""";

    client
        .postRaw("application/json, text/event-stream", "nonexistent-session-id", body)
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteWithValidSessionReturns204() throws Exception {
    String sessionId = client.initialize();

    client.delete(sessionId).andExpect(status().isNoContent());
  }

  @Test
  void deleteWithoutSessionIdReturns400() throws Exception {
    client.deleteWithoutSession().andExpect(status().isBadRequest());
  }

  @Test
  void deleteWithUnknownSessionIdReturns404() throws Exception {
    client.delete("nonexistent-session-id").andExpect(status().isNotFound());
  }

  @Test
  void postToDeletedSessionReturns404() throws Exception {
    String sessionId = client.initialize();

    client.delete(sessionId).andExpect(status().isNoContent());

    String body =
        """
        {"jsonrpc":"2.0","method":"ping","id":3}""";

    client
        .postRaw("application/json, text/event-stream", sessionId, body)
        .andExpect(status().isNotFound());
  }
}
