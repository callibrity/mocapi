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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.model.CallToolRequestParams;
import com.callibrity.mocapi.model.RequestMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ContentNegotiationIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void initializeResponseIsJsonNotSse() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}""";

    client
        .postRaw("application/json, text/event-stream", null, body)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.result.protocolVersion").exists());
  }

  @Test
  void pingResponseIsJson() throws Exception {
    String sessionId = client.initialize();

    client
        .post(sessionId, "ping", null, client.objectMapper().getNodeFactory().numberNode(2))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.result").isEmpty());
  }

  @Test
  void streamingToolCallReturnsSseContentType() throws Exception {
    String sessionId = client.initialize();

    var arguments = client.objectMapper().createObjectNode();
    arguments.put("message", "hello streaming");
    var meta =
        new RequestMeta(client.objectMapper().getNodeFactory().textNode("test-progress-token"));
    var params = new CallToolRequestParams("stream", arguments, null, meta);

    client
        .post(
            sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(10))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
  }

  @Test
  void nonStreamingToolReturnsJsonNotSse() throws Exception {
    String sessionId = client.initialize();

    var arguments = client.objectMapper().createObjectNode();
    arguments.put("message", "hello json");
    var params = new CallToolRequestParams("echo", arguments, null, null);

    client
        .post(
            sessionId, "tools/call", params, client.objectMapper().getNodeFactory().numberNode(12))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }
}
