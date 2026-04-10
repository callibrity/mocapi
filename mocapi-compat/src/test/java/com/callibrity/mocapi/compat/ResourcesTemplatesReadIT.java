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
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest(classes = CompatibilityApplication.class)
@AutoConfigureMockMvc
@ContextConfiguration(initializers = RandomMasterKeyInitializer.class)
class ResourcesTemplatesReadIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void readTemplateResourceSubstitutesId() throws Exception {
    String sessionId = client.initialize();

    ObjectNode params = client.objectMapper().createObjectNode();
    params.put("uri", "test://template/123/data");

    client
        .post(
            sessionId,
            "resources/read",
            params,
            client.objectMapper().getNodeFactory().numberNode(2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.contents").isArray())
        .andExpect(jsonPath("$.result.contents[0].uri").value("test://template/123/data"))
        .andExpect(jsonPath("$.result.contents[0].mimeType").value("application/json"))
        .andExpect(
            jsonPath("$.result.contents[0].text")
                .value("{\"id\":\"123\",\"templateTest\":true,\"data\":\"Data for ID: 123\"}"));
  }
}
