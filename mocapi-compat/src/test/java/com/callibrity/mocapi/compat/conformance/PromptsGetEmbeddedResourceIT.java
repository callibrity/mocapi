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
package com.callibrity.mocapi.compat.conformance;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.compat.McpClient;
import com.callibrity.mocapi.compat.RandomMasterKeyInitializer;
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
class PromptsGetEmbeddedResourceIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void getPromptWithEmbeddedResource() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("name", "test_prompt_with_embedded_resource");
    ObjectNode arguments = params.putObject("arguments");
    arguments.put("resourceUri", "test://example-resource");

    client
        .post(
            sessionId, "prompts/get", params, client.objectMapper().getNodeFactory().numberNode(2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.messages").isArray())
        .andExpect(jsonPath("$.result.messages[0].role").value("user"))
        .andExpect(jsonPath("$.result.messages[0].content.type").value("resource"))
        .andExpect(
            jsonPath("$.result.messages[0].content.resource.uri").value("test://example-resource"))
        .andExpect(jsonPath("$.result.messages[0].content.resource.mimeType").value("text/plain"))
        .andExpect(
            jsonPath("$.result.messages[0].content.resource.text")
                .value("Embedded resource content for testing."))
        .andExpect(jsonPath("$.result.messages[1].role").value("user"))
        .andExpect(jsonPath("$.result.messages[1].content.type").value("text"))
        .andExpect(
            jsonPath("$.result.messages[1].content.text")
                .value("Please process the embedded resource above."));
  }
}
