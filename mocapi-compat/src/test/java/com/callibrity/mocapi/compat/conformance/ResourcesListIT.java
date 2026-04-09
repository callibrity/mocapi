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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.callibrity.mocapi.compat.McpClient;
import com.callibrity.mocapi.compat.RandomMasterKeyInitializer;
import java.util.ArrayList;
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
class ResourcesListIT {

  @Autowired private MockMvc mockMvc;

  private McpClient client;

  @BeforeEach
  void setUp() {
    client = new McpClient(mockMvc);
  }

  @Test
  void resourcesListReturnsAllConformanceResources() throws Exception {
    String sessionId = client.initialize();

    String body =
        client
            .post(
                sessionId,
                "resources/list",
                null,
                client.objectMapper().getNodeFactory().numberNode(2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.resources").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ObjectNode response = (ObjectNode) client.objectMapper().readTree(body);
    var resources = response.get("result").get("resources");

    var uris = new ArrayList<String>();
    for (var resource : resources) {
      uris.add(resource.get("uri").asString());
    }

    assertThat(uris)
        .contains("test://static-text", "test://static-binary", "test://watched-resource");
  }

  @Test
  void resourcesTemplatesListReturnsTemplates() throws Exception {
    String sessionId = client.initialize();

    String body =
        client
            .post(
                sessionId,
                "resources/templates/list",
                null,
                client.objectMapper().getNodeFactory().numberNode(2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.resourceTemplates").isArray())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ObjectNode response = (ObjectNode) client.objectMapper().readTree(body);
    var templates = response.get("result").get("resourceTemplates");

    var uriTemplates = new ArrayList<String>();
    for (var template : templates) {
      uriTemplates.add(template.get("uriTemplate").asString());
    }

    assertThat(uriTemplates).contains("test://template/{id}/data");
  }
}
