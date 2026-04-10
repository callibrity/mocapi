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

import com.callibrity.mocapi.compat.conformance.ConformanceApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = ConformanceApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ToolsListIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void toolsListReturnsAllConformanceTools() throws Exception {
    String sessionId = client.initialize();

    String body =
        client
            .post(
                sessionId, "tools/list", null, client.objectMapper().getNodeFactory().numberNode(2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.tools").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ObjectNode response = (ObjectNode) client.objectMapper().readTree(body);
    var tools = response.get("result").get("tools");

    var toolNames = new java.util.ArrayList<String>();
    for (var tool : tools) {
      toolNames.add(tool.get("name").asString());
    }

    assertThat(toolNames)
        .contains(
            "test_simple_text",
            "test_image_content",
            "test_audio_content",
            "test_embedded_resource",
            "test_multiple_content_types",
            "test_error_handling");
  }
}
