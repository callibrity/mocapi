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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class PostEndpointIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void missingAcceptHeaderReturns406() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}""";

    client.postRaw(null, null, body).andExpect(status().isNotAcceptable());
  }

  @Test
  void acceptWithOnlyJsonReturns406() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}""";

    client.postRaw("application/json", null, body).andExpect(status().isNotAcceptable());
  }

  @Test
  void acceptWithOnlySseReturns406() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}""";

    client.postRaw("text/event-stream", null, body).andExpect(status().isNotAcceptable());
  }

  @Test
  void acceptWithWildcardIsAccepted() throws Exception {
    String body =
        """
        {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}},"id":1}""";

    client.postRaw("*/*", null, body).andExpect(status().isOk());
  }

  @Test
  void invalidJsonRpcVersionReturnsError() throws Exception {
    String sessionId = client.initialize();

    String body =
        """
        {"jsonrpc":"1.0","method":"ping","id":2}""";

    MvcResult mvcResult =
        client.postRaw("application/json, text/event-stream", sessionId, body).andReturn();

    mvcResult.getAsyncResult(5000);
    String responseBody = mvcResult.getResponse().getContentAsString();

    ObjectMapper objectMapper = client.objectMapper();
    JsonNode errorResponse = null;
    for (String line : responseBody.split("\n")) {
      if (line.startsWith("data:")) {
        JsonNode node = objectMapper.readTree(line.substring(5));
        if (node.has("error")) {
          errorResponse = node;
        }
      }
    }

    assertThat(errorResponse).isNotNull();
    assertThat(errorResponse.get("error").get("code").asInt()).isEqualTo(-32600);
    assertThat(errorResponse.get("error").get("message").asString())
        .isEqualTo("jsonrpc value must be \"2.0\".");
  }
}
