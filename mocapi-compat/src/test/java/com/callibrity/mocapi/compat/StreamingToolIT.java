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

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = CompatApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class StreamingToolIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void getSseStreamReturnsOkWithSseContentType() throws Exception {
    String sessionId = client.initialize();

    client
        .get(sessionId)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
  }

  @Test
  void streamingToolCallReturnsSseContentType() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "stream");
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("message", "hello streaming");

    client
        .post(
            sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(10))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
  }

  @Test
  void nonStreamingToolReturnsJsonNotSse() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "echo");
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("message", "hello json");

    client
        .post(
            sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(12))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  @Test
  void tamperedLastEventIdReturns400() throws Exception {
    String sessionId = client.initialize();

    client.get(sessionId).andExpect(status().isOk());

    // Tampered/unencrypted Last-Event-ID should be rejected
    client.getWithLastEventId(sessionId, "tampered-event-id").andExpect(status().isBadRequest());
  }
}
